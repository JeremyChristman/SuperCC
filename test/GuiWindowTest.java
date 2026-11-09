import emulator.SuperCC;
import game.Tile;

import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * The MAIN window, graphics.Gui, built for real -- the one form class FormClassTest cannot reach.
 *
 * WHY A SECOND JVM. Gui IS the JFrame, so under java.awt.headless=true (how run-tests.ps1 runs
 * everything) its super() throws before any of its own code runs. This test therefore launches a
 * child JVM WITHOUT headless mode, in a temp folder, and that child builds the real window through
 * `new SuperCC()` -- exactly what a double-click does -- then reports what it saw, one line per
 * fact, which this side turns into assertions. Where there is no desktop to draw on, the child says
 * so and the test SKIPS rather than fails. On a desktop, a SuperCC window appears for a moment.
 *
 * WHAT IT PROVES.
 *   1. The window builds itself: every component Gui.form binds exists, and the content pane is
 *      the form's root. A Gui.class rebuilt by formclass.ps1 without a working $$$setupUI$$$ call
 *      fails here and nowhere else.
 *   2. With NO level set open -- the state every launch starts in -- EVERY button in EVERY menu
 *      (found by walking the menu bar, so an item added later is covered), every submenu hovered, a
 *      key press, the play button, the speed slider, and a full repaint. Measured on the build with
 *      only upstream's 72476bc guards: 26 of the 56 menu controls still threw NullPointerException
 *      on the event thread, which is why jc-17 to jc-20 guarded the whole bar at once
 *      (MenuBar.requireALevel). On jc-16 more threw; the maintainer's own error log holds Restart
 *      and Next/Previous.
 *   3a. A set whose level 1 cannot load, one whose first three cannot, and one with no levels
 *      each show exactly ONE error (counted by a closer that stays on for the whole open, so a
 *      second dialog fails the count rather than hanging the child).
 *   3. After a level set opens, the same repaint works.
 *   4. Canceling Go to..., Set RNG Seed and a file chooser (the reply is null).
 *   5. A level that loads is not reported as failed when the repaint after it throws.
 *   Nothing reaches the error log before 3a, nothing after 3a until 5, whose trace is expected.
 *
 * ⚠ UPSTREAM BASELINE (from jc-21): jc-17's fixes behind 2, 3a, 4 and 5 were REVERTED at the
 * maintainer's request (FORK.md, "The upstream baseline"; BASELINE-ISSUES.md UB-11 to UB-14). Where
 * upstream's code gives another answer, the child reports a PIN line -- what it saw, upstream's
 * measured answer, the correct one -- and this side judges it with Harness.upstreamBug. Every check
 * upstream passes is still an ordinary one.
 *
 * Run with -Dsupercc.jar (run-tests.ps1 passes it); the child gets this JVM's own classpath.
 */
public class GuiWindowTest {

    public static void main(String[] args) {
        System.exit(Harness.run("GuiWindowTest", GuiWindowTest::body));
    }

