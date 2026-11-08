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
 *   2. With NO level set open -- the state every launch starts in -- nothing a user can touch
 *      throws: EVERY button in EVERY menu (found by walking the menu bar, so an item added later is
 *      covered), every submenu hovered, a key press, the play button, the speed slider, and a full
 *      repaint. Measured on the build with only upstream's 72476bc guards: 26 of the 56 menu
 *      controls still threw NullPointerException on the event thread, which is why the fork guards
 *      the whole bar at once (MenuBar.requireALevel). On jc-16 more threw; the maintainer's own
 *      error log holds Restart and Next/Previous.
 *   3a. A set whose level 1 cannot load, one whose first three cannot, and one with no levels
 *      each show exactly ONE error (counted by a closer that stays on for the whole open, so a
 *      second dialog fails the count rather than hanging the child), and leave nothing half-open.
 *   3. After a level set opens, the same repaint works.
 *   4. Canceling Go to... and Set RNG Seed does not throw (it did: the reply is null, and the code
 *      tested it for empty only). A helper thread closes each dialog as its close box would.
 *   5. A level that loads is not reported as failed when the repaint after it throws.
 *   Nothing reaches the error log before 3a, nothing after 3a until 5, whose trace is expected.
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

        /** Runs on the event thread, as a click would, and reports whether it threw. */
        static void noThrow(String what, Runnable r) {
            Throwable[] thrown = { null };
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try { r.run(); } catch (Throwable t) { thrown[0] = t; }
                });
            } catch (InvocationTargetException | InterruptedException e) { thrown[0] = e; }
            fact(what + (thrown[0] == null ? "" : " (threw " + thrown[0] + ")"), thrown[0] == null);
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

        /** In ONE event-thread step, so no 50 ms window can close in between: forgets anything an
         *  earlier run left waiting or held, opens `set` afresh, presses each key through the frame as
         *  a real KEY_PRESSED, runs `then`, and returns the recorded moves at that instant. Then
         *  releases the keys, so none is left "held" for the next run. */
        static String keyRun(SuperCC emu, graphics.Gui w, File set, Runnable then, int... keys) throws Exception {
            String[] moves = { null };
            SwingUtilities.invokeAndWait(() -> {
                emu.getControls().inputInterrupted();
                emu.openLevelset(set);
                for (int k : keys)
                    w.dispatchEvent(new KeyEvent(w, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, k, KeyEvent.CHAR_UNDEFINED));
                if (then != null) then.run();
                moves[0] = emu.getSavestates().movesToString();
                for (int k : keys)
                    w.dispatchEvent(new KeyEvent(w, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, k, KeyEvent.CHAR_UNDEFINED));
            });
            return moves[0];
        }
        static String keyRun(SuperCC emu, graphics.Gui w, File set, int... keys) throws Exception {
            return keyRun(emu, w, set, null, keys);
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

            // 2. No level set open: nothing a user can touch may throw. EVERY button in EVERY menu,
            //    found by walking the menu bar -- so a menu item added later is covered too -- plus
            //    hovering each submenu (Copy macro moves threw on hover). A dialog or file chooser a
            //    click opens is closed, as its close box would, so a regression is a failure rather
            //    than a hang; the closer is stopped after each click so it cannot close a later one.
            fact("no level is loaded yet", !emu.isLevelLoaded());
            List<Object[]> controls = new ArrayList<>();
            for (int i = 0; i < w.getJMenuBar().getMenuCount(); i++) {
                JMenu m = w.getJMenuBar().getMenu(i);
                if (m != null) walk(m, m.getText() + " > ", controls);
            }
            fact("the menu bar has its controls (" + controls.size() + ")", controls.size() >= 50);
            for (Object[] c : controls) {
                Thread closer = closeNextDialog();
                if (c[1] instanceof JMenu) {
                    JMenu sub = (JMenu) c[1];
                    noThrow("hovering " + c[0] + " with no level open", () -> {
                        for (javax.swing.event.MenuListener l : sub.getMenuListeners())
                            l.menuSelected(new javax.swing.event.MenuEvent(sub));
                    });
                } else {
                    noThrow(c[0] + " with no level open", ((AbstractButton) c[1])::doClick);
                }
                closer.interrupt();
                closeToolWindows(w);
            }
            fact("and still no level is loaded (Open levelset was canceled)", !emu.isLevelLoaded());

            //    View > Tileset works with no level open (jc-17). It used to be skipped by
            //    requireALevel() while its radio mark still moved: the menu showed a tileset that was
            //    neither drawn nor saved (review). The walk above clicked every tileset in menu order.
            //    The last BUILT-IN one -- MSCC (Black and White - Editor), whose overlay image could not
            //    be found in any jar before jc-17 -- must now be drawn and saved, as the MS tileset,
            //    the one drawn at startup. The Lynx choice is untouched.
            //    Then came "custom" (jc-18), whose first file chooser the closer canceled: canceling
            //    is a no-op, so Black and White stays drawn and saved, the radio mark goes back to
            //    it, and no image path is saved (upstream made the cancel an error dialog).
            graphics.TileSheet bw = graphics.TileSheet.CCEDIT_BLACK_AND_WHITE;
            fact("custom is the last tileset in the menu", graphics.TileSheet.values()[graphics.TileSheet.values().length - 1] == graphics.TileSheet.CUSTOM);
            fact("the last built-in tileset clicked is drawn (" + bw + ")", w.getGamePanel().getTileSheet() == bw);
            fact("and saved as the MS tileset", emu.getPaths().getMSTilesetNum() == bw.ordinal());
            fact("and the Lynx tileset is left alone", emu.getPaths().getLynxTilesetNum() == 0);
            Object[] tilesetMarks = (Object[]) field(w.getJMenuBar(), "tilesetButtons");
            fact("canceling custom put the radio mark back on " + bw, ((AbstractButton) tilesetMarks[bw.ordinal()]).isSelected()
                 && !((AbstractButton) tilesetMarks[graphics.TileSheet.CUSTOM.ordinal()]).isSelected());
            fact("and saved no custom image, nor turned custom on", emu.getPaths().getCustomTilesetImages() == null
                 && !emu.getPaths().getCustomTileset(false) && !emu.getPaths().getCustomTileset(true));
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
            //     fixture until jc-17 made those load). It used to throw on a null level right
            //     after the error; now the error is shown once and nothing is left half-open. The
            //     failed load is a real error, so it SHOULD reach the log -- and nothing else may.
            //     Each open below runs under closeEveryDialog, so a second dialog is counted, not hung on.
            int beforeBad = dialogsClosed.get();
            Thread badCloser = closeEveryDialog();
            noThrow("opening a set whose first level cannot load", () -> emu.openLevelset(new File(args[1])));
            stop(badCloser);
            fact("and its error was shown, once", dialogsClosed.get() == beforeBad + 1);
            fact("and still no level is loaded", !emu.isLevelLoaded());
            fact("and the failed load was recorded in the error log", logSize() > 0);

            //     Three levels that cannot load, then one that can: the set opens at level 4, with ONE
            //     error for all three (it was one modal dialog per level).
            int beforeMulti = dialogsClosed.get();
            Thread multiCloser = closeEveryDialog();
            noThrow("opening a set whose first three levels cannot load", () -> emu.openLevelset(new File(args[2])));
            stop(multiCloser);
            fact("it opens at level 4", emu.isLevelLoaded() && emu.getLevel().getLevelNumber() == 4
                                        && "Fourth".equals(emu.getLevel().getTitle()));
            fact("with one error for the three, not three", dialogsClosed.get() == beforeMulti + 1);

            //     A set with no levels at all: it said nothing before. Now one error, and the open
            //     set is kept -- and kept WHILE the error is on screen, not only after it: the dialog
            //     runs a nested event loop, and the previous set used to be put back only once it
            //     closed, so for as long as it was open the new file sat under the old level (review).
            //     The closer reads the open set's file from inside that loop, then closes it.
            int beforeEmpty = dialogsClosed.get();
            String openSet = emu.getLevelsetPath();
            List<String> duringDialog = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread emptyCloser = closeEveryDialog(() -> duringDialog.add(emu.getLevelsetPath()));
            noThrow("opening a set with no levels", () -> emu.openLevelset(new File(args[3])));
            stop(emptyCloser);
            fact("says so, once", dialogsClosed.get() == beforeEmpty + 1);
            fact("and keeps the set that was open", emu.isLevelLoaded() && emu.getLevel().getLevelNumber() == 4);
            fact("and had already put it back while the error was showing (saw " + duringDialog + ")",
                 duringDialog.size() == 1 && openSet.equals(duringDialog.get(0)));
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
                noThrow("Level > " + text + ", then Cancel", it::doClick);
                closer.interrupt();
                // Otherwise this passes when the dialog never appears at all.
                fact("and its dialog really appeared and was canceled", dialogsClosed.get() == shown + 1);
            }
            fact("and the level is still loaded", emu.isLevelLoaded());

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
            fact("a level that loads is not reported as failed when its repaint throws",
                 dialogsClosed.get() == beforeFault);
            fact("and it did load", emu.getLevel() != levelBefore && emu.getSavestates() != null
                                    && "Gui Probe".equals(emu.getLevel().getTitle()));
            noThrow("and the window repaints again with its play button back", () -> w.repaint(true));

            // 6. Auto-diagonals (jc-18; upstream 5f928e0/da35e33, rewritten): the key listener's
            //    WIRING, against the real window. Which move a burst makes is AutoDiagonalTest's.
            //    Real KeyEvents are dispatched through the frame, so the program's AWT event
            //    listener sees them exactly as it sees a keyboard. Each sequence starts from a
            //    freshly opened set, and its recorded moves are compared with the same moves made
            //    the plain way -- with the setting off, or with the diagonal key -- so nothing here
            //    depends on how a ruleset spells a move.
            File msSet = new File(args[0]), lynxSet = new File(args[4]);
            Component moveList = (Component) field(w, "movePanel");
            Runnable click = () -> moveList.dispatchEvent(new java.awt.event.MouseEvent(moveList,
                    java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1));
            // The plain way first, with the setting off.
            String msRight    = keyRun(emu, w, msSet, KeyEvent.VK_RIGHT);
            String plainUp    = keyRun(emu, w, lynxSet, KeyEvent.VK_UP);
            String plainRight = keyRun(emu, w, lynxSet, KeyEvent.VK_RIGHT);
            String plainDiag  = keyRun(emu, w, lynxSet, KeyEvent.VK_I, KeyEvent.VK_SPACE);   // the Up-Right key, then a wait
            fact("setting OFF: an arrow moves at once, as it always has", !msRight.isEmpty() && !plainUp.isEmpty() && !plainRight.isEmpty());
            fact("and the plain moves differ from each other (so the comparisons below can fail)",
                 !plainUp.equals(plainRight) && !plainDiag.equals(plainUp));

            setAutoDiagonals(emu, true);
            fact("setting ON under MS: still at once -- MS cannot move diagonally, so nothing waits",
                 keyRun(emu, w, msSet, KeyEvent.VK_RIGHT).equals(msRight));
            fact("setting ON under Lynx: Up and Right wait for their window (nothing recorded yet)",
                 keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT).isEmpty());
            String flushed = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_SPACE);
            fact("a wait pressed while they wait comes AFTER their diagonal, not before it (" + flushed + " vs " + plainDiag + ")",
                 flushed.equals(plainDiag));
            String clicked = keyRun(emu, w, lynxSet, click, KeyEvent.VK_UP);
            fact("a mouse press anywhere makes the waiting move first (" + clicked + " vs " + plainUp + ")", clicked.equals(plainUp));
            String interrupted = keyRun(emu, w, lynxSet, () -> emu.getControls().inputInterrupted(), KeyEvent.VK_LEFT);
            fact("losing focus makes the waiting move at once, too", !interrupted.isEmpty());

            // AUTO-REPEAT: a second KEY_PRESSED of an arrow with no release between is Windows
            // repeating a held key. The review measured the first version making two moves of four
            // repeats (half speed), and a run-then-turn coming out as a diagonal. Each sequence
            // below must record exactly what the setting-off run records.
            setAutoDiagonals(emu, false);
            String offRun   = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP);
            String offDiags = keyRun(emu, w, lynxSet, KeyEvent.VK_I, KeyEvent.VK_I, KeyEvent.VK_I);
            Runnable turn = () -> {
                w.dispatchEvent(new KeyEvent(w, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
                w.dispatchEvent(new KeyEvent(w, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
                emu.getControls().inputInterrupted();   // close Right's window now, without a timer
            };
            String offTurn  = keyRun(emu, w, lynxSet, turn, KeyEvent.VK_UP, KeyEvent.VK_UP);
            setAutoDiagonals(emu, true);
            String onRun = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_UP);
            fact("Up held through three repeats: four moves, as with the setting off (" + onRun + " vs " + offRun + ")", onRun.equals(offRun));
            String onDiags = keyRun(emu, w, lynxSet, KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_RIGHT, KeyEvent.VK_RIGHT);
            fact("Up held, Right pressed and repeating twice: three diagonals, like three presses of the diagonal key ("
                 + onDiags + " vs " + offDiags + ")", onDiags.equals(offDiags));
            String onTurn = keyRun(emu, w, lynxSet, turn, KeyEvent.VK_UP, KeyEvent.VK_UP);
            fact("Up held (one repeat), let go, then Right: up, up, right -- not a diagonal (" + onTurn + " vs " + offTurn + ")",
                 onTurn.equals(offTurn));

            // A TAP: pressed and released inside the window, then nothing -- the window's own timer.
            String tapped = keyRun(emu, w, lynxSet, () -> w.dispatchEvent(new KeyEvent(w, KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED)), KeyEvent.VK_RIGHT);
            fact("a tap waits like a press (nothing recorded yet)", tapped.isEmpty());
            Thread.sleep(400);   // well past the 50 ms window; the Swing timer fires on the event thread
            String[] afterTap = { null };
            SwingUtilities.invokeAndWait(() -> afterTap[0] = emu.getSavestates().movesToString());
            fact("and still makes its move when the window closes -- upstream made none (" + afterTap[0] + " vs " + plainRight + ")",
                 afterTap[0].equals(plainRight));
            setAutoDiagonals(emu, false);

            SwingUtilities.invokeAndWait(w::dispose);
            // The parent requires this line: a click that quit the program with exit code 0 (closing
            // the main window does) would otherwise skip every check after it and still look clean.
            System.out.println("END");
        }
    }
}
