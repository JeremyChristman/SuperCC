import emulator.Solution;
import emulator.SuperCC;
import game.Direction;
import game.Ruleset;
import game.Step;
import game.Tile;
import tools.SeedSearch;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tools > Seed Search, and the form-compiled classes it is one of.
 *
 * WHY THIS FILE EXISTS. Upstream's 40495dd fixed a search that never ended: the last thread's range
 * ran to Integer.MAX_VALUE in an int, so the seed after it wrapped to Integer.MIN_VALUE, the loop's
 * `currentSeed <= endSeed` stayed true, and the search went on through every negative seed (which the
 * RNG masks back onto the positive ones) for as long as the window stayed open. Measured on jc-15:
 * asked for the last 3,000 seeds, it had made 12.3 million attempts eight seconds later. The fix is
 * longs throughout. jc-16 to jc-19 added one more (long), for a negative start; the upstream baseline
 * reverted it (section 3, BASELINE-ISSUES.md UB-7).
 *
 * SeedSearch is FORM-BASED, so its .class is committed bytecode rebuilt by formclass.ps1, not by the
 * splice build (docs\adr\0010). Section 1 is the guard on that: a class rebuilt without IntelliJ's
 * generated $$$setupUI$$$, or without the constructor's call to it, has no components and dies on
 * the first line of its constructor.
 *
 * HOW A HEADLESS TEST DRIVES A WINDOW. The suite runs with java.awt.headless=true, and SeedSearch's
 * constructor ends by opening a JFrame, which throws HeadlessException there. Everything before that
 * line -- components, listeners, the starting savestate -- has already happened, but the instance is
 * lost with the exception. So a spy ButtonUI (installed through UIManager, the standard Swing hook
 * for "a component of this kind was created") records the one JButton the form makes; its listener
 * is the constructor's own lambda, and that lambda captured the instance. Pressing the button is
 * then calling that listener: the real constructor's real code, minus only the window.
 *
 * Fixture: Chip at 3,5 facing right, the exit at 4,5, solution "r". It wins on every seed, so a
 * correct search of N seeds reports exactly N attempts and N successes.
 *
 * Run with -Djava.awt.headless=true and -Dsupercc.jar (run-tests.ps1 passes both).
 */
public class SeedSearchTest {

    public static void main(String[] args) {
        System.exit(Harness.run("SeedSearchTest", SeedSearchTest::body));
    }

    /** Records every JButton built while installed. Must be public with a public static createUI. */
    public static final class SpyButtonUI extends BasicButtonUI {
        static final List<JComponent> made = Collections.synchronizedList(new ArrayList<>());
        public static ComponentUI createUI(JComponent c) { made.add(c); return BasicButtonUI.createUI(c); }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = SeedSearch.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }

    private static int numAlive() throws Exception {
        return ((AtomicInteger) field(null, "numAlive")).get();
    }

    /**
     * Runs the real constructor and returns the instance it built before the window threw.
     * Returns null, with a failed assertion, if it threw anything else.
     */
    private static SeedSearch open(SuperCC emulator, Solution sol) throws Exception {
        SpyButtonUI.made.clear();
        Object before = UIManager.get("ButtonUI");
        UIManager.put("ButtonUI", SpyButtonUI.class.getName());
        Throwable thrown = null;
        try {
            new SeedSearch(emulator, sol);
        } catch (Throwable t) {
            thrown = t;
        } finally {
            UIManager.put("ButtonUI", before);
        }
        if (!(thrown instanceof HeadlessException)) {
            Harness.check("the constructor got as far as the window (threw " + thrown + ")", false);
            return null;
        }
        for (JComponent c : SpyButtonUI.made) {
            if (!(c instanceof JButton)) continue;
            for (ActionListener l : ((JButton) c).getActionListeners()) {
                for (Field f : l.getClass().getDeclaredFields()) {
                    if (f.getType() != SeedSearch.class) continue;
                    f.setAccessible(true);
                    return (SeedSearch) f.get(l);
                }
            }
        }
        Harness.check("found the instance through the start button's listener", false);
        return null;
    }

    /** Presses Begin/Pause/Resume: calls the constructor's listener, as the button would. */
    private static void press(SeedSearch ss) throws Exception {
        JButton b = (JButton) field(ss, "startStopButton");
        for (ActionListener l : b.getActionListeners()) l.actionPerformed(new ActionEvent(b, ActionEvent.ACTION_PERFORMED, ""));
    }

    /**
     * Sets the start and thread count and presses Begin. Returns the thread count the slider took:
     * the constructor caps it at availableProcessors(), so a 2-CPU machine runs 2 where 4 was asked.
     */
    private static int start(SeedSearch ss, long startSeed, int threads) throws Exception {
        ((JTextField) field(ss, "startField")).setText(Long.toString(startSeed));
        JSlider slider = (JSlider) field(ss, "threadSlider");
        slider.setValue(threads);
        press(ss);
        return slider.getValue();
    }