    private static void body() throws Exception {
        Harness.section("1. the main window, built in a non-headless JVM");
        Path dir = Harness.tempDir("scc-gui-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        b.level().title("Gui Probe").password("ABCD").chips(0).timeLimit(100)
         .tile(3, 5, Tile.CHIP_RIGHT).tile(4, 5, Tile.EXIT).end();
        File dat = b.writeTo(dir, "guiprobe.dat").toFile();
        DatBuilder bad = new DatBuilder().signature(DatBuilder.SIG_MS);
        bad.level().title("Broken").password("ABCD").chips(0).timeLimit(100).unreadable().end();  // cannot load
        File badDat = bad.writeTo(dir, "unreadable.dat").toFile();
        DatBuilder multi = new DatBuilder().signature(DatBuilder.SIG_MS);
        for (int i = 0; i < 3; i++)
            multi.level().title("Broken " + i).password("BAD" + i).chips(0).timeLimit(100).unreadable().end();
        multi.level().title("Fourth").password("GOOD").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();
        File multiDat = multi.writeTo(dir, "threebad.dat").toFile();
        File emptyDat = new DatBuilder().signature(DatBuilder.SIG_MS).writeTo(dir, "nolevels.dat").toFile();
        // Open floor around Chip, under Lynx, where auto-diagonals make diagonal moves.
        File lynxDat = new DatBuilder().signature(DatBuilder.SIG_LYNX)
                .level().title("Lynx Probe").password("WXYZ").chips(0).timeLimit(100).tile(15, 15, Tile.CHIP_DOWN).end()
                .writeTo(dir, "lynxprobe.dat").toFile();

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-Djava.awt.headless=false",
                "-Dsupercc.jar=" + System.getProperty("supercc.jar", ""),
                "-cp", System.getProperty("java.class.path"), "GuiWindowTest$Probe", dat.getAbsolutePath(),
                badDat.getAbsolutePath(), multiDat.getAbsolutePath(), emptyDat.getAbsolutePath(),
                lynxDat.getAbsolutePath());
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        List<String> lines = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String s; (s = r.readLine()) != null; ) synchronized (lines) { lines.add(s); }
            } catch (Exception ignored) { }
        });
        reader.start();
        boolean ended = p.waitFor(90, TimeUnit.SECONDS);
        if (!ended) p.destroyForcibly();
        reader.join(5000);

        List<String> out;
        synchronized (lines) { out = new ArrayList<>(lines); }
        if (out.contains("HEADLESS")) {
            Harness.skip("no desktop to draw on here, so the main window cannot be built");
            return;
        }
        Harness.check("the child JVM finished (no hang, no modal dialog)", ended);
        int facts = 0;
        for (String s : out) {
            if (s.startsWith("PASS ")) { Harness.check(s.substring(5), true); facts++; }
            else if (s.startsWith("FAIL ")) { Harness.check(s.substring(5), false); facts++; }
            else if (s.startsWith("PIN ")) {
                // The upstream baseline's pins (Probe.pin): what, actual, upstream's answer, the correct one.
                String[] pin = s.substring(4).split("\t", -1);
                if (pin.length == 4) Harness.upstreamBug(pin[0], pin[1], pin[2], pin[3]);
                else Harness.check("a well-formed PIN line from the child: " + s, false);
                facts++;
            }
            else if (s.startsWith("SKIP ")) Harness.skip(s.substring(5));
        }
        Harness.check("the child reported its checks (" + facts + ")", facts >= 20);
        Harness.check("and reached its last line (nothing quit the program partway)", out.contains("END"));
        Harness.check("and exited cleanly", ended && p.exitValue() == 0);
        if (!ended || p.exitValue() != 0 || facts < 20) {
            for (String s : out) System.out.println("        child: " + s);
        }
    }

    /** The child JVM. Prints PASS/FAIL lines; never throws out of main. */
    public static final class Probe {
        static int failed = 0;

        static void fact(String what, boolean ok) {
            System.out.println((ok ? "PASS " : "FAIL ") + what);
            if (!ok) failed++;
        }

        /** Runs on the event thread, as a click would, and returns what it threw, or null. */
        static Throwable onEventThread(Runnable r) {
            Throwable[] thrown = { null };
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try { r.run(); } catch (Throwable t) { thrown[0] = t; }
                });
            } catch (InvocationTargetException | InterruptedException e) { thrown[0] = e; }
            return thrown[0];
        }

        /** Runs on the event thread, as a click would, and reports whether it threw. */
        static void noThrow(String what, Runnable r) {
            Throwable t = onEventThread(r);
            fact(what + (t == null ? "" : " (threw " + t + ")"), t == null);
        }

        /** Runs on the event thread, as a click would: "works", or "throws <its class>". */
        static String outcome(Runnable r) {
            Throwable t = onEventThread(r);
            return t == null ? "works" : "throws " + t.getClass().getSimpleName();
        }

        /**
         * An upstream-baseline pin, judged by the parent with Harness.upstreamBug: it passes only on
         * upstream's own answer (BASELINE-ISSUES.md). The name must stay the same whatever happens.
         */
        static void pin(String what, String actual, String upstream, String correct) {
            System.out.println("PIN " + what + "\t" + actual + "\t" + upstream + "\t" + correct);
        }

        /** A check deliberately not made, with the reason; the parent records it as a skip. */
        static void skip(String why) {
            System.out.println("SKIP " + why);
        }

        /**
         * Closes the next modal dialog that appears, as its close box would (showInputDialog then
         * returns null, exactly as for Cancel). The click that opens it blocks the event thread in
         * the dialog's own loop, so this has to come from another thread; the dispose itself is
         * queued, which that loop runs.
         */
        static final java.util.concurrent.atomic.AtomicInteger dialogsClosed = new java.util.concurrent.atomic.AtomicInteger();

        static Thread closeNextDialog() {
            Thread t = new Thread(() -> {
                long end = System.currentTimeMillis() + 15_000;
                while (System.currentTimeMillis() < end) {
                    for (java.awt.Window win : java.awt.Window.getWindows()) {
                        if (win instanceof javax.swing.JDialog && win.isShowing()) {
                            dialogsClosed.incrementAndGet();
                            SwingUtilities.invokeLater(win::dispose);
                            return;
                        }
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            });
            t.setDaemon(true);
            t.start();
            return t;
        }

        /**
         * Closes EVERY modal dialog until stopped, counting each one once. closeNextDialog stops
         * after one, so where a check counts dialogs it cannot see a second: a build that went back
         * to one dialog per bad level left that one open, the child blocked until the parent's
         * 90-second kill, and the count checks never ran (reproduced by review with that mutant).
         * With this the open finishes, and the count check is what fails. Stop it with stop().
         */
        static Thread closeEveryDialog() {
            return closeEveryDialog(() -> { });
        }

        /**
         * The same, running {@code whileShowing} on the event thread -- inside the dialog's own
         * nested loop, so it sees the program's state as it is while the dialog is up -- before
         * closing each one.
         */
        static Thread closeEveryDialog(Runnable whileShowing) {
            Thread t = new Thread(() -> {
                java.util.Set<java.awt.Window> seen =
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                while (!Thread.currentThread().isInterrupted()) {
                    for (java.awt.Window win : java.awt.Window.getWindows()) {
                        // seen: the dispose is queued, so a dialog can still be showing next pass.
                        if (win instanceof javax.swing.JDialog && win.isShowing() && seen.add(win)) {
                            dialogsClosed.incrementAndGet();
                            SwingUtilities.invokeLater(() -> { whileShowing.run(); win.dispose(); });
                        }
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            });
            t.setDaemon(true);
            t.start();
            return t;
        }

        /**
         * Answers each file chooser that appears with the next of `files` -- selects it and approves,
         * as a person picking the file would -- and closes any other dialog (an error) as its close
         * box would. Records "chooser" or "dialog" in `log` for each. Stop it with stop().
         */
        static Thread answerChoosers(List<String> log, File... files) {
            java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
            Thread t = new Thread(() -> {
                java.util.Set<java.awt.Window> seen =
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                while (!Thread.currentThread().isInterrupted()) {
                    for (java.awt.Window win : java.awt.Window.getWindows()) {
                        if (!(win instanceof javax.swing.JDialog) || !win.isShowing() || !seen.add(win)) continue;
                        javax.swing.JFileChooser fc = findChooser(win);
                        int i = next.get();
                        if (fc != null && i < files.length) {
                            next.incrementAndGet();
                            log.add("chooser");
                            SwingUtilities.invokeLater(() -> { fc.setSelectedFile(files[i]); fc.approveSelection(); });
                        } else {
                            log.add(fc != null ? "chooser (closed)" : "dialog");
                            SwingUtilities.invokeLater(win::dispose);
                        }
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            });
            t.setDaemon(true);
            t.start();
            return t;
        }

        static javax.swing.JFileChooser findChooser(java.awt.Container c) {
            for (Component k : c.getComponents()) {
                if (k instanceof javax.swing.JFileChooser) return (javax.swing.JFileChooser) k;
                if (k instanceof java.awt.Container) {
                    javax.swing.JFileChooser f = findChooser((java.awt.Container) k);
                    if (f != null) return f;
                }
            }
            return null;
        }

        /** Stops a closer and waits for it, so it cannot close a dialog meant for a later check. */
        static void stop(Thread closer) throws InterruptedException {
            closer.interrupt();
            closer.join(2_000);
        }

        /** Total bytes in this folder's error log files (0 when there are none). */
        static long logSize() {
            File[] logs = new File(".").listFiles((d, n) -> n.startsWith("succ_error"));
            long total = 0;
            if (logs != null) for (File f : logs) total += f.length();
            return total;
        }

        /** Every JMenu (to hover) and every button below it, depth first, with its menu path. */
        static void walk(JMenu m, String path, List<Object[]> out) {
            for (Component c : m.getMenuComponents()) {
                if (c instanceof JMenu) {
                    out.add(new Object[]{ path + ((JMenu) c).getText(), c });
                    walk((JMenu) c, path + ((JMenu) c).getText() + " > ", out);
                } else if (c instanceof AbstractButton) {
                    out.add(new Object[]{ path + ((AbstractButton) c).getText(), c });
                }
            }
        }

        /** Closes any tool window a click opened (a frame other than the main window). */
        static void closeToolWindows(graphics.Gui main) throws Exception {
            Thread.sleep(100);
            for (java.awt.Window win : java.awt.Window.getWindows())
                if (win != main && win.isShowing() && !(win instanceof javax.swing.JDialog))
                    SwingUtilities.invokeAndWait(win::dispose);
        }

        static Object field(Object o, String name) throws Exception {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        }

        /** Six times auto-diagonals' 50 ms window: long enough for any window to have closed. */
        static final int WINDOW_CLOSED_MS = 300;

        /** In ONE event-thread step: opens `set` afresh, presses each key through the frame as a
         *  real KEY_PRESSED, runs `then`, and returns the recorded moves at that instant. Then
         *  releases the keys, so none is left "held" for the next run. Upstream's timer fires on its
         *  own thread, so the step does not hold a window open by itself: the keys are pressed and
         *  let go microseconds apart, far inside the 50 ms window (every pin here was also measured
         *  at 1 and 2 CPUs). With the switch on, it first waits out any window an earlier run opened,
         *  since one still pending would decide from THIS run's keys; with it off, no window opens. */
        static String keyRun(SuperCC emu, graphics.Gui w, File set, Runnable then, int... keys) throws Exception {
            if (emu.getPaths().getControls().autoDiagonals) Thread.sleep(WINDOW_CLOSED_MS);
            String[] moves = { null };
            SwingUtilities.invokeAndWait(() -> {
                emu.openLevelset(set);
                for (int k : keys) press(w, k, 0);
                if (then != null) then.run();
                moves[0] = emu.getSavestates().movesToString();
                for (int k : keys) release(w, k);
            });
            return moves[0];
        }
        static String keyRun(SuperCC emu, graphics.Gui w, File set, int... keys) throws Exception {
            return keyRun(emu, w, set, null, keys);
        }

        /** Like keyRun, but the keys stay DOWN until the window has closed (upstream decides from the
         *  keys still held when its timer fires), and the moves are read after that. */
        static String heldRun(SuperCC emu, graphics.Gui w, File set, int... keys) throws Exception {
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> {
                emu.openLevelset(set);
                for (int k : keys) press(w, k, 0);
            });
            Thread.sleep(WINDOW_CLOSED_MS);
            return movesThen(emu, () -> { for (int k : keys) release(w, k); });
        }

        /** A tap: pressed and let go in one step. Returns the moves at once, and after the window. */
        static String[] tapRun(SuperCC emu, graphics.Gui w, File set, int key) throws Exception {
            String atOnce = keyRun(emu, w, set, () -> release(w, key), key);
            Thread.sleep(WINDOW_CLOSED_MS);
            return new String[]{ atOnce, movesThen(emu, null) };
        }
        static String tapped(String[] tap, String plain) {
            if (tap[0].equals(plain)) return "at once";
            if (tap[0].isEmpty() && tap[1].equals(plain)) return "after the window";
            if (tap[0].isEmpty() && tap[1].isEmpty()) return "never";
            return "something else: " + tap[0] + " / " + tap[1];
        }

        /** On the event thread: the recorded moves, then `then` (if any). */
        static String movesThen(SuperCC emu, Runnable then) throws Exception {
            String[] moves = { null };
            SwingUtilities.invokeAndWait(() -> {
                moves[0] = emu.getSavestates().movesToString();
                if (then != null) then.run();
            });
            return moves[0];
        }
        static void press(Component c, int key, int modifiers) {
            c.dispatchEvent(new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, key, KeyEvent.CHAR_UNDEFINED));
        }
        static void release(Component c, int key) {
            c.dispatchEvent(new KeyEvent(c, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
        }
        /** Names a run's moves by which plain run they equal: each pair is a name and its moves. */
        static String which(String moves, String... nameThenMoves) {
            for (int i = 0; i < nameThenMoves.length; i += 2)
                if (moves.equals(nameThenMoves[i + 1])) return nameThenMoves[i];
            return "something else: " + moves;
        }

        /** Tools > Controls' checkbox, the way its window saves it. */
        static void setAutoDiagonals(SuperCC emu, boolean on) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                io.SuccPaths.Controls c = emu.getPaths().getControls();
                c.autoDiagonals = on;
                emu.getPaths().setControls(c);
            });
        }

        static JMenuItem item(graphics.Gui w, String menu, String text) {
            for (int i = 0; i < w.getJMenuBar().getMenuCount(); i++) {
                JMenu m = w.getJMenuBar().getMenu(i);
                if (m == null || !menu.equals(m.getText())) continue;
                for (Component c : m.getMenuComponents())
                    if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) return (JMenuItem) c;
            }
            return null;
        }

        public static void main(String[] args) {
            try {
                run(args);
            } catch (Throwable t) {
                // invokeAndWait wraps what the event thread threw: report the real cause, and treat a
                // missing desktop found INSIDE new SuperCC() as the skip it is, not a failure.
                Throwable c = t;
                while (c instanceof InvocationTargetException && c.getCause() != null) c = c.getCause();
                if (c instanceof HeadlessException || c instanceof java.awt.AWTError) { System.out.println("HEADLESS"); System.exit(0); }
                fact("the probe ran to the end (threw " + c + ")", false);
            }
            System.out.flush();
            System.exit(failed == 0 ? 0 : 1);
        }

        static void run(String[] args) throws Exception {
            if (GraphicsEnvironment.isHeadless()) { System.out.println("HEADLESS"); System.exit(0); }
            // As SuperCC.main does, so that anything printed to stderr lands in a log file here --
            // which section 3 then requires to be absent.
            io.ErrorLog.install(new File(".").getAbsoluteFile());
            SuperCC[] made = { null };
            SwingUtilities.invokeAndWait(() -> made[0] = new SuperCC());
            SuperCC emu = made[0];
            graphics.Gui w = emu.getMainWindow();
            fact("new SuperCC() built the main window", w != null);
            if (w == null) return;

            // 1. Every component the .form binds exists -- the work of $$$setupUI$$$.
            List<String> bound = new ArrayList<>();
            String jar = System.getProperty("supercc.jar", "");
            if (!jar.isEmpty()) {
                try (ZipFile z = new ZipFile(jar)) {
                    String form = new String(z.getInputStream(z.getEntry("java/graphics/Gui.form")).readAllBytes(), StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("(?<![\\w-])binding\\s*=\\s*\"([^\"]+)\"").matcher(form);
                    while (m.find()) bound.add(m.group(1));
                }
            }
            fact("Gui.form's bindings were read from the jar (" + bound.size() + ")", bound.size() >= 10);
            for (String name : bound) fact("bound component '" + name + "' exists", field(w, name) != null);
            fact("the content pane is the form's root panel", w.getContentPane() == field(w, "mainPanel"));

            // 2. No level set open: EVERY button in EVERY menu, found by walking the menu bar -- so a
            //    menu item added later is covered too -- plus hovering each submenu (Copy macro moves
            //    threw on hover). On upstream's menus 28 of the clicked ones throw, each pinned
            //    (below); Paste solution is not clicked (it reads the clipboard first); all the
            //    others must not throw. A dialog or file chooser a click opens is closed, as its close
            //    box would, so a regression is a failure rather than a hang; the closer is stopped
            //    after each click so it cannot close a later one.
            fact("no level is loaded yet", !emu.isLevelLoaded());
            List<Object[]> controls = new ArrayList<>();
            for (int i = 0; i < w.getJMenuBar().getMenuCount(); i++) {
                JMenu m = w.getJMenuBar().getMenu(i);
                if (m != null) walk(m, m.getText() + " > ", controls);
            }
            fact("the menu bar has its controls (" + controls.size() + ")", controls.size() >= 50);
            /* ⚠ UPSTREAM BASELINE (from jc-21): jc-17's one guard over the whole menu bar
             * (MenuBar.requireALevel) was REVERTED at the maintainer's request, so the menus are
             * upstream's: 72476bc guards six Level items and nothing else (FORK.md, "The upstream
             * baseline"; BASELINE-ISSUES.md UB-11). These are the controls that throw on upstream's
             * code with no level open, measured; each is pinned at exactly that, and every other
             * control must still work. To re-apply: `git revert` both of this step's commits, newest
             * first -- "GuiWindowTest: do not click Paste solution on upstream's menus", then
             * "Upstream baseline: revert the no-level-loaded guards"; they hold this change too.
             * If step 5 ("Upstream baseline: revert the custom-tileset fixes") is still reverted,
             * re-apply it FIRST: both steps rewrote View > Tileset's listener in MenuBar. */
            java.util.Set<String> upstreamThrows = new java.util.HashSet<>(java.util.Arrays.asList(
                    "Level > Go to...", "Level > Change stepping value",
                    "Solution > Save as", "Solution > Save", "Solution > Open", "Solution > Search for seeds",
                    "Solution > Copy solution",
                    "Solution > Copy all previous moves", "Solution > Copy all future moves",
                    "hovering Solution > Copy macro moves",
                    "Solution > Save all states to disk", "Solution > Load states from disk",
                    "TWS > Write solution to new tws", "TWS > Verify tws",
                    "View > Tileset > Tile World (CCEdit)", "View > Tileset > Tile World",
                    "View > Tileset > MSCC (CCEdit)", "View > Tileset > MSCC",
                    "View > Tileset > MSCC (Black and White - Editor)", "View > Tile size > custom",
                    "Tools > Variation testing", "Tools > TSP Solver",
                    "Cheats > Change inventory", "Cheats > Change timer",
                    "Cheats > Change Monster List Positions", "Cheats > Change Slip List Positions",
                    "Cheats > Press Green Button", "Cheats > Press Blue Button"));
            int pinnedControls = 0;
            for (Object[] c : controls) {
                Thread closer = closeNextDialog();
                String name;
                Runnable act;
                if (c[1] instanceof JMenu) {
                    JMenu sub = (JMenu) c[1];
                    name = "hovering " + c[0];
                    act = () -> {
                        for (javax.swing.event.MenuListener l : sub.getMenuListeners())
                            l.menuSelected(new javax.swing.event.MenuEvent(sub));
                    };
                } else {
                    name = (String) c[0];
                    act = ((AbstractButton) c[1])::doClick;
                }
                if (name.equals("Solution > Paste solution")) {
                    /* Not clicked on upstream's menus. Paste reads the system clipboard BEFORE it
                     * touches the level, so with no level open what it does depends on what the
                     * clipboard holds: with text it fails on the missing level (UB-11; seen on the
                     * desktop), with none it prints an UnsupportedFlavorException trace to the error
                     * log (CI, 2026-11-08). A test must not depend on the clipboard, and must never
                     * write to the user's. jc-17 to jc-20 skipped the paste with no level open, so
                     * this did not arise. */
                    skip(name + " with no level open: depends on the system clipboard (UB-11), not clicked");
                    closer.interrupt();
                    continue;
                }
                if (name.equals("View > Tileset > custom")) {
                    /* ⚠ UPSTREAM BASELINE (from jc-22, UB-18): upstream asks for BOTH images even when
                     * the first chooser is canceled, then reports "No tileset image provided" -- three
                     * dialogs, which closeNextDialog (one) would leave hanging. jc-18 to jc-21 stopped
                     * at the first Cancel. Counted with closeEveryDialog, and pinned. */
                    stop(closer);
                    int beforeCustom = dialogsClosed.get();
                    Thread every = closeEveryDialog();
                    String did = outcome(act);
                    stop(every);
                    int shown = dialogsClosed.get() - beforeCustom;
                    pin(name + ", canceled, with no level open", did + ", " + (shown == 1 ? "one chooser"
                            : shown == 3 ? "both choosers and an error" : shown + " dialogs"),
                        "works, both choosers and an error", "works, one chooser");
                    closeToolWindows(w);
                    continue;
                }
                if (upstreamThrows.contains(name)) {
                    pinnedControls++;
                    pin(name + " with no level open", outcome(act), "throws NullPointerException", "works");
                } else {
                    noThrow(name + " with no level open", act);
                }
                closer.interrupt();
                closeToolWindows(w);
            }
            fact("every control upstream throws on was found in the menus (" + pinnedControls + " of "
                 + upstreamThrows.size() + ")", pinnedControls == upstreamThrows.size());
            fact("and still no level is loaded (Open levelset was canceled)", !emu.isLevelLoaded());

            //    View > Tileset with no level open. jc-17 to jc-20 made it work (it had been skipped by
            //    requireALevel() while its radio mark still moved: a tileset neither drawn nor saved).
            //    The walk above clicked every tileset in menu order. The last BUILT-IN one -- MSCC
            //    (Black and White - Editor), whose overlay image could not be found in any jar before
            //    jc-17 -- is drawn; saving it as the MS tileset is pinned (UB-11), since upstream
            //    throws before it saves. The Lynx choice is untouched.
            //    Then came "custom" (jc-18), whose choosers the closer canceled. jc-18 to jc-21 made
            //    that a no-op that put the radio mark back on Black and White; upstream leaves the mark
            //    on custom, which is not drawn (UB-18, pinned). Either way no image path is saved.
            graphics.TileSheet bw = graphics.TileSheet.CCEDIT_BLACK_AND_WHITE;
            fact("custom is the last tileset in the menu", graphics.TileSheet.values()[graphics.TileSheet.values().length - 1] == graphics.TileSheet.CUSTOM);
            fact("the last built-in tileset clicked is drawn (" + bw + ")", w.getGamePanel().getTileSheet() == bw);
            // ⚠ UPSTREAM BASELINE (UB-11): upstream draws it, then throws reading the level's ruleset
            // before it saves, so the stock 0 stays.
            pin("and saved as the MS tileset", String.valueOf(emu.getPaths().getMSTilesetNum()), "0", String.valueOf(bw.ordinal()));
            fact("and the Lynx tileset is left alone", emu.getPaths().getLynxTilesetNum() == 0);
            Object[] tilesetMarks = (Object[]) field(w.getJMenuBar(), "tilesetButtons");
            boolean markOnBw = ((AbstractButton) tilesetMarks[bw.ordinal()]).isSelected();
            boolean markOnCustom = ((AbstractButton) tilesetMarks[graphics.TileSheet.CUSTOM.ordinal()]).isSelected();
            pin("canceling custom put the radio mark back on " + bw,
                markOnBw && !markOnCustom ? "on " + bw : markOnCustom ? "on custom" : "elsewhere", "on custom", "on " + bw);
            String[] noImages = emu.getPaths().getCustomTilesetImages();
            fact("and saved no custom image, nor chose custom",
                 (noImages == null || (noImages[0].isEmpty() && noImages[1].isEmpty()))
                 && emu.getPaths().getMSTilesetNum() != graphics.TileSheet.CUSTOM.ordinal());

            //    And custom with two real pictures CHOSEN, still with no level open (answerChoosers
            //    approves each chooser with a file, as a person would). jc-18 to jc-21 saved it as the
            //    MS choice; ⚠ upstream saves the pictures, draws them, then throws reading the level's
            //    ruleset, so the tileset choice itself is never saved (UB-11, pinned).
            File cBase = new File("cbase.png").getAbsoluteFile(), cOver = new File("coverlay.png").getAbsoluteFile();
            File cText = new File("cnotes.png").getAbsoluteFile();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(28, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", cBase);
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(28, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", cOver);
            Files.writeString(cText.toPath(), "this is text, not a picture");
            AbstractButton customMark = (AbstractButton) tilesetMarks[graphics.TileSheet.CUSTOM.ordinal()];
            AbstractButton bwMark = (AbstractButton) tilesetMarks[bw.ordinal()];
            // Pictures of their own, so section 5b's check of what IT saves cannot be satisfied by
            // this pick's lines (review).
            File nBase = new File("nolevel-base.png").getAbsoluteFile(), nOver = new File("nolevel-overlay.png").getAbsoluteFile();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(28, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", nBase);
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(28, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", nOver);
            List<String> picked = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread pick = answerChoosers(picked, nBase, nOver);
            pin("View > Tileset > custom, two pictures chosen, with no level open", outcome(customMark::doClick),
                "throws NullPointerException", "works");
            stop(pick);
            fact("and both of its choosers were answered (" + picked + ")",
                 picked.size() >= 2 && picked.get(0).equals("chooser") && picked.get(1).equals("chooser"));
            for (KeyListener k : w.getKeyListeners()) {
                noThrow("a key press (" + k.getClass().getSimpleName() + ") with no level open", () -> {
                    k.keyPressed(new KeyEvent(w, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
                    k.keyPressed(new KeyEvent(w, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_A, 'a'));
                });
            }
            AbstractButton play = (AbstractButton) field(w, "playButton");
            noThrow("the play button with no level open", play::doClick);
            JSlider speed = (JSlider) field(w, "speedSlider");
            noThrow("the speed slider with no level open", () -> speed.setValue(speed.getValue() == speed.getMinimum() ? speed.getMaximum() : speed.getMinimum()));
            JSlider time = (JSlider) field(w, "timeSlider");
            noThrow("dragging the time slider with no level open", () -> {
                time.setValueIsAdjusting(true);
                time.setValue(time.getValue() == time.getMinimum() ? time.getMaximum() : time.getMinimum());
                time.setValueIsAdjusting(false);
            });
            Component game = (Component) field(w, "gamePanel");
            noThrow("left- and right-clicking and moving over the game area with no level open", () -> {
                long now = System.currentTimeMillis();
                for (java.awt.event.MouseListener l : game.getMouseListeners()) {
                    l.mousePressed(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_PRESSED, now, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 30, 30, 1, false, java.awt.event.MouseEvent.BUTTON1));
                    l.mouseReleased(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_RELEASED, now, 0, 30, 30, 1, false, java.awt.event.MouseEvent.BUTTON1));
                    l.mousePressed(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_PRESSED, now, java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 30, 30, 1, true, java.awt.event.MouseEvent.BUTTON3));
                    l.mouseReleased(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_RELEASED, now, 0, 30, 30, 1, true, java.awt.event.MouseEvent.BUTTON3));
                }
                for (java.awt.event.MouseMotionListener l : game.getMouseMotionListeners())
                    l.mouseMoved(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_MOVED, now, 0, 40, 40, 0, false));
            });
            Component moves = (Component) field(w, "movePanel");
            noThrow("scrolling the move list with no level open", () -> {
                for (java.awt.event.MouseWheelListener l : moves.getMouseWheelListeners())
                    l.mouseWheelMoved(new java.awt.event.MouseWheelEvent(moves, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 10, 10, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
                moves.paint(moves.getGraphics());
            });
            noThrow("a full repaint with no level open", () -> w.repaint(true));

            fact("nothing so far wrote to the error log", logSize() == 0);

            // 3a. A set whose level 1 cannot load (a corrupt record -- an untitled level was the
            //     fixture until jc-17 made those load). Before jc-17 it threw on a null level right
            //     after the error; jc-17 to jc-20 showed the error once and left nothing half-open;
            //     upstream's code, restored, throws again (pinned below). The failed load is a real
            //     error, so it SHOULD reach the log -- and nothing else may.
            //     Each open below runs under closeEveryDialog, so a second dialog is counted, not hung on.
            //     ⚠ UPSTREAM BASELINE (from jc-21, UB-14): jc-17's open-at-the-first-level-that-loads
            //     was REVERTED. Upstream tries level 1 only, shows its error, and then throws on the
            //     null level swapping the tileset; each check below that differs is pinned at that.
            int beforeBad = dialogsClosed.get();
            Thread badCloser = closeEveryDialog();
            pin("opening a set whose first level cannot load", outcome(() -> emu.openLevelset(new File(args[1]))),
                "throws NullPointerException", "works");
            stop(badCloser);
            fact("and its error was shown, once", dialogsClosed.get() == beforeBad + 1);
            fact("and still no level is loaded", !emu.isLevelLoaded());
            fact("and the failed load was recorded in the error log", logSize() > 0);

            //     Three levels that cannot load, then one that can: the set opens at level 4, with ONE
            //     error for all three (it was one modal dialog per level).
            int beforeMulti = dialogsClosed.get();
            Thread multiCloser = closeEveryDialog();
            pin("opening a set whose first three levels cannot load", outcome(() -> emu.openLevelset(new File(args[2]))),
                "throws NullPointerException", "works");
            stop(multiCloser);
            pin("it opens at level 4", !emu.isLevelLoaded() ? "no level loaded"
                                       : "level " + emu.getLevel().getLevelNumber() + " " + emu.getLevel().getTitle(),
                "no level loaded", "level 4 Fourth");
            fact("with one error for the three, not three", dialogsClosed.get() == beforeMulti + 1);

            //     A set with no levels at all, opened while a GOOD set is open -- the case where a
            //     failed open does its damage. jc-17 to jc-20 show one error and keep the open set,
            //     and keep it WHILE the error is on screen, not only after it: the dialog runs a nested
            //     event loop, and the previous set used to be put back only once it closed (review).
            //     The closer reads the open set's file from inside that loop, then closes it.
            //     Upstream keeps the open LEVEL, but under the new set's FILE (UB-14, pinned), so
            //     Solution > Save would file that level's route in the new set's folder.
            int beforeGood = dialogsClosed.get();
            Thread goodCloser = closeEveryDialog();
            noThrow("opening a good set first", () -> emu.openLevelset(new File(args[0])));
            stop(goodCloser);
            fact("and no error dialog appeared while it opened", dialogsClosed.get() == beforeGood);
            int beforeEmpty = dialogsClosed.get();
            String openSet = emu.getLevelsetPath();
            List<String> duringDialog = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread emptyCloser = closeEveryDialog(() -> duringDialog.add(emu.getLevelsetPath()));
            noThrow("opening a set with no levels", () -> emu.openLevelset(new File(args[3])));
            stop(emptyCloser);
            fact("says so, once", dialogsClosed.get() == beforeEmpty + 1);
            fact("and keeps the level that was open", emu.isLevelLoaded() && "Gui Probe".equals(emu.getLevel().getTitle()));
            String fileNow = emu.getLevelsetPath();
            pin("and the open set's file with it", fileNow.equals(openSet) ? "the open set's file"
                                                  : fileNow.endsWith("nolevels.dat") ? "the new set's file" : fileNow,
                "the new set's file", "the open set's file");
            String seen = duringDialog.size() != 1 ? duringDialog.size() + " readings"
                        : openSet.equals(duringDialog.get(0)) ? "the open set's file"
                        : duringDialog.get(0).endsWith("nolevels.dat") ? "the new set's file" : duringDialog.get(0);
            pin("and had already put it back while the error was showing", seen, "the new set's file", "the open set's file");
            long logAfterBad = logSize();

            // 3. With a level set open, the full repaint path runs. Any dialog while opening it is an
            //    error dialog (throwError): closed so the run cannot hang, and reported as a failure.
            int before = dialogsClosed.get();
            Thread errorCloser = closeEveryDialog();
            noThrow("opening a level set", () -> emu.openLevelset(new File(args[0])));
            stop(errorCloser);
            fact("and no error dialog appeared while it opened", dialogsClosed.get() == before);
            fact("a level is loaded now", emu.isLevelLoaded());
            noThrow("a full repaint with a level open", () -> w.repaint(true));
            fact("the title names the level", w.getTitle().contains("Gui Probe"));

            // 4. Canceling the two input dialogs. showInputDialog returns null for Cancel or the
            //    close box; upstream tested the reply for empty only, so canceling threw.
            for (String text : new String[]{ "Go to...", "Set RNG Seed" }) {
                JMenuItem it = item(w, "Level", text);
                fact("Level > " + text + " exists", it != null);
                if (it == null) continue;
                int shown = dialogsClosed.get();
                Thread closer = closeNextDialog();
                // ⚠ UPSTREAM BASELINE (from jc-21, UB-12): upstream tests the reply for empty only,
                // and Cancel's reply is null.
                pin("Level > " + text + ", then Cancel", outcome(it::doClick), "throws NullPointerException", "works");
                closer.interrupt();
                // Otherwise this passes when the dialog never appears at all.
                fact("and its dialog really appeared and was canceled", dialogsClosed.get() == shown + 1);
            }
            fact("and the level is still loaded", emu.isLevelLoaded());

            //    And a file chooser. ⚠ UPSTREAM BASELINE (from jc-21, UB-13): jc-12 made a canceled
            //    chooser a no-op in MenuBar.openFileBytes; upstream's 72476bc wraps the null reply in
            //    Objects.requireNonNull, and the baseline keeps upstream's line.
            JMenuItem openRoute = item(w, "Solution", "Open");
            fact("Solution > Open exists", openRoute != null);
            if (openRoute != null) {
                int shownOpen = dialogsClosed.get();
                Thread closer = closeNextDialog();
                pin("Solution > Open, then Cancel", outcome(openRoute::doClick), "throws NullPointerException", "works");
                closer.interrupt();
                fact("and its file chooser really appeared and was canceled", dialogsClosed.get() == shownOpen + 1);
            }

            Thread.sleep(200);
            fact("nothing after the failed load wrote to the error log", logSize() == logAfterBad);

            // 5. A load that WORKED is not reported as a failure when the GUI work after it throws.
            //    openLevelset counted it as one: with the repaint made to throw, a good set showed
            //    "Level 1 could not be loaded ... Opened at level 1." (review; reproduced). A null
            //    playButton makes repaint throw, as a jar replaced under a running instance does
            //    (CLAUDE.md section 9). The open may then throw from its tileset swap, which is not
            //    what this checks, so that is caught here; the stack trace it logs is expected,
            //    which is why the log check above comes first.
            java.lang.reflect.Field playButton = graphics.Gui.class.getDeclaredField("playButton");
            playButton.setAccessible(true);
            Object realButton = playButton.get(w);
            game.Level levelBefore = emu.getLevel();
            int beforeFault = dialogsClosed.get();
            Thread faultCloser = closeEveryDialog();
            playButton.set(w, null);
            SwingUtilities.invokeAndWait(() -> {
                try { emu.openLevelset(new File(args[0])); } catch (Throwable expected) { }
            });
            playButton.set(w, realButton);
            stop(faultCloser);
            // ⚠ UPSTREAM BASELINE (from jc-21, UB-14): upstream's loadLevel reports any throw inside
            // it, the repaint's included, as "Could not load level".
            int faultDialogs = dialogsClosed.get() - beforeFault;
            pin("a level that loads is not reported as failed when its repaint throws",
                faultDialogs == 0 ? "not reported" : faultDialogs == 1 ? "reported as failed" : faultDialogs + " dialogs",
                "reported as failed", "not reported");
            fact("and it did load", emu.getLevel() != levelBefore && emu.getSavestates() != null
                                    && "Gui Probe".equals(emu.getLevel().getTitle()));
            noThrow("and the window repaints again with its play button back", () -> w.repaint(true));

            // 5b. View > Tileset > custom, with a level open and real pictures CHOSEN in its two file
            //     choosers (answerChoosers approves each with a file, as a person would). jc-18 to
            //     jc-21 stored custom as a switch of its own beside a built-in TilesheetNum, stored
            //     the paths relative to this folder (the Chip's Challenge folder), and loaded both
            //     pictures before saving them. ⚠ UPSTREAM BASELINE (from jc-22): all reverted
            //     (UB-16, UB-17, UB-19) -- upstream stores custom as tileset number 5, the paths
            //     absolute, and saves a picture before it knows the picture loads. Each is pinned at
            //     upstream's, read from the settings file this window writes.
            List<String> answered = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread chooser = answerChoosers(answered, cBase, cOver);
            noThrow("View > Tileset > custom, two pictures chosen, with a level open", customMark::doClick);
            stop(chooser);
            fact("both choosers were answered, and no error shown (" + answered + ")",
                 answered.equals(java.util.Arrays.asList("chooser", "chooser")));
            fact("the custom tileset is drawn", w.getGamePanel().getTileSheet() == graphics.TileSheet.CUSTOM);
            Thread.sleep(200);
            List<String> ini = java.util.Arrays.asList(Files.readString(Path.of(io.SuccPaths.SETTINGS_FILE_NAME)).split("\\R"));
            pin("choosing custom keeps TilesheetNum a built-in number, with custom a switch of its own",
                ini.contains("TilesheetNum = 5") ? "custom saved as tileset number 5"
                    : ini.contains("CustomTileset = true") ? "custom saved as its own switch" : "neither",
                "custom saved as tileset number 5", "custom saved as its own switch");
            pin("and its pictures, inside the Chip's Challenge folder, are saved relative to it",
                ini.contains("CustomBaseImage = cbase.png") ? "relative"
                    : ini.contains("CustomBaseImage = " + cBase.getPath()) ? "absolute" : "neither",
                "absolute", "relative");
            answered.clear();
            chooser = answerChoosers(answered, cText, cOver);
            pin("choosing a file that is not a picture", outcome(customMark::doClick), "throws NullPointerException", "works");
            stop(chooser);
            // The harness, not the program: both choosers must really have been answered, or the
            // pin above would be reading a canceled pick (review).
            fact("and both of its choosers were answered (" + answered + ")",
                 answered.size() >= 2 && answered.get(0).equals("chooser") && answered.get(1).equals("chooser"));
            Thread.sleep(200);
            String iniText = Files.readString(Path.of(io.SuccPaths.SETTINGS_FILE_NAME));
            pin("and that file is not saved as a custom picture", iniText.contains("cnotes.png") ? "saved" : "not saved",
                "saved", "not saved");
            Thread restore = closeEveryDialog();
            noThrow("and a built-in tileset can be chosen again", bwMark::doClick);
            stop(restore);

            // 6. Auto-diagonals (upstream 5f928e0, fb8fe25, da35e33, 8c95b3e), exactly as upstream
            //    wrote them (the upstream baseline, FORK.md). Upstream's key listener sends EVERY game
            //    key -- arrows, diagonal keys, waits, Backspace, Enter -- through a 50 ms window, and
            //    when its java.util.Timer fires, on that timer's own thread, it makes ONE move from the
            //    keys STILL HELD. jc-18 to jc-22 rewrote it; each of the rewrite's fixes is pinned here
            //    at upstream's measured answer (BASELINE-ISSUES.md, UB-22 to UB-27). Real KeyEvents go
            //    through the frame, and every sequence is compared with the same moves made the plain
            //    way -- with the setting off, or with a diagonal key -- so nothing here depends on how a
            //    ruleset spells a move. Re-applying the rewrite (git revert) brings back its own
            //    checks, and AutoDiagonalTest with them.
            File msSet = new File(args[0]), lynxSet = new File(args[4]);
            Component moveList = (Component) field(w, "movePanel");
            // The plain way first, with the setting off: every key moves at once.
            String msUp       = keyRun(emu, w, msSet, KeyEvent.VK_UP);
            String msRight    = keyRun(emu, w, msSet, KeyEvent.VK_RIGHT);
            String msUpRight  = keyRun(emu, w, msSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT);
            String plainUp    = keyRun(emu, w, lynxSet, KeyEvent.VK_UP);
            String plainRight = keyRun(emu, w, lynxSet, KeyEvent.VK_RIGHT);
            String plainWait  = keyRun(emu, w, lynxSet, KeyEvent.VK_SPACE);
            String plainDiag  = keyRun(emu, w, lynxSet, KeyEvent.VK_I);                     // the Up-Right key
            String diagWait   = keyRun(emu, w, lynxSet, KeyEvent.VK_I, KeyEvent.VK_SPACE);
            String downRight  = keyRun(emu, w, lynxSet, KeyEvent.VK_K);                     // the Down-Right key
            String upUp       = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_UP);
            String upRight    = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT);
            String upDiag     = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_I);
            String upFour     = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP);
            List<String> plain = List.of(plainUp, plainRight, plainWait, plainDiag, diagWait, downRight, upUp, upRight, upDiag, upFour);
            fact("setting OFF: every key moves at once, as it always has",
                 !msUp.isEmpty() && !msRight.isEmpty() && plain.stream().noneMatch(String::isEmpty));
            fact("and the plain runs differ from each other (so the comparisons below can fail)",
                 new HashSet<>(plain).size() == plain.size() && new HashSet<>(List.of(msUp, msRight, msUpRight)).size() == 3);

            setAutoDiagonals(emu, true);
            fact("setting ON under Lynx: Up and Right wait for the window (nothing recorded yet)",
                 keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT).isEmpty());
            fact("and held past it, they make the diagonal",
                 heldRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT).equals(plainDiag));

            // UB-22: a tap -- pressed and let go inside the window -- makes no move: the timer finds
            // nothing held. Every game key waits, so this is true of all of them.
            pin("setting ON: a tapped arrow still moves (after the window)",
                tapped(tapRun(emu, w, lynxSet, KeyEvent.VK_RIGHT), plainRight), "never", "after the window");
            pin("a tapped wait key moves at once -- only an arrow has a partner to wait for",
                tapped(tapRun(emu, w, lynxSet, KeyEvent.VK_SPACE), plainWait), "never", "at once");
            pin("so does a tapped diagonal key",
                tapped(tapRun(emu, w, lynxSet, KeyEvent.VK_I), plainDiag), "never", "at once");

            // UB-23: keys inside one window make ONE move between them.
            pin("Up repeating three times inside one window (Windows repeats a held key every ~33 ms): four moves, as with the setting off",
                which(heldRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP),
                      "four moves", upFour, "one move", plainUp),
                "one move", "four moves");
            pin("a wait pressed while Up and Right wait: their diagonal, then the wait",
                which(heldRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_SPACE),
                      "the diagonal, then the wait", diagWait, "the diagonal only: the wait is lost", plainDiag),
                "the diagonal only: the wait is lost", "the diagonal, then the wait");
            pin("Down, Up and Right held: the newer of Down and Up wins, so up-right",
                which(heldRun(emu, w, lynxSet, KeyEvent.VK_DOWN, KeyEvent.VK_UP, KeyEvent.VK_RIGHT),
                      "up-right", plainDiag, "down-right, by the order of upstream's table", downRight),
                "down-right, by the order of upstream's table", "up-right");
            // Up held (its own window closes: up), then Right tapped while Up is still down.
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> { emu.openLevelset(lynxSet); press(w, KeyEvent.VK_UP, 0); });
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> { press(w, KeyEvent.VK_RIGHT, 0); release(w, KeyEvent.VK_RIGHT); });
            Thread.sleep(WINDOW_CLOSED_MS);
            pin("Up held, Right tapped: up, then the diagonal",
                which(movesThen(emu, () -> release(w, KeyEvent.VK_UP)),
                      "up, then the diagonal", upDiag, "up, then up: the tapped Right is lost", upUp),
                "up, then up: the tapped Right is lost", "up, then the diagonal");

            // UB-24: under MS the window is there too, though MS has no diagonal to make.
            pin("setting ON under MS: an arrow moves at once -- MS cannot move diagonally, so nothing waits",
                which(keyRun(emu, w, msSet, KeyEvent.VK_RIGHT), "at once", msRight, "waits", ""), "waits", "at once");
            pin("under MS, Up held and Right pressed: up, then right, as with the setting off",
                which(heldRun(emu, w, msSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT),
                      "up, then right", msUpRight, "up only: the Right is lost", msUp),
                "up only: the Right is lost", "up, then right");

            // UB-25: other input does not wait, so it jumps ahead of a waiting move. Shift+1 saves a
            // state; 1 loads it. Saved while Up waits, the state must hold Up's move.
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> {
                emu.openLevelset(lynxSet);
                press(w, KeyEvent.VK_UP, 0);
                press(w, KeyEvent.VK_1, KeyEvent.SHIFT_DOWN_MASK);
                release(w, KeyEvent.VK_1);
            });
            Thread.sleep(WINDOW_CLOSED_MS);
            String beforeLoad = movesThen(emu, () -> {
                release(w, KeyEvent.VK_UP);
                press(w, KeyEvent.VK_1, 0);
                release(w, KeyEvent.VK_1);
            });
            fact("(Up made its move before the state was loaded: " + beforeLoad + ")", beforeLoad.equals(plainUp));
            pin("a state saved (Shift+1) while Up waits is saved AFTER Up's move",
                which(movesThen(emu, null), "after Up's move", plainUp, "before it: loading it undoes Up", ""),
                "before it: loading it undoes Up", "after Up's move");

            // UB-26: the move is made on the timer's own thread, not on the event thread that all
            // other input and all drawing use. Here the event thread is busy for the whole window.
            Thread.sleep(WINDOW_CLOSED_MS);
            String[] busy = { null };
            SwingUtilities.invokeAndWait(() -> {
                emu.openLevelset(lynxSet);
                press(w, KeyEvent.VK_UP, 0);
                press(w, KeyEvent.VK_RIGHT, 0);
                try { Thread.sleep(WINDOW_CLOSED_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                busy[0] = emu.getSavestates().movesToString();
                release(w, KeyEvent.VK_UP);
                release(w, KeyEvent.VK_RIGHT);
            });
            pin("the waiting move is made on the event thread, so not while that thread is busy",
                which(busy[0], "it waits for the event thread", "", "made anyway, beside the busy event thread", plainDiag),
                "made anyway, beside the busy event thread", "it waits for the event thread");

            // UB-27: a release the main window never sees -- here it reaches the move list, as it does
            // when anything else has the keyboard -- leaves the key held for every later window. Last,
            // because under upstream the key stays held until pressed and let go in the main window.
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> { emu.openLevelset(lynxSet); press(w, KeyEvent.VK_UP, 0); });
            Thread.sleep(WINDOW_CLOSED_MS);
            SwingUtilities.invokeAndWait(() -> {
                release(moveList, KeyEvent.VK_UP);
                press(w, KeyEvent.VK_RIGHT, 0);
                release(w, KeyEvent.VK_RIGHT);
            });
            Thread.sleep(WINDOW_CLOSED_MS);
            pin("Up let go while the move list had the keyboard, then Right tapped: up, then right",
                which(movesThen(emu, () -> { press(w, KeyEvent.VK_UP, 0); release(w, KeyEvent.VK_UP); }),
                      "up, then right", upRight, "up, then up: Up is still held", upUp),
                "up, then up: Up is still held", "up, then right");
            Thread.sleep(WINDOW_CLOSED_MS);
            setAutoDiagonals(emu, false);

            SwingUtilities.invokeAndWait(w::dispose);
            // The parent requires this line: a click that quit the program with exit code 0 (closing
            // the main window does) would otherwise skip every check after it and still look clean.
            System.out.println("END");
        }
    }
}
