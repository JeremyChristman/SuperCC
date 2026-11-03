import emulator.Solution;
import emulator.SuperCC;
import game.Direction;
import game.Ruleset;
import game.Step;
import game.Tile;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPanelUI;
import java.awt.HeadlessException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The fifteen form-based classes -- the GUI windows whose .class is committed IntelliJ bytecode, or,
 * since jc-16, rebuilt by formclass.ps1 (docs\adr\0010). This RUNS their real constructors.
 *
 * WHY. formclass.ps1 -Check compares a rebuilt class with the committed one, and once a class has
 * been rebuilt both sides came from the same tool -- so a defect in the tool passes it. The hostile
 * review of jc-16 measured four such defects that kept every other gate green and broke the window
 * only when it opened:
 * - a final bound field: IllegalAccessError;
 * - the $$$setupUI$$$ call placed on one branch only: NullPointerException on a bound field;
 * - an exception skipping it: the same;
 * - a second top-level class dropped: NoClassDefFoundError.
 * Running the constructor is the check that cannot be fooled that way.
 *
 * HOW, HEADLESS. Each window's constructor builds its components ($$$setupUI$$$, which IntelliJ
 * inserts at the first call after super()) before it opens its JFrame, and the JFrame throws
 * HeadlessException. So reaching that exception -- or returning, for the two tools with a
 * no-window constructor -- means setup ran and everything the constructor touched existed. A spy
 * PanelUI, installed through UIManager, also counts the panels built during the call: every form
 * here has a JPanel at its root, so zero means $$$setupUI$$$ never ran.
 *
 * NOT COVERED: graphics.Gui. It IS the JFrame, so its super() throws HeadlessException before any
 * of its own code runs; nothing headless can reach its setup. Launching the program is its check
 * (the release playtest does that).
 *
 * Run with -Djava.awt.headless=true and -Dsupercc.jar (run-tests.ps1 passes both).
 */
public class FormClassTest {

    public static void main(String[] args) {
        System.exit(Harness.run("FormClassTest", FormClassTest::body));
    }

    /** Counts every JPanel built while installed. Must be public with a public static createUI. */
    public static final class SpyPanelUI extends BasicPanelUI {
        static final AtomicInteger made = new AtomicInteger();
        public static ComponentUI createUI(JComponent c) { made.incrementAndGet(); return BasicPanelUI.createUI(c); }
    }

    /** What a constructor is expected to end with, headless. */
    private enum End { WINDOW, RETURNS, NO_MAIN_WINDOW }

    /** Every constructor this test has run -- section 3 checks no form class has one it skipped. */
    private static final java.util.Set<Constructor<?>> ran = new java.util.HashSet<>();

    private static void construct(String fqcn, End expected, Class<?>[] types, Object... args) throws Exception {
        String what = fqcn.substring(fqcn.lastIndexOf('.') + 1) + "(" + types.length + " args)";
        Constructor<?> k = Class.forName(fqcn).getDeclaredConstructor(types);
        ran.add(k);
        k.setAccessible(true);
        Object before = UIManager.get("PanelUI");
        UIManager.put("PanelUI", SpyPanelUI.class.getName());
        SpyPanelUI.made.set(0);
        Throwable thrown = null;
        try {
            k.newInstance(args);
        } catch (InvocationTargetException e) {
            thrown = e.getCause();
        } finally {
            UIManager.put("PanelUI", before);
        }
        End got;
        if (thrown == null) got = End.RETURNS;
        else if (thrown instanceof HeadlessException) got = End.WINDOW;
        /* ChooseWindowSize reads emulator.getMainWindow().getGamePanel(), and the headless emulator
         * has no main window. That is the ONLY NullPointerException accepted, matched on the JVM's
         * own message: an NPE naming a field ("this.spinner" is null) is the missing-setup failure. */
        else if (thrown instanceof NullPointerException && String.valueOf(thrown.getMessage())
                     .contains("\"emulator.SuperCC.getMainWindow()\" is null")) got = End.NO_MAIN_WINDOW;
        else {
            Harness.check(what + " builds its window (threw " + thrown + ")", false);
            return;
        }
        Harness.eq(what + " gets as far as expected", got, expected);
        Harness.check(what + " built its components (" + SpyPanelUI.made.get() + " panels)", SpyPanelUI.made.get() > 0);
    }

