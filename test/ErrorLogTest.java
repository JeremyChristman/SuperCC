import io.ErrorLog;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the jc-11 error log against the REAL class, by installing it.
 *
 * Installing is safe here, and the reasons are worth stating because an earlier version of this
 * file avoided it and consequently tested nothing: run-tests.ps1 gives every test class its own
 * JVM, so "for the rest of the process" means "for the rest of this file"; and Harness reports on
 * System.out, so replacing System.err takes nothing away from it.
 *
 * ErrorLog sits underneath System.err, which makes its failure modes unusually nasty -- an
 * exception escaping it surfaces inside code that was merely reporting a problem, and anything it
 * prints recurses into itself. Those are the properties pinned here, on the real object.
 */
public class ErrorLogTest {

    public static void main(String[] args) {
        System.exit(Harness.run("ErrorLogTest", ErrorLogTest::body));
    }

    private static void body() throws Exception {

        Harness.section("1. before install, nothing exists");
        Harness.check("file() is null before install", ErrorLog.file() == null);

        Path dir = Harness.tempDir("scc-errorlog-");
        File logDir = dir.toFile();

        Harness.section("2. install creates no file until something is written");
        /* Lazy creation is what lets the log mean something: if the file exists, something went
         * wrong. It only holds because jc-11 also fixed the NPE that fired on every launch. */
        PrintStream consoleBefore = System.err;
        ErrorLog.install(logDir);
        File log = ErrorLog.file();
        Harness.check("file() names a log after install", log != null);
        Harness.check("the name carries this machine, not a shared one",
                      log != null && log.getName().startsWith("succ_error-") && log.getName().endsWith(".log"));
        Harness.check("no file exists yet", log != null && !log.isFile());
        Harness.check("System.err was replaced by the tee", System.err != consoleBefore);

        Harness.section("2b. the notifier is not fired for a log that has no content yet");
        /* Registered BEFORE anything is written, which is the only ordering that exercises the
         * gate: once a write has happened the condition is trivially satisfied. The dialog must
         * never name a file the user would open and find empty. */
        AtomicInteger early = new AtomicInteger();
        ErrorLog.setNotifier(f -> early.incrementAndGet());
        Harness.eq("nothing written, so nobody is told", early.get(), 0);
        Harness.check("and still no file on disk", !log.isFile());

        Harness.section("3. a caught printStackTrace reaches the file AND the console");
        /* 26 of this program's 27 error reports are a caught exception logged this way -- never an
         * uncaught one -- so this, not the uncaught handler, is the path that matters most. */
        new IllegalStateException("CAUGHT-PROBE-MARKER").printStackTrace();
        System.err.flush();
        Harness.check("the log file now exists", log.isFile());
        String afterCaught = Files.readString(log.toPath(), StandardCharsets.UTF_8);
        Harness.check("the caught trace is in the file", afterCaught.contains("CAUGHT-PROBE-MARKER"));
        Harness.check("the session header names the build",
                      afterCaught.contains("SuperCC") && afterCaught.contains("====="));

        Harness.section("4. the notifier fires exactly once, for a CAUGHT exception");
        /* The README and the issue template both promise the user is told. If notification only
         * fired for uncaught exceptions, that promise would be false in almost every real case.
         * `early` was registered in 2b, before any content existed, so its count now also proves
         * the gate opened at the right moment rather than never. */
        Harness.eq("the notifier registered before any content fired exactly once", early.get(), 1);

        /* Single-fire is process-wide, not per-notifier: once the user has been told, replacing
         * the notifier must not tell them again. (SuperCC relies on this -- it registers a
         * pre-GUI fallback and then swaps in the real one.) */
        AtomicInteger later = new AtomicInteger();
        ErrorLog.setNotifier(f -> later.incrementAndGet());
        for (int i = 0; i < 5; i++) { new IllegalStateException("MORE-" + i).printStackTrace(); }
        System.err.flush();
        Harness.eq("a notifier registered afterward is never called", later.get(), 0);
        Harness.eq("and the original still fired only once", early.get(), 1);

        Harness.section("5. an uncaught exception on another thread is captured once");
        Thread t = new Thread(() -> { throw new RuntimeException("UNCAUGHT-PROBE-MARKER"); }, "probe-thread");
        t.start();
        t.join(5000);
        System.err.flush();
        String afterUncaught = Files.readString(log.toPath(), StandardCharsets.UTF_8);
        Harness.eq("the uncaught trace appears exactly once in the file",
                   countOf(afterUncaught, "UNCAUGHT-PROBE-MARKER"), 1);
        /* If the JVM's own ThreadGroup fallback had also printed, jc-11 would be double-logging;
         * if the handler had NOT re-emitted through System.err, jc-11 would be quieter than jc-10. */
        Harness.check("the JVM's own fallback did not also print it",
                      !afterUncaught.contains("Exception in thread \"probe-thread\""));

        Harness.section("6. it rotates at the cap and keeps the content");
        /* The failure this exists to record can be a repaint loop throwing every frame, which
         * produces megabytes in seconds -- so the cap has to hold DURING a session. */
        String filler = "x".repeat(512);
        for (int i = 0; i < 1600; i++) System.err.println(filler);   // ~820 KB, past the 512 KB cap
        System.err.flush();
        File prev = new File(log.getParentFile(),
                log.getName().substring(0, log.getName().length() - 4) + ".prev.log");
        Harness.check("a .prev.log was produced", prev.isFile());
        Harness.check("the live log was truncated, not grown past the cap", log.length() < 700_000);
        String rotated = Files.readString(log.toPath(), StandardCharsets.UTF_8);
        Harness.check("the rotated log still contains real payload, not only headers",
                      rotated.contains(filler));
        /* The regression guard for the worst bug found in review: when rotation could not happen,
         * the sink used to reopen, write a fresh header, fail to rotate, and drop the payload --
         * once per write, forever, producing an ever-growing file of nothing but headers. */
        int headers = countOf(rotated, "===== SuperCC");
        Harness.check("the rotated log is not a pile of session headers (" + headers + ")", headers <= 2);

        Harness.section("6b. a rotation that CANNOT happen shuts logging off instead of looping");
        /* THE regression guard for the worst defect found in review, and it only reproduces when
         * rotation actually fails -- a happy-path rotation behaves identically before and after the
         * fix, so a test that merely rotates proves nothing.
         *
         * Windows will not delete or replace a file another handle holds open, and .prev.log being
         * held is entirely realistic: the user opening it to read before filing a bug, antivirus,
         * or Dropbox, in a folder that is Dropbox-synced by design.
         *
         * Before the fix, a failed rotation returned with the sink still enabled, so the next write
         * reopened the file, appended a fresh session header, found it still over the cap, failed
         * to rotate again, and dropped the payload -- once per stderr write, forever, growing a
         * file that contained nothing but headers. */
        long sizeBeforeStorm;
        int headersBeforeStorm;
        try (java.io.FileOutputStream hold = new java.io.FileOutputStream(prev, true)) {
            hold.write('x');
            hold.flush();
            String before = Files.readString(log.toPath(), StandardCharsets.UTF_8);
            headersBeforeStorm = countOf(before, "===== SuperCC");
            sizeBeforeStorm = log.length();
            // Push well past the cap again, so rotation is attempted and cannot succeed.
            for (int i = 0; i < 1600; i++) System.err.println(filler);
            System.err.flush();
        }
        String stormed = Files.readString(log.toPath(), StandardCharsets.UTF_8);
        int headersAfterStorm = countOf(stormed, "===== SuperCC");
        Harness.check("no header storm while rotation was blocked ("
                      + headersBeforeStorm + " -> " + headersAfterStorm + ")",
                      headersAfterStorm - headersBeforeStorm <= 1);
        Harness.check("the log did not balloon past the cap while rotation was blocked ("
                      + sizeBeforeStorm + " -> " + log.length() + " bytes)",
                      log.length() < 900_000);
        Harness.check("System.err still works after logging shut itself off", writesCleanly());

        Harness.section("7. an unwritable destination never throws and never kills the console");
        /* The property that matters most. If the file half can throw, every printStackTrace in the
         * program turns into a second exception raised from inside error handling. */
        Path dir2 = Harness.tempDir("scc-errorlog-bad-");
        File notADir = new File(dir2.toFile(), "a-file-not-a-directory");
        Files.writeString(notADir.toPath(), "x", StandardCharsets.UTF_8);
        boolean threw = false;
        try {
            // Drive a Sink straight at a path whose parent is a regular file: it can never open.
            Class<?> sinkClass = Class.forName("io.ErrorLog$Sink");
            java.lang.reflect.Constructor<?> ctor = sinkClass.getDeclaredConstructor(File.class);
            ctor.setAccessible(true);
            Object sink = ctor.newInstance(new File(notADir, "succ_error-probe.log"));
            java.lang.reflect.Method write = sinkClass.getDeclaredMethod("write", byte[].class, int.class, int.class);
            write.setAccessible(true);
            byte[] payload = "boom\n".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 5000; i++) write.invoke(sink, payload, 0, payload.length);
            java.lang.reflect.Method flush = sinkClass.getDeclaredMethod("flush");
            flush.setAccessible(true);
            flush.invoke(sink);
        } catch (Throwable t2) {
            threw = true;
            System.out.println("        threw: " + t2);
        }
        Harness.check("5000 writes to an impossible path threw nothing", !threw);
        Harness.check("and created no file", !new File(notADir, "succ_error-probe.log").isFile());

