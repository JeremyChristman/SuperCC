package emulator;

import emulator.EmulatorKeyListener.Key;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * MOD (Jeremy, upstream merge): which move a burst of arrow keys makes when auto-diagonals is on
 * (Tools > Controls > Auto diagonals, upstream 5f928e0 / da35e33). Pure logic -- no Swing, no
 * clock -- so every rule below is unit-tested (test/AutoDiagonalTest.java); EmulatorKeyListener owns
 * the 50 ms window and decides when it closes.
 *
 * Upstream decided from the arrows STILL HELD when its timer fired, picked from a HashSet when no
 * pair formed, and did it all off the event thread. Three consequences the architecture review
 * measured against Tile World's own rule (tworld generic/dirinput.c, resolvedirections, jc-43):
 *   - a quick tap, released inside the window, made NO move at all;
 *   - holding Up and tapping Right inside the window made plain Up;
 *   - with three keys down the choice depended on table order, not on what was pressed last.
 *
 * The rule here is Tile World's, per AXIS:
 *   1. each axis (up/down, left/right) is claimed by its most recently pressed key that is still
 *      HELD;
 *   2. an axis nothing holds is filled by the most recent key STRUCK on it -- pressed and released
 *      inside this window -- so a tap counts, and a tap completes a diagonal;
 *   3. both axes claimed make the diagonal, one makes that direction, none makes no move.
 * A struck key never displaces a held key on its own axis, as in Tile World: holding Up and
 * tapping Down stays Up rather than flickering backward. One deliberate difference: two keys BOTH
 * tapped inside one window make a diagonal here, where Tile World's per-tick poll keeps one. SuperCC
 * makes one move per window rather than one per tick, and two arrows struck together is exactly
 * the gesture this setting exists for.
 */
public final class DiagonalCombiner {

    private final Map<Key, Long> held = new EnumMap<>(Key.class);     // key -> when it was pressed
    private final Map<Key, Long> struck = new EnumMap<>(Key.class);   // pressed AND released this window
    private final Set<Key> pressedThisWindow = EnumSet.noneOf(Key.class);
    private long clock;
    private boolean open;

    public static boolean isArrow(Key k) {
        return k == Key.UP || k == Key.DOWN || k == Key.LEFT || k == Key.RIGHT;
    }

    /** An arrow went down (an auto-repeat counts as a new press: it becomes the newest). Returns
     *  true when this press OPENS a window, which is when the caller starts its timer -- and only
     *  then: auto-repeat arrives faster than the window, and restarting the timer on every press
     *  would hold a move off for as long as the key is down. */
    public boolean press(Key k) {
        held.put(k, ++clock);
        struck.remove(k);
        pressedThisWindow.add(k);
        if (open) return false;
        open = true;
        return true;
    }

    /** An arrow came up. Inside the window it stays in play as STRUCK if it was pressed in this
     *  window; a key from an earlier window that is released now simply stops being held. */
    public void release(Key k) {
        Long when = held.remove(k);
        if (open && when != null && pressedThisWindow.contains(k)) struck.put(k, when);
    }

    public boolean isOpen() {
        return open;
    }

    /** Closes the window and returns the move it makes, or null for none (also when no window was
     *  open). Keys still held stay held, ready to combine with the next window's presses. */
    public Key resolve() {
        if (!open) return null;
        open = false;
        Key vertical = claim(Key.UP, Key.DOWN);
        Key horizontal = claim(Key.LEFT, Key.RIGHT);
        struck.clear();
        pressedThisWindow.clear();
        if (vertical == null) return horizontal;
        if (horizontal == null) return vertical;
        if (vertical == Key.UP) return horizontal == Key.LEFT ? Key.UP_LEFT : Key.UP_RIGHT;
        return horizontal == Key.LEFT ? Key.DOWN_LEFT : Key.DOWN_RIGHT;
    }

    /** Forgets everything: a focus change can swallow key releases, so nothing held can be trusted. */
    public void clear() {
        held.clear();
        struck.clear();
        pressedThisWindow.clear();
        open = false;
    }

    private Key claim(Key a, Key b) {
        Key byHeld = newest(held, a, b);
        return byHeld != null ? byHeld : newest(struck, a, b);
    }

    private static Key newest(Map<Key, Long> keys, Key a, Key b) {
        Long ta = keys.get(a), tb = keys.get(b);
        if (ta == null) return tb == null ? null : b;
        if (tb == null) return a;
        return ta > tb ? a : b;
    }
}
