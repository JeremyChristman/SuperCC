import emulator.DiagonalCombiner;
import emulator.EmulatorKeyListener.Key;

/**
 * Auto-diagonals (upstream 5f928e0 / da35e33, rewritten for jc-18): which move a burst of arrow keys
 * makes. DiagonalCombiner is pure logic, so every rule is checked here without a window, a timer or
 * a clock. EmulatorKeyListener's wiring -- the 50 ms window, the flush before any other input, MS
 * making no window at all -- is checked by GuiWindowTest against the real main window.
 *
 * Expected values come from Tile World's rule for the same problem (tworld generic/dirinput.c,
 * resolvedirections(), jc-43): each axis is claimed by its NEWEST HELD key; an axis nothing holds is
 * filled by a key struck (pressed and released) inside the cycle; a struck key never displaces a held
 * one on its own axis. Where this differs from Tile World -- two keys both tapped inside one window
 * make a diagonal here -- the case says so.
 *
 * Several cases are the architecture review's counterexamples to upstream's version, which decided
 * from the keys STILL HELD when its timer fired: a tap inside the window made no move, and holding
 * Up while tapping Right made plain Up.
 */
public class AutoDiagonalTest {

    public static void main(String[] args) {
        System.exit(Harness.run("AutoDiagonalTest", AutoDiagonalTest::body));
    }

    private static void body() throws Exception {
        Harness.section("1. the four arrows are the only keys that wait");
        for (Key k : Key.values()) {
            boolean arrow = k == Key.UP || k == Key.DOWN || k == Key.LEFT || k == Key.RIGHT;
            Harness.eq("isArrow(" + k + ")", DiagonalCombiner.isArrow(k), arrow);
        }

        Harness.section("2. one arrow");
        DiagonalCombiner c = new DiagonalCombiner();
        Harness.check("nothing is waiting at first", !c.isOpen());
        Harness.eq("no window, no move", c.resolve(), null);
        Harness.check("the first press OPENS a window", c.press(Key.RIGHT));
        Harness.check("and it is open", c.isOpen());
        Harness.eq("a held arrow alone makes that move", c.resolve(), Key.RIGHT);
        Harness.check("resolving closes the window", !c.isOpen());

        c = new DiagonalCombiner();
        c.press(Key.RIGHT);
        c.release(Key.RIGHT);
        Harness.eq("a TAP inside the window still moves (upstream made no move at all)", c.resolve(), Key.RIGHT);

        Harness.section("3. two arrows make a diagonal, held or tapped");
        c = new DiagonalCombiner();
        c.press(Key.UP);
        Harness.check("a second press inside the window does not open another (the timer must not restart)", !c.press(Key.RIGHT));
        Harness.eq("Up and Right held together", c.resolve(), Key.UP_RIGHT);

        Key[][] pairs = {{Key.UP, Key.LEFT, Key.UP_LEFT}, {Key.UP, Key.RIGHT, Key.UP_RIGHT},
                         {Key.DOWN, Key.LEFT, Key.DOWN_LEFT}, {Key.DOWN, Key.RIGHT, Key.DOWN_RIGHT}};
        for (Key[] p : pairs) {
            DiagonalCombiner a = new DiagonalCombiner();
            a.press(p[1]); a.press(p[0]);
            Harness.eq(p[0] + " + " + p[1] + ", in either order", a.resolve(), p[2]);
        }

        c = new DiagonalCombiner();
        c.press(Key.UP);
        c.press(Key.RIGHT);
        c.release(Key.RIGHT);
        Harness.eq("Up held, Right TAPPED inside the window: the diagonal (upstream made plain Up)", c.resolve(), Key.UP_RIGHT);

        c = new DiagonalCombiner();
        c.press(Key.UP); c.release(Key.UP);
        c.press(Key.LEFT); c.release(Key.LEFT);
        Harness.eq("both TAPPED inside one window: a diagonal (Tile World's per-tick poll would keep one)", c.resolve(), Key.UP_LEFT);

        Harness.section("4. a held key outlasts its window and combines with the next");
        c = new DiagonalCombiner();
        c.press(Key.UP);
        Harness.eq("Up held: the first window moves Up", c.resolve(), Key.UP);
        Harness.check("Right pressed while Up is still down opens a new window", c.press(Key.RIGHT));
        Harness.eq("and makes the diagonal, as holding one arrow and pressing another does in Tile World", c.resolve(), Key.UP_RIGHT);
        c.release(Key.RIGHT);
        Harness.check("an auto-repeat of the held Up opens another window", c.press(Key.UP));
        Harness.eq("Right came up in an EARLIER window, so it is gone, not struck: plain Up", c.resolve(), Key.UP);

        Harness.section("5. one key per axis: the newest held, and held beats struck");
        c = new DiagonalCombiner();
        c.press(Key.UP); c.press(Key.DOWN);
        Harness.eq("Up then Down, both held: the newest, Down", c.resolve(), Key.DOWN);
        c = new DiagonalCombiner();
        c.press(Key.DOWN); c.press(Key.UP);
        Harness.eq("Down then Up, both held: Up", c.resolve(), Key.UP);

        c = new DiagonalCombiner();
        c.press(Key.UP);
        c.press(Key.DOWN); c.release(Key.DOWN);
        Harness.eq("Up held, Down tapped: Up -- a tap never displaces a held key on its own axis (no flicker back)", c.resolve(), Key.UP);

        c = new DiagonalCombiner();
        c.press(Key.UP); c.release(Key.UP);
        c.press(Key.DOWN); c.release(Key.DOWN);
        Harness.eq("Up tapped then Down tapped, nothing held: the newer tap, Down", c.resolve(), Key.DOWN);

        // Upstream went by table order with three keys down: Up+Down+Right was always Down-Right,
        // whatever came last.
        c = new DiagonalCombiner();
        c.press(Key.DOWN); c.press(Key.RIGHT); c.press(Key.UP);
        Harness.eq("Down, Right, then Up, all held: the newest vertical with Right -- Up-Right (upstream: Down-Right)", c.resolve(), Key.UP_RIGHT);
        c = new DiagonalCombiner();
        c.press(Key.UP); c.press(Key.LEFT); c.press(Key.RIGHT);
        Harness.eq("Up, Left, then Right: Up-Right", c.resolve(), Key.UP_RIGHT);

        c = new DiagonalCombiner();
        c.press(Key.UP);
        c.release(Key.UP);
        c.press(Key.UP);
        Harness.eq("tapped and pressed again inside the window: held again, plain Up", c.resolve(), Key.UP);

        Harness.section("6. clearing, for a lost focus that can swallow key releases");
        c = new DiagonalCombiner();
        c.press(Key.UP);
        c.clear();
        Harness.check("clear closes the window", !c.isOpen());
        Harness.eq("and leaves no move", c.resolve(), null);
        Harness.check("Right pressed after a clear opens a fresh window", c.press(Key.RIGHT));
        Harness.eq("and Up is no longer held: a lost release cannot make a stale diagonal", c.resolve(), Key.RIGHT);

        c = new DiagonalCombiner();
        c.release(Key.LEFT);
        Harness.check("a release with nothing pressed changes nothing", !c.isOpen() && c.resolve() == null);
    }
}