        Harness.section("8. the console half is still intact after all of that");
        Harness.check("System.err still accepts output without throwing", writesCleanly());

        /* Release the log file before the JVM exits. ErrorLog deliberately never closes it -- it is
         * a process-lifetime tee -- but Windows will not delete a directory containing an open
         * file, so leaving it open makes Harness's cleanup hook fail silently and this test leaks
         * one temp folder per run, forever. Closing it here is test hygiene, not a product concern. */
        Harness.check("the sink could be closed for cleanup", closeSinkQuietly());
    }

    /** Reaches the installed Sink and closes it, so the temp directory can actually be removed. */
    private static boolean closeSinkQuietly() {
        try {
            java.lang.reflect.Field sinkField = Class.forName("io.ErrorLog").getDeclaredField("sink");
            sinkField.setAccessible(true);
            Object sink = sinkField.get(null);
            if (sink == null) return true;
            java.lang.reflect.Method close = sink.getClass().getDeclaredMethod("closeQuietly");
            close.setAccessible(true);
            close.invoke(sink);
            return true;
        } catch (Throwable t) {
            System.out.println("        could not close the sink: " + t);
            return false;
        }
    }

    private static boolean writesCleanly() {
        try { System.err.println("final-console-probe"); System.err.flush(); return true; }
        catch (Throwable t) { return false; }
    }

    private static int countOf(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
