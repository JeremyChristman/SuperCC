package io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * MOD (Jeremy, jc-11): gives a crash somewhere to land.
 *
 * WHY THIS EXISTS
 * ---------------
 * SuperCC is launched by double-clicking the jar, which runs it under javaw -- and javaw discards
 * stderr entirely. So every one of the 27 printStackTrace() calls in this program, and every
 * uncaught exception, went nowhere. A user reporting "it just closed" had no artifact to send and
 * nothing to look at, and neither did anyone trying to fix it.
 *
 * install() tees System.err to a log file. That single move captures every existing
 * printStackTrace() without touching any of them.
 *
 * THE CONSOLE OUTPUT IS DELIBERATELY PRESERVED
 * --------------------------------------------
 * The tee writes to the ORIGINAL System.err as well as the file, and the uncaught-exception
 * handler prints through System.err rather than writing the file itself. Both details are
 * load-bearing:
 *
 *   * On Java 9+, AWT's EventDispatchThread.processException() calls
 *     getUncaughtExceptionHandler().uncaughtException(...) -- and ThreadGroup only prints a trace
 *     to System.err when NO default handler is installed. So merely installing a handler REMOVES
 *     the console stack trace that exists today, making the program quieter than before. Printing
 *     through System.err puts it back.
 *   * Because the handler prints through the tee, the tee remains the single owner of the file.
 *     A handler that both printed to the console and wrote the file would log everything twice.
 *
 * RULES THIS CLASS MUST NEVER BREAK
 * ---------------------------------
 *   1. It must never throw. It sits underneath System.err, so an exception escaping from here
 *      surfaces inside unrelated code that was merely reporting a problem.
 *   2. It must never print. Its own output IS System.err; a println on a failure path recurses
 *      until the stack ends.
 *   3. Once writing fails it stays off. Retrying the open on every write turns a read-only folder
 *      into a syscall storm.
 */
public final class ErrorLog {

    /** Rotate at this size. Small enough to attach to a bug report, large enough for a real trace. */
    private static final long MAX_BYTES = 512 * 1024;

    private static final Object LOCK = new Object();
    private static boolean installed;
    private static PrintStream originalErr;
    private static Sink sink;
    private static Consumer<File> notifier;
    /* volatile so notifyOnce can bail out on the common path without taking LOCK -- it is now
     * called from every write to the file half, not just from the uncaught-exception handler. */
    private static volatile boolean notified;

    private ErrorLog() { }

