package emulator;

import io.SuccPaths;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.HashMap;

public class EmulatorKeyListener extends KeyAdapter {
    
    private final SuperCC emulator;
    
    public enum Key {
        UP(SuperCC.UP),
        LEFT(SuperCC.LEFT),
        DOWN(SuperCC.DOWN),
        RIGHT(SuperCC.RIGHT),
        UP_LEFT(SuperCC.UP_LEFT),
        DOWN_LEFT(SuperCC.DOWN_LEFT),
        DOWN_RIGHT(SuperCC.DOWN_RIGHT),
        UP_RIGHT(SuperCC.UP_RIGHT),
        HALF_WAIT(SuperCC.WAIT),
        FULL_WAIT(SuperCC.WAIT),
        REWIND,
        FORWARD;
        private char directionChar;
        private int keyCode;

        public int getKeyCode() {
            return keyCode;
        }
        
        Key(char directionChar) {
            this.directionChar = directionChar;
        }
        Key() {}
    }
    private final HashMap<Integer, Key> keyMap = new HashMap<>();
    
    public void setKeyCode(Key key, int keyCode) {
        keyMap.remove(key.keyCode);
        key.keyCode = keyCode;
        keyMap.put(keyCode, key);
    }
    
    /* Upstream da35e33: auto-diagonals -- arrows pressed together, or one held while another is
     * pressed, make a diagonal move. MOD (Jeremy, upstream merge): rewritten after the architecture
     * review, keeping upstream's 50 ms window and its setting. What changed, and why:
     *   - upstream's java.util.Timer fired on its own thread and ticked the game from there, beside
     *     the event thread, through an unsynchronized HashSet; a javax.swing.Timer fires ON the
     *     event thread, like every other input. One timer, started only when a window opens.
     *   - only the four ARROWS wait. Upstream sent every action key through the window, so a wait,
     *     a rewind or a diagonal key pressed with an arrow down could be dropped for it.
     *   - a waiting arrow is FLUSHED -- its move made at once -- before ANY other key, any mouse
     *     press, and any loss of focus. Otherwise the 50 ms let other input jump the queue: arrow
     *     then Backspace rewound the move BEFORE it, arrow then Shift+1 saved the state a move
     *     early, a click on the game ticked first, and the move list recorded something other than
     *     what was typed. The flush comes first in keyPressed, and from an AWT event listener for
     *     key and mouse presses anywhere in the program (it runs before the component sees them,
     *     so a menu accelerator or a click on the time slider is behind the move, not ahead of it).
     *   - which move a burst makes is DiagonalCombiner's, after Tile World's rule: newest key per
     *     axis, and a tap inside the window counts.
     *   - in MS there is no waiting at all. MS has no diagonal moves -- SuperCC.tick reduces one to
     *     its vertical half -- so holding Up and pressing Right would have moved Up, where Tile
     *     World's MS mode moves Right. With no diagonal to make, arrows behave exactly as with the
     *     setting off. */
    private final DiagonalCombiner combiner = new DiagonalCombiner();
    private final javax.swing.Timer window = new javax.swing.Timer(1000 / 20, e -> flushPending());

    /** Whether arrows wait for a partner now: the setting is on, and the level open can move
     *  diagonally (Lynx). */
    private boolean autoDiagonalsActive() {
        SuccPaths paths = emulator.getPaths();
        return paths != null && paths.getAutoDiagonals() && emulator.isLevelLoaded()
               && emulator.getLevel().supportsDiagonal();
    }

    /** Makes the waiting arrow's move now, if one is waiting. Safe to call at any time. */
    void flushPending() {
        window.stop();
        Key move = combiner.resolve();
        if (move != null) processKeyPress(move, 0, false, false);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        Key k = keyMap.getOrDefault(e.getKeyCode(), null); //Checks if the pressed key is one of the 'action' keys
        if (k != null && DiagonalCombiner.isArrow(k) && autoDiagonalsActive()) {
            /* A press of an arrow that is already down is Windows' auto-repeat (about every 33 ms).
             * It makes a move of its own AT ONCE, from what is held: joining the open window, as it
             * did, halved the speed of a held arrow (Up held: four key events, two moves), and
             * marked the held key as pressed in that window, so letting go of it before the window
             * closed counted as a TAP and turned a run-then-turn into a diagonal (review,
             * reproduced in the real window). Holding Up and Right still repeats as the diagonal. */
            if (combiner.isHeld(k)) {
                flushPending();
                combiner.press(k);
                flushPending();
                return;
            }
            if (combiner.press(k)) window.start();
            return;
        }
        flushPending();
        processKeyPress(k, e.getKeyCode(), e.isControlDown(), e.isShiftDown());
    }

    /* keyPressed's flush and this release repeat what onAnyEvent does for the same event, on
     * purpose: a KeyEvent dispatched through AWT reaches both, but code that calls keyPressed or
     * keyReleased directly -- as GuiWindowTest's section 2 does for every key listener on the frame
     * -- reaches only these. Both operations are idempotent, so the second run of either is a
     * no-op. (One main window, so one listener, per JVM; the AWT listener lives as long as it.) */
    @Override
    public void keyReleased(KeyEvent e) {
        Key k = keyMap.getOrDefault(e.getKeyCode(), null);
        if (k != null && DiagonalCombiner.isArrow(k)) combiner.release(k);
    }

