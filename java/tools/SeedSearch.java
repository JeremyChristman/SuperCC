package tools;

import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Position;
import game.Ruleset;

import javax.swing.*;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SeedSearch {

    private JPanel panel1;
    private JButton startStopButton;
    private JLabel resultsLabel;
    private JLabel exampleSeedLabel;
    private JLabel startLabel;
    private JTextField startField;
    private JRadioButton untilExitRadioButton;
    private JRadioButton untilPositionRadioButton;
    private JLabel searchTypeLabel;
    private JTextField positionField;
    private JSlider threadSlider;
    private JLabel threadLabel;
    private JLabel rulesetWarningLabel;

    private static final int UPDATE_VALUE_RATE = 1000;
    private static final int UPDATE_GUI_RATE = UPDATE_VALUE_RATE * 2;

    private final byte[] startingState;
    /* MOD (Jeremy, fix layer, UB-10): volatile. Both flags are shared between the event thread and
     * the search threads: Pause (or closing the window) sets killFlag, which every search thread's
     * loop reads; the last thread to finish clears running, which Pause and isRunning() read.
     * Without volatile the Java memory model does not promise either side ever sees the other's
     * write. (Since UB-8's fix, run() no longer writes either flag as it starts; the listener sets
     * both once, before any thread runs.) SeedSearchTest §6 checks the modifier on the shipped
     * class. jc-16's, re-applied alone. */
    private static volatile boolean killFlag = false;
    private static volatile boolean running = false;
    private static AtomicInteger numAlive = new AtomicInteger(0);
    private DecimalFormat df;

    private long[] threadCurrentSeed;
    private AtomicLong globalAttempts = new AtomicLong(0);
    private AtomicLong globalSuccesses = new AtomicLong(0);
    /* MOD (Jeremy, fix layer, UB-9): "no success yet" was -1, tested as `>= 0`, which hid every
     * success at a negative seed -- reachable from a negative start, which UB-7's fix splits
     * properly. No seed is a long this small (seeds are ints), and a negative one shown here can be
     * typed into Set RNG Seed as is. SeedSearchTest §4 checks it. jc-16's, re-applied alone. */
    private static final long NO_SUCCESS = Long.MIN_VALUE;
    private AtomicLong globalLastSuccess = new AtomicLong(NO_SUCCESS);
    private boolean ranAlready = false;
    private boolean untilPosition = false;
    private Position endPosition = new Position(0, 0);

    public SeedSearch(SuperCC emulator, Solution solution) {
        int maxNumThreads = Runtime.getRuntime().availableProcessors();
        threadSlider.setMaximum(maxNumThreads);
        threadSlider.setValue(maxNumThreads);

        if (solution.ruleset != emulator.getLevel().getRuleset()) {
            rulesetWarningLabel.setVisible(true);
            rulesetWarningLabel.setText("The provided solution does not match the current ruleset, the ruleset HAS been changed");
        }

        emulator.loadLevel(emulator.getLevel().getLevelNumber(), 0, solution.step, false,
                solution.ruleset, solution.initialSlide);
        startingState = emulator.getLevel().save();
    
        resultsLabel.setText("Successes: 0/0 (0%)");
        df = new DecimalFormat("##.####");

        startStopButton.addActionListener((e) -> {
            int numThreads = threadSlider.getValue();
            int start = Integer.parseInt(startField.getText());
            /* MOD (Jeremy, fix layer, UB-7): (long) added. Upstream's overflow fix (40495dd) left this
             * subtraction in int arithmetic, so a NEGATIVE start wrapped it: start -5 gave a negative
             * pool, every thread but the last got an empty range, and the last one searched billions
             * of seeds alone. (The RNG masks seeds to 31 bits, so a negative start only repeats
             * positive seeds; it is still a number the field accepts.) SeedSearchTest §3 checks each
             * thread's share. jc-16's, re-applied alone. */
            long numSeeds = (long) Integer.MAX_VALUE - start;
            long seedPoolSize = (numSeeds + 1) / numThreads; //avoids overflow before reducing back in range

            if (running) {
                startStopButton.setText("Resume");
                killFlag = true;
            }
            else {
                if (!ranAlready) {
                    threadCurrentSeed = new long[numThreads];
                    ranAlready = true;
                    for (int i=0; i < numThreads; i++) {
                        threadCurrentSeed[i] = (start + seedPoolSize * i);
                    }
                }
                if (untilPosition) {
                    String positionString = positionField.getText().replaceAll("\\s+",""); //Remove whitespace
                    String[] positionStrings = positionString.split(",");
                    int[] positions = {Integer.parseInt(positionStrings[0]), Integer.parseInt(positionStrings[1])};
                    positions[0] = Integer.max(positions[0], 0);
                    positions[0] = Integer.min(positions[0], 31);
                    positions[1] = Integer.max(positions[1], 0);
                    positions[1] = Integer.min(positions[1], 31);
                    endPosition = new Position(positions[0], positions[1]);
                }
                searchTypeLabel.setVisible(false);
                untilExitRadioButton.setVisible(false);
                untilPositionRadioButton.setVisible(false);
                positionField.setVisible(false);
                startLabel.setVisible(false);
                startField.setVisible(false);
                threadLabel.setVisible(false);
                threadSlider.setVisible(false);
                rulesetWarningLabel.setVisible(false);
                startStopButton.setText("Pause");

                /* MOD (Jeremy, fix layer, UB-8): every thread is BUILT, then all of them are counted
                 * alive, the flags are set once, and only then are they started. Upstream started each
                 * thread the moment it was built, and building the next (an emulator, the level set,
                 * the level) takes longer than a short range takes to search -- so an early thread
                 * finished, counted numAlive down to zero and ran the "last one cleans up" block
                 * mid-search: `running` read false (the button then started a SECOND batch over the
                 * same ranges, and the main window unlocked what a running search locks), and each
                 * run() also cleared killFlag, undoing a Pause that landed before the last thread
                 * began. Counted all at once, not in each constructor, so a later build that throws
                 * cannot leave earlier counted threads that never run. SeedSearchTest §2 checks
                 * `running` never drops mid-search. jc-16's, re-applied alone. */
                SeedSearchThread[] workers = new SeedSearchThread[numThreads];
                for (int i = 0; i < numThreads; i++) {
                    long end;
                    if (i != numThreads - 1) {
                        end = start + seedPoolSize * (i + 1) - 1;
                    }
                    else
                        end = Integer.MAX_VALUE;

                    SuperCC threadEmulator = new SuperCC(false);
                    threadEmulator.openLevelset(new File(emulator.getLevelsetPath()));
                    threadEmulator.loadLevel(emulator.getLevel().getLevelNumber(), solution.rngSeed, solution.step, false, solution.ruleset, solution.initialSlide);
                    workers[i] = new SeedSearchThread(i, end, threadEmulator, new Solution(solution));
                }
                running = true;
                killFlag = false;
                numAlive.addAndGet(workers.length);
                for (SeedSearchThread w : workers) w.start();
            }
        });
        untilPositionRadioButton.addActionListener(e -> {
            positionField.setEnabled(true);
            untilPosition = true;
        });
        untilExitRadioButton.addActionListener(e -> {
            positionField.setEnabled(false);
            untilPosition = false;
        });

        JFrame frame = new JFrame("Seed Search");
        frame.setContentPane(panel1);
        frame.pack();
        frame.setLocationRelativeTo(emulator.getMainWindow());
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent windowEvent) { killFlag = true; }
        });
    }

    private void createUIComponents() {
        resultsLabel = new JLabel();
        exampleSeedLabel = new JLabel("Example seed:");
    }

    private void updateValues(int attempsSinceUpdate, int successes, long exampleSuccess, long currentSeed) {
        globalAttempts.addAndGet(attempsSinceUpdate);
        globalSuccesses.addAndGet(successes);
        if (exampleSuccess != NO_SUCCESS)
            globalLastSuccess.set(exampleSuccess);
        if (currentSeed % UPDATE_GUI_RATE == 0)
            updateText();
    }

    private void updateText() {
        long lastSuccessNA = globalLastSuccess.get();
        long globalSuccessesNA = globalSuccesses.get();
        long globalAttemptsNA = globalAttempts.get();
        resultsLabel.setText("Successes: "+ globalSuccessesNA +"/"+globalAttemptsNA+" ("+df.format(100.0 * globalSuccessesNA / globalAttemptsNA)+"%)");
        resultsLabel.repaint();
        if (lastSuccessNA != NO_SUCCESS)
            exampleSeedLabel.setText("Example seed: " + lastSuccessNA);
        exampleSeedLabel.repaint();
    }

    private boolean verifySeed(long seed, Solution solution, SuperCC emulator) {
        solution.rngSeed = (int) seed;
        emulator.getLevel().load(startingState);
        emulator.getLevel().getCheats().setRng((int) seed);
        solution.loadMoves(emulator, TickFlags.LIGHT, false);
        if (!untilPosition)
            return emulator.getLevel().isCompleted();
        else
            return emulator.getLevel().getChip().getPosition().equals(endPosition) && !emulator.getLevel().getChip().isDead();
    }

    public static boolean isRunning() {
        return running;
    }

    private class SeedSearchThread extends Thread {
        int threadNum, attemptsSinceUpdate, successesSinceUpdate;
        long currentSeed, endSeed, lastSuccess;
        SuperCC emulator;
        Solution solution;
        public void run(){
            /* MOD (Jeremy, fix layer, UB-8): `running = true; killFlag = false;` moved out of here to
             * just before the threads start -- see the start/stop button's listener. */
            while (!killFlag && currentSeed <= endSeed) {
                if (verifySeed(currentSeed, solution, emulator)) {
                    successesSinceUpdate++;
                    lastSuccess = currentSeed;
                }
                attemptsSinceUpdate++;
                currentSeed++;
                if (currentSeed % UPDATE_VALUE_RATE == 0) {
                    updateValues(attemptsSinceUpdate, successesSinceUpdate, lastSuccess, currentSeed);
                    attemptsSinceUpdate = 0;
                    successesSinceUpdate = 0;
                }
            }
            updateValues(attemptsSinceUpdate, successesSinceUpdate, lastSuccess, currentSeed); //just have it update with the last result, in case a success is found before an update and it gets canceled
            threadCurrentSeed[threadNum] = currentSeed;
            if (numAlive.decrementAndGet() == 0) { //last one cleans up
                updateText();
                /* MOD (Jeremy, fix layer, UB-8): killFlag cleared BEFORE running is published false.
                 * The other order let a Resume start a new batch in between, so a Pause pressed at
                 * once was then cleared by this, the old batch's cleanup. */
                killFlag = false;
                running = false;
            }
        }

        SeedSearchThread(int threadNum, long endSeed, SuperCC emulator, Solution solution) {
            this.threadNum = threadNum;
            this.currentSeed = threadCurrentSeed[threadNum];
            this.endSeed = endSeed;
            this.lastSuccess = NO_SUCCESS;
            this.emulator = emulator;
            this.solution = solution;
            /* MOD (Jeremy, fix layer, UB-8): no longer counted alive here; the listener adds every
             * worker at once, just before starting them. */
        }
    }
}