    /**
     * Tees System.err into a log file in the given directory and routes uncaught exceptions
     * through it. Safe to call more than once; only the first call does anything.
     *
     * @param directory where the log lives -- the same folder succ_settings.ini resolves to.
     */
    public static void install(File directory) {
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
            try {
                originalErr = System.err;
                sink = new Sink(new File(directory, logName()));

                /* The charset is explicit because the two-argument PrintStream constructor would
                 * silently use the DEFAULT charset rather than the one the console is actually
                 * using -- and this program prints Unicode arrows and level titles. */
                System.setErr(new PrintStream(new Tee(originalErr, sink), true, consoleCharset()));

                Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                    try {
                        System.err.println("Uncaught exception in thread \"" + thread.getName() + "\"");
                        error.printStackTrace();
                    } catch (Throwable ignored) {
                        // Never let the handler throw: the JVM re-enters it once and then reports
                        // the failure as a second crash, which is worse than the first.
                    }
                    notifyOnce();
                });
            } catch (Throwable ignored) {
                // A program that cannot set up logging still has to run.
            }
        }
    }

    /**
     * Registers a one-shot callback for the first thing written to the log, so the user can be
     * told the file exists. Under javaw that notice is the ONLY signal they will ever get.
     */
    public static void setNotifier(Consumer<File> callback) {
        synchronized (LOCK) { notifier = callback; }
    }

    /**
     * The path the log WOULD be written to, or null before install(). Exposed so callers can name
     * it to the user. Note it is a path, not a promise: after a failed open -- an unwritable
     * folder, say -- this still returns the intended path for a file that will never exist.
     */
    public static File file() {
        synchronized (LOCK) { return sink == null ? null : sink.target; }
    }

    private static void notifyOnce() {
        if (notified) return;                    // fast path: no lock once it has fired
        Consumer<File> callback;
        File target;
        synchronized (LOCK) {
            /* wrotePayload, not merely created, and not `written > 0` either: created is true as
             * soon as the file opens, and `written` is already past zero by then because open()
             * writes a session header (and on append it starts at the existing file length). So
             * both would announce "details were written to <path>" for a file containing nothing
             * but a header -- which is what happens if the disk fills between the header and the
             * first real line. wrotePayload is set only after a caller's bytes actually land.
             *
             * It is also volatile, because it is written under the Sink's monitor and read here
             * under LOCK; a plain field read across two different monitors has no happens-before
             * edge, and the volatile on `created` does not extend to anything written after it. */
            if (notified || notifier == null || sink == null || !sink.wrotePayload) return;
            notified = true;
            callback = notifier;
            target = sink.target;
        }
        // Deliberately OUTSIDE the lock: the callback opens a dialog, and holding LOCK across it
        // would let anything that logs from the UI block on a modal window.
        try { callback.accept(target); } catch (Throwable ignored) { }
    }

    /**
     * The file name carries the machine name.
     *
     * The Chip's Challenge folder is Dropbox-synced between two PCs (the reason settings paths are
     * stored relative -- see ADR 0004). A single shared log would be appended by both machines and
     * come back as "succ_error (Jeremy's conflicted copy).log"; worse, rotation cannot work at all
     * when another process holds the file, because Windows refuses to move or delete an open file.
     * One file per machine removes both problems without a special case.
     */
    private static String logName() {
        /* COMPUTERNAME is Windows. HOSTNAME is a best-effort second try for Linux and macOS, where
         * upstream SuperCC also runs -- but it is a shell variable that is usually NOT exported to
         * child processes (and zsh, the macOS default, does not set it at all), so in practice most
         * non-Windows launches still land on "pc". That is acceptable: this fork is Windows-first,
         * and the case the name actually defends against is Jeremy's two Windows PCs sharing one
         * Dropbox folder.
         *
         * ⚠ Do NOT "improve" this with InetAddress.getLocalHost().getHostName(). This runs inside
         * install(), on the event thread, before the window appears -- and that call can block on
         * DNS for seconds. An environment variable cannot.
         *
         * The sanitize also makes it impossible to escape the directory through a hostile
         * environment variable, which is why it strips rather than merely trims. */
        String host = firstNonBlank(System.getenv("COMPUTERNAME"), System.getenv("HOSTNAME"));
        if (host == null) host = "pc";
        host = host.replaceAll("[^A-Za-z0-9._-]", "_");
        if (host.isBlank()) host = "pc";
        if (host.length() > 32) host = host.substring(0, 32);
        return "succ_error-" + host + ".log";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    /** The console's charset, not the platform default -- they differ on Windows. */
    private static Charset consoleCharset() {
        for (String key : new String[]{"sun.stderr.encoding", "stderr.encoding", "file.encoding"}) {
            String name = System.getProperty(key);
            if (name == null || name.isBlank()) continue;
            try { return Charset.forName(name); } catch (Throwable ignored) { }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Writes to both streams; a failure of the file half never affects the console half.
     *
     * notifyOnce() is called HERE rather than only from the uncaught-exception handler, and that
     * distinction is the whole difference between the feature working and not. 26 of this program's
     * 27 error reports are a CAUGHT exception logged with printStackTrace() -- never an uncaught
     * one -- so notifying only from the handler would create the log file and never tell anyone it
     * exists, in almost every case. Under javaw that notice is the only signal a user gets.
     *
     * Lock note: this runs while the PrintStream's monitor is held and takes LOCK. Nothing ever
     * goes the other way (ErrorLog never writes to a stream while holding LOCK, and notifyOnce
     * releases LOCK before invoking the callback), so the ordering stays acyclic.
     */
    private static final class Tee extends OutputStream {
        private final OutputStream console, file;

        Tee(OutputStream console, OutputStream file) { this.console = console; this.file = file; }

        @Override public void write(int b) {
            try { console.write(b); } catch (Throwable ignored) { }
            try { file.write(b); notifyOnce(); } catch (Throwable ignored) { }
        }

        @Override public void write(byte[] b, int off, int len) {
            try { console.write(b, off, len); } catch (Throwable ignored) { }
            try { file.write(b, off, len); notifyOnce(); } catch (Throwable ignored) { }
        }

        @Override public void flush() {
            try { console.flush(); } catch (Throwable ignored) { }
            try { file.flush(); } catch (Throwable ignored) { }
        }
    }

    /**
     * The file half: opens lazily on the first byte, counts what it writes, and rotates itself.
     *
     * Lazy because a launch that goes fine should leave no file at all. The byte count is kept in
     * memory rather than stat-ing the file per write, which also keeps rotation honest inside a
     * single session -- a repaint loop throwing every frame can produce megabytes in seconds, and
     * a size check that only ran at startup would cap nothing.
     */
    private static final class Sink extends OutputStream {
        private final File target;
        private final File previous;
        private final byte[] oneByte = new byte[1];   // reused; write(int) is synchronized
        private FileOutputStream out;
        private long written;
        private boolean disabled;
        /* Both read by notifyOnce() under LOCK but written here under this object's monitor, so
         * both must be volatile for that cross-monitor read to be well defined. */
        private volatile boolean created;
        /** True once a caller's own bytes have actually reached the file -- not just the header. */
        private volatile boolean wrotePayload;

        Sink(File target) {
            this.target = target;
            String name = target.getName();
            this.previous = new File(target.getParentFile(),
                    name.substring(0, name.length() - ".log".length()) + ".prev.log");
        }

        @Override public synchronized void write(int b) { oneByte[0] = (byte) b; write(oneByte, 0, 1); }

        @Override public synchronized void write(byte[] b, int off, int len) {
            if (disabled) return;
            try {
                if (out == null && !open()) return;
                if (written + len > MAX_BYTES) rotate();
                if (out == null) return;
                out.write(b, off, len);
                written += len;
                wrotePayload = true;      // only after the caller's bytes really landed
            } catch (Throwable t) {
                // Stay off for good rather than retrying every write.
                disabled = true;
                closeQuietly();
            }
        }

        @Override public synchronized void flush() {
            try { if (out != null) out.flush(); } catch (Throwable ignored) { }
        }

        private boolean open() {
            try {
                File dir = target.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) { disabled = true; return false; }
                out = new FileOutputStream(target, true);
                written = target.length();      // 0 for a file that did not exist a moment ago
                created = true;
                header();
                return true;
            } catch (Throwable t) {
                disabled = true;
                return false;
            }
        }

        /**
         * Rotation needs the handle CLOSED first: Windows refuses to move a file that is open.
         *
         * A rotation that CANNOT happen disables logging for good. That is not defensive tidiness,
         * it is the only correct answer: bailing out here used to leave out == null with the sink
         * still enabled, so the next write reopened the file, appended a fresh session header,
         * found the size still over the cap, failed to rotate again, and dropped the payload --
         * open, header, close, discard, once per stderr write, forever. The result was an
         * ever-growing file containing nothing but session headers, with every real stack trace
         * thrown away, in a Dropbox-synced folder. Precisely when the log matters most.
         *
         * This is reachable in practice: .prev.log held open by the user reading it, by antivirus,
         * or by Dropbox is enough, because Windows will not delete or replace an open file.
         */
        private void rotate() {
            closeQuietly();
            boolean rotated = false;
            try {
                rotated = (!previous.isFile() || previous.delete()) && target.renameTo(previous);
            } catch (Throwable ignored) {
                rotated = false;
            }
            if (!rotated) { disabled = true; return; }
            written = 0;
            try {
                out = new FileOutputStream(target, false);
                header();
            } catch (Throwable t) {
                disabled = true;
            }
        }

        /* BUILD_TAG is a compile-time String constant, so javac INLINES it here -- this class ends
         * up with no runtime reference to emulator.SuperCC at all (which is a bonus: it can still
         * log if SuperCC itself fails to load). That is only correct because io\ErrorLog.java is in
         * $SPLICE_MODIFIED and so is recompiled alongside SuperCC.java on every build. Dropping it
         * from that list on the reasonable-sounding grounds that "ErrorLog hasn't changed" would
         * ship a STALE build tag in the one file whose job is saying which build crashed. Same
         * hazard the Gui comment in SuperCC.java describes. */
        private void header() {
            try {
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                byte[] head = ("\r\n===== SuperCC " + emulator.SuperCC.BUILD_TAG + "  " + stamp
                        + "  (Java " + System.getProperty("java.version") + ") =====\r\n")
                        .getBytes(StandardCharsets.UTF_8);
                out.write(head);
                written += head.length;
            } catch (Throwable ignored) { }
        }

        private void closeQuietly() {
            try { if (out != null) out.close(); } catch (Throwable ignored) { }
            out = null;
        }
    }
}