    /* The other input that must not jump a waiting arrow, and focus changes. Releases are tracked
     * here, for EVERY component of the program -- a release delivered to a button, a dialog or a tool
     * window never reaches keyReleased -- which is why only focus leaving the program can lose one,
     * and only that forgets what is held (see the WINDOW_LOST_FOCUS branch). All of it runs on the
     * event thread, like the timer, so none of it needs a lock. */
    private void onAnyEvent(AWTEvent ev) {
        int id = ev.getID();
        if (id == KeyEvent.KEY_PRESSED) {
            Key k = keyMap.getOrDefault(((KeyEvent) ev).getKeyCode(), null);
            if (k == null || !DiagonalCombiner.isArrow(k) || !autoDiagonalsActive()) flushPending();
        } else if (id == KeyEvent.KEY_RELEASED) {
            Key k = keyMap.getOrDefault(((KeyEvent) ev).getKeyCode(), null);
            if (k != null && DiagonalCombiner.isArrow(k)) combiner.release(k);
        } else if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_WHEEL) {
            // A drag of the time slider rewinds at every step, and a wheel turn over the move list
            // moves through it: an arrow pressed in the middle of either must land where it was
            // pressed, not after the next step (review).
            flushPending();
        } else if (id == FocusEvent.FOCUS_LOST) {
            flushPending();
        } else if (id == WindowEvent.WINDOW_LOST_FOCUS) {
            /* Only focus leaving the PROGRAM forgets what is held: a release then goes to another
             * application, where this listener cannot see it. Focus moving inside the program -- to
             * a button, a dialog, a tool window -- loses no release, because this listener sees key
             * releases anywhere in it, so held arrows stay held (review: forgetting them there made
             * a held Up plus Right come out as plain Right). Either way the waiting arrow moves now. */
            if (((WindowEvent) ev).getOppositeWindow() == null) inputInterrupted();
            else flushPending();
        }
    }

    /** Focus left the PROGRAM: a waiting arrow makes its move now, and nothing is trusted to be still
     *  held, because the releases go to another application, where this listener cannot see them.
     *  (Focus moving inside the program only flushes.) Called for a WINDOW_LOST_FOCUS whose opposite
     *  window is null. Public so a test can drive it directly: a synthetic window-focus event cannot
     *  reliably claim that focus went to another application, and Swing's focus manager swallows
     *  synthetic focus events whenever no component owns focus. */
    public void inputInterrupted() {
        flushPending();
        combiner.clear();
    }

    void processKeyPress(Key k, int keyCode, boolean controlDown, boolean shiftDown) {
        SavestateManager savestates = emulator.getSavestates();
        if (savestates == null)
            return;
        if (k == null) {
            if (keyCode != KeyEvent.VK_SHIFT && shiftDown) {
                if (controlDown && KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
                    try {
                        boolean recording = savestates.macroRecorder(keyCode - KeyEvent.VK_0);
                        if (recording)
                            emulator.showAction("Started macro record");
                        else
                            emulator.showAction("Finished macro record");
                    }
                    catch (IllegalArgumentException e1) {
                        emulator.throwError("The macro start position was after the end.");
                    }
                }
                else {
                    if (!controlDown) { //Just so you can't accidentally save a state into these
                        savestates.addSavestate(keyCode);
                        emulator.showAction("State " + KeyEvent.getKeyText(keyCode) + " saved");
                    }
                }
            }
            else {
                if (controlDown && KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
                    savestates.playMacro(keyCode-KeyEvent.VK_0);
                    emulator.getMainWindow().repaint(false);
                    emulator.showAction("Macro " + KeyEvent.getKeyText(keyCode) + " loaded");
                }
                else if (savestates.load(keyCode, emulator.getLevel())) {
                    emulator.showAction("State " + KeyEvent.getKeyText(keyCode) + " loaded");
                    emulator.getMainWindow().repaint(false);
                }
            }
        }
        else {
            switch (k) {
                case UP:
                case LEFT:
                case DOWN:
                case RIGHT:
                case UP_LEFT:
                case DOWN_LEFT:
                case DOWN_RIGHT:
                case UP_RIGHT:
                case HALF_WAIT:
                    if (!emulator.getLevel().getChip().isDead() && !SuperCC.areToolsRunning())
                        emulator.tick(k.directionChar, TickFlags.GAME_PLAY);
                    break;
                case FULL_WAIT:
                    for (int i=0; i < emulator.getLevel().ticksPerMove(); i++) {
                        if (!emulator.getLevel().getChip().isDead() && !SuperCC.areToolsRunning())
                            emulator.tick(k.directionChar, TickFlags.GAME_PLAY);
                    }
                    break;
                case REWIND:
                    savestates.rewind();
                    emulator.getLevel().load(emulator.getSavestates().getSavestate());
                    emulator.showAction("Rewind");
                    emulator.getMainWindow().repaint(false);
                    break;
                case FORWARD:
                    savestates.replay();
                    emulator.getLevel().load(emulator.getSavestates().getSavestate());
                    emulator.showAction("Replay");
                    emulator.getMainWindow().repaint(false);
                    break;
            }
        }
    }
    
    public EmulatorKeyListener(SuperCC emulator) {
        this.emulator = emulator;
        SuccPaths.Controls controls = emulator.getPaths().getControls();
        Key[] allKeys = Key.values();
        for (int i = 0; i < allKeys.length; i++) {
            allKeys[i].keyCode = controls.keys[i];
            keyMap.put(controls.keys[i], allKeys[i]);
        }
        window.setRepeats(false);
        Toolkit.getDefaultToolkit().addAWTEventListener(this::onAnyEvent,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK);
    }
    
}