    /** Counts how often isRunning() goes from true to false while it watches. */
    private static final class RunningWatcher extends Thread {
        final AtomicInteger falls = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        RunningWatcher() { setDaemon(true); start(); }
        @Override public void run() {
            boolean prev = SeedSearch.isRunning();
            while (!stop.get()) {
                boolean now = SeedSearch.isRunning();
                if (prev && !now) falls.incrementAndGet();
                prev = now;
            }
        }
        int finish() throws InterruptedException { stop.set(true); join(); return falls.get(); }
    }

    /**
     * Waits until every search thread has ended. numAlive is the real condition; `running` alone is
     * not -- a thread with an empty range counts down and clears it while later threads still run.
     * On timeout it stops the threads anyway, so one failure does not poison the next scenario.
     */
    private static boolean awaitIdle(long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (numAlive() == 0 && !SeedSearch.isRunning()) return true;
            Thread.sleep(20);
        }
        Field kill = SeedSearch.class.getDeclaredField("killFlag");
        kill.setAccessible(true);
        for (int i = 0; i < 250 && numAlive() > 0; i++) { kill.set(null, true); Thread.sleep(20); }
        if (numAlive() == 0) kill.set(null, false);  // as stopAll: do not leave the static flag set
        return false;
    }

    /**
     * Stops every search thread through killFlag, setting it again until all have ended: on upstream's
     * code a late "last one cleans up" can clear it (UB-8). True if they all ended in time.
     */
    private static boolean stopAll(long millis) throws Exception {
        Field kill = SeedSearch.class.getDeclaredField("killFlag");
        kill.setAccessible(true);
        long end = System.currentTimeMillis() + millis;
        while (numAlive() > 0 && System.currentTimeMillis() < end) {
            kill.set(null, true);
            Thread.sleep(20);
        }
        // As awaitIdle: the last thread's cleanup publishes running=false just AFTER numAlive hits 0.
        while (numAlive() == 0 && SeedSearch.isRunning() && System.currentTimeMillis() < end) Thread.sleep(20);
        boolean stopped = numAlive() == 0 && !SeedSearch.isRunning();
        // Set after the last thread's own cleanup may have cleared it: leave the static flag clean.
        if (stopped) kill.set(null, false);
        return stopped;
    }

    private static long count(SeedSearch ss, String name) throws Exception {
        return ((Number) field(ss, name)).longValue();
    }

    private static String label(SeedSearch ss) throws Exception {
        return ((JLabel) field(ss, "resultsLabel")).getText();
    }

    private static void body() throws Exception {
        File dat;
        {
            DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
            b.level().title("Seed").password("ABCD").chips(0)
             .tile(3, 5, Tile.CHIP_RIGHT).tile(4, 5, Tile.EXIT).end();
            dat = b.writeTo(Harness.tempDir("scc-seed-"), "seed.dat").toFile();
        }
        SuperCC emulator = new SuperCC(false);
        emulator.openLevelset(dat);
        emulator.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Solution win = new Solution("r".toCharArray(), 0, Step.EVEN, Solution.SUCC_MOVES, Ruleset.MS, Direction.UP);

        Harness.section("1. the rebuilt window builds itself");
        /* A rebuilt class can have $$$setupUI$$$ and still never call it: then threadSlider is null
         * and the constructor's first line throws NullPointerException. Reaching the window -- the
         * HeadlessException -- means every component the constructor touches was built. (All fifteen
         * form classes, constructed the same way: FormClassTest.) */
        SeedSearch probe = open(emulator, win);
        Harness.check("SeedSearch's constructor builds its components and reaches the window", probe != null);
        if (probe != null) {
            Harness.eq("its results label was set by the constructor", label(probe), "Successes: 0/0 (0%)");
            Harness.eq("and its start field comes from the .form", ((JTextField) field(probe, "startField")).getText(), "0");
        }

        Harness.section("2. the search stops at the top seed");
        /* jc-15 never got here: the last thread wrapped past Integer.MAX_VALUE and kept going. */
        SeedSearch a = open(emulator, win);
        if (a != null) {
            RunningWatcher watch = new RunningWatcher();
            start(a, (long) Integer.MAX_VALUE - 2999, 4);
            Harness.check("the last 3,000 seeds: every thread ends", awaitIdle(20_000));
            /* jc-16 to jc-19 carried a thread-start fix (UB-8). Upstream starts each thread as soon
             * as it is built, and a 750-seed range finishes faster than the next thread is built, so
             * an early thread runs the "last one cleans up" block mid-search: measured on the laptop,
             * `running` went false four times in three of five such searches. It is a race; the
             * fixed code can only ever go false once. */
            /* ⚠ UPSTREAM BASELINE (from jc-20): the fork's fix was REVERTED (FORK.md, "The upstream
             * baseline"; BASELINE-ISSUES.md UB-8). On upstream's code this is a RACE -- it passed five
             * runs of five on a 28-CPU desktop and fails on slower machines -- so it can be neither a
             * check nor a pin; the 8-seed search below pins the same bug deterministically. */
            int falls = watch.finish();
            Harness.skip("`running` went false once, at the end -- not as each early thread finished: "
                         + "a race on upstream's code (UB-8), not asserted; this run saw " + falls);
            Harness.eq("exactly 3,000 attempts", count(a, "globalAttempts"), 3000L);
            Harness.eq("exactly 3,000 successes (the fixture wins on every seed)", count(a, "globalSuccesses"), 3000L);
            Harness.eq("and the window says so", label(a), "Successes: 3000/3000 (100%)");
            Thread.sleep(300);
            Harness.eq("still 3,000 a moment later (nothing kept searching)", count(a, "globalAttempts"), 3000L);
        }
        /* The flicker made nearly certain rather than likely: two seeds a thread is microseconds of
         * work, and building the next thread (an emulator, the level set, the level) is
         * milliseconds, so with upstream's start-as-you-build every thread finishes before the next
         * begins and `running` goes false once per thread. The 3,000-seed search above runs first,
         * on a cold JIT, and its threads usually overlap -- that check alone let the regression
         * through three times out of three.
         *
         * ⚠ UPSTREAM BASELINE (from jc-20): pinned (UB-8). One search is not enough to pin it: the
         * watcher is itself a thread, and on 2 CPUs it missed the flicker in 1 search of 4
         * (measured), which would read as "the correct answer came back". So the search runs FIVE
         * times and the pin is whether `running` ever went false more than once. With the fix that
         * can never happen in any search, so that side stays deterministic; on upstream's code all
         * five would have to be missed. One thread cannot show it at all. */
        int maxFalls = 0, n = 0;
        boolean allOk = true;
        for (int round = 0; round < 5; round++) {
            SeedSearch tiny = open(emulator, win);
            if (tiny == null) { allOk = false; break; }
            RunningWatcher watch = new RunningWatcher();
            n = start(tiny, (long) Integer.MAX_VALUE - 7, 4);
            allOk &= awaitIdle(20_000);
            maxFalls = Math.max(maxFalls, watch.finish());
            allOk &= count(tiny, "globalAttempts") == 8L;
        }
        Harness.check("the last 8 seeds, 2 a thread, five times: every thread ends, exactly 8 attempts each", allOk);
        if (n < 2) Harness.skip("one CPU: the slider allows one thread, so there is no early thread to see");
        else Harness.upstreamBug("`running` went false once per search (" + n + " threads; upstream's order flips it once per thread)",
                                 maxFalls <= 1);
        SeedSearch one = open(emulator, win);
        if (one != null) {
            /* Integer.MAX_VALUE itself, with four threads: three get an empty range, one gets one seed. */
            start(one, Integer.MAX_VALUE, 4);
            Harness.check("start at the top seed: every thread ends", awaitIdle(20_000));
            Harness.eq("exactly one attempt", count(one, "globalAttempts"), 1L);
            Harness.eq("the window says 1/1", label(one), "Successes: 1/1 (100%)");
        }

        Harness.section("3. a negative start shares the work");
        /* jc-16 to jc-19 carried a (long) cast (UB-7): upstream leaves Integer.MAX_VALUE - start in
         * int, so -5 wraps to a negative pool, every thread but the last gets an empty range, and the
         * last searches billions alone (3.76 billion at four threads).
         *
         * ⚠ UPSTREAM BASELINE (from jc-20): that fix was REVERTED (FORK.md, "The upstream baseline";
         * BASELINE-ISSUES.md UB-7), so each thread's outcome is pinned at upstream's: every thread
         * but the last searched nothing, and the last searched from upstream's (very negative) start
         * of its range. Upstream's split is computed below with upstream's own int arithmetic. To
         * re-apply all four at once (the fix layer, FORK.md, takes them back one at a time instead):
         * `git revert` the commit "Upstream baseline: revert the Seed Search fixes". */
        SeedSearch neg = open(emulator, win);
        if (neg != null) {
            long from = -5;
            int threads = start(neg, from, 4);
            long pool = ((long) Integer.MAX_VALUE - from + 1) / threads;
            if (threads < 2) {
                Harness.skip("one CPU: the slider allows one thread, so there is no split to see");
                Harness.check("one thread: stopped", stopAll(20_000));
            } else {
                Thread.sleep(400);
                /* Not `running` and not the Pause button: on upstream's code both are unreliable (UB-8).
                 * An empty-range thread's late cleanup can write running=false after the searching thread
                 * set it true, and the button would then START a second batch instead of pausing. This
                 * section is about the split, so it watches the threads themselves and stops them through
                 * killFlag directly (section 2 covers `running`). */
                Harness.check("threads are still searching after 400 ms", numAlive() > 0);
                Harness.check("stopped: every thread ends", stopAll(20_000));
                long[] at = (long[]) field(neg, "threadCurrentSeed");
                // Upstream's split: Integer.MAX_VALUE - start in INT arithmetic, as SeedSearch writes it.
                long upPool = ((long) (Integer.MAX_VALUE - (int) from) + 1) / threads;
                long sum = 0, upSum = 0;
                for (int i = 0; i < threads; i++) {
                    long own = from + pool * i, upOwn = from + upPool * i;
                    String outcome = at[i] > own && at[i] < own + pool ? "searched part of its own range"
                                   : at[i] == upOwn ? "searched nothing"
                                   : at[i] > upOwn ? "searched from " + upOwn
                                   : "stopped at " + at[i];
                    String upstream = i < threads - 1 ? "searched nothing" : "searched from " + upOwn;
                    Harness.upstreamBug("thread " + i + " started at " + own + " and searched part of its own range",
                                        outcome, upstream, "searched part of its own range");
                    sum += at[i] - own;
                    upSum += at[i] - upOwn;
                }
                long attempts = count(neg, "globalAttempts");
                Harness.upstreamBug("the attempts are exactly what the threads covered",
                                    attempts == sum ? "the correct split" : attempts == upSum ? "upstream's split" : "neither: " + attempts,
                                    "upstream's split", "the correct split");
            }
        }

        Harness.section("4. the counters count past 2^31");
        /* A full search is 2^31 seeds, which an int attempt counter cannot hold -- it overflowed into
         * a negative total and percentage at the very end of every complete run. */
        SeedSearch big = open(emulator, win);
        if (big != null) {
            ((java.util.concurrent.atomic.AtomicLong) field(big, "globalAttempts")).set(Integer.MAX_VALUE);
            ((java.util.concurrent.atomic.AtomicLong) field(big, "globalSuccesses")).set(Integer.MAX_VALUE);
            Method upd = SeedSearch.class.getDeclaredMethod("updateValues", int.class, int.class, long.class, long.class);
            upd.setAccessible(true);
            JLabel example = (JLabel) field(big, "exampleSeedLabel");
            String untouched = example.getText();  // the .form's own text
            try {
                // Long.MIN_VALUE = no example to report, under upstream's `>= 0` test and jc-16's sentinel
                // alike; currentSeed 0 is a multiple of the GUI rate: repaints
                upd.invoke(big, 1, 1, Long.MIN_VALUE, 0L);
                Harness.eq("one more attempt past Integer.MAX_VALUE", label(big), "Successes: 2147483648/2147483648 (100%)");
                Harness.eq("and no example seed is shown before one is reported", example.getText(), untouched);
                /* jc-16 to jc-19 carried a sentinel fix (UB-9): upstream's "no success yet" is -1,
                 * tested as >= 0, so a success at a negative seed -- reachable from a negative
                 * start -- is counted but never shown. */
                upd.invoke(big, 0, 0, -3L, 0L);
            } catch (InvocationTargetException e) {
                throw new Exception(e.getCause());
            }
            /* ⚠ UPSTREAM BASELINE (from jc-20): the sentinel fix was REVERTED (UB-9); upstream tests
             * `>= 0`, so the label keeps its untouched text. */
            Harness.upstreamBug("a success at a negative seed is shown", example.getText(), untouched, "Example seed: -3");
        }

        Harness.section("5. Pause stops a search");
        /* The button's Pause, which section 3 no longer presses (UB-8 makes `running` unreliable
         * there). Here it is race-free on upstream's code too: from seed 0 every thread's range is
         * hundreds of millions of seeds, so no thread can finish and run an early "last one cleans
         * up" in the first moments, and by the time Pause is pressed every thread has long begun --
         * a thread that had not would clear the flag as it started (UB-8). */
        SeedSearch paused = open(emulator, win);
        if (paused != null) {
            start(paused, 0, 4);
            Thread.sleep(500);
            Harness.check("a long search is running", SeedSearch.isRunning() && numAlive() > 0);
            press(paused);  // Pause
            Harness.check("Pause: every thread ends", awaitIdle(20_000));
            Harness.eq("and the button offers Resume", ((JButton) field(paused, "startStopButton")).getText(), "Resume");
        }
    }
}