    private static void body() throws Exception {
        Harness.section("1. every form class in the jar was form-compiled");
        String jarPath = System.getProperty("supercc.jar");
        List<String> formClasses = new ArrayList<>();
        if (jarPath == null || !new File(jarPath).isFile()) {
            Harness.skip("no -Dsupercc.jar given: cannot list the jar's .form files");
        } else {
            int forms = 0;
            try (ZipFile z = new ZipFile(jarPath)) {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    String n = e.nextElement().getName();
                    if (!n.startsWith("java/") || !n.endsWith(".form")) continue;
                    forms++;
                    String fqcn = n.substring(5, n.length() - 5).replace('/', '.');
                    formClasses.add(fqcn);
                    Class<?> c = Class.forName(fqcn, false, FormClassTest.class.getClassLoader());
                    boolean setup = false, root = false;
                    for (Method m : c.getDeclaredMethods()) {
                        if (m.getName().equals("$$$setupUI$$$") && m.getParameterCount() == 0) setup = true;
                        if (m.getName().equals("$$$getRootComponent$$$") && m.getParameterCount() == 0) root = true;
                    }
                    Harness.check(fqcn + " has IntelliJ's $$$setupUI$$$ and $$$getRootComponent$$$", setup && root);
                }
            }
            Harness.eq("form files in the jar", forms, 15);
        }

        Harness.section("2. every form window's real constructor builds its components");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        // A time limit: ChangeTimer seeds a spinner with the timer, which must be at least 0.1 second.
        b.level().title("Forms").password("ABCD").chips(0).timeLimit(100)
         .tile(3, 5, Tile.CHIP_RIGHT).tile(4, 5, Tile.EXIT).end();
        File dat = b.writeTo(Harness.tempDir("scc-forms-"), "forms.dat").toFile();
        SuperCC emu = new SuperCC(false);
        emu.openLevelset(dat);
        emu.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Solution sol = new Solution("r".toCharArray(), 0, Step.EVEN, Solution.SUCC_MOVES, Ruleset.MS, Direction.UP);
        List<Solution> one = new ArrayList<>(List.of(sol));

        Class<?>[] e = { SuperCC.class };
        construct("tools.ChangeInventory",         End.RETURNS,        new Class<?>[0]);  // private; its main() uses it
        construct("tools.ChangeInventory",         End.WINDOW,         e, emu);
        construct("tools.ChangeTimer",             End.WINDOW,         e, emu);
        construct("tools.ChooseTileSize",          End.WINDOW,         new Class<?>[]{ SuperCC.class, int.class, int.class, int.class }, emu, 20, 1, 100);
        construct("tools.ChooseWindowSize",        End.NO_MAIN_WINDOW, e, emu);
        construct("tools.ControlGUI",              End.WINDOW,         e, emu);
        construct("tools.GameGifRecorder",         End.WINDOW,         e, emu);
        construct("tools.HelpWindow",              End.WINDOW,         e, emu);
        construct("tools.MonsterlistRearrangeGUI", End.WINDOW,         new Class<?>[]{ SuperCC.class, boolean.class }, emu, false);
        construct("tools.SeedSearch",              End.WINDOW,         new Class<?>[]{ SuperCC.class, Solution.class }, emu, sol);
        construct("tools.TSPGUI",                  End.WINDOW,         e, emu);
        construct("tools.TSPGUI",                  End.RETURNS,        new Class<?>[]{ SuperCC.class, boolean.class }, emu, false);
        construct("tools.TSPHelpWindow",           End.WINDOW,         e, emu);
        construct("tools.VariationResult",         End.WINDOW,         new Class<?>[]{ SuperCC.class, long.class, ArrayList.class, int.class }, emu, 1L, one, 0);
        construct("tools.VariationTesting",        End.WINDOW,         e, emu);
        construct("tools.VariationTesting",        End.RETURNS,        new Class<?>[]{ SuperCC.class, boolean.class }, emu, false);
        construct("tools.VerifyTWS",               End.WINDOW,         e, emu);
        Harness.skip("graphics.Gui: it IS the JFrame, so headless its super() throws before its setup can run "
                     + "-- launching the program is its check");

        Harness.section("3. no form class has a constructor this test does not run");
        /* formclass.ps1 inserts $$$setupUI$$$ into EVERY constructor that calls super(...), so each one
         * is a separate way to get it wrong -- and the review that found the delegating-constructor
         * case found it in a constructor no test ran. A new constructor fails here until it is
         * added to section 2. */
        if (formClasses.isEmpty()) {
            Harness.skip("no form classes listed (no -Dsupercc.jar)");
        }
        for (String fqcn : formClasses) {
            if (fqcn.equals("graphics.Gui")) continue;  // see the skip above
            for (Constructor<?> k : Class.forName(fqcn).getDeclaredConstructors()) {
                Harness.check(k + " is run by section 2", ran.contains(k));
            }
        }
    }
}
