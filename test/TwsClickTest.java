import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Creature;
import game.Direction;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;
import io.TWSReader;
import io.TWSWriter;
import util.CharList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exporting a MOUSE CLICK to .tws -- the path jc-2 fixed, which had no test.
 *
 * WHAT THE BUG WAS
 * ----------------
 * A click char encodes an offset into the 9x9 scrolled viewport; a .tws stores an offset RELATIVE
 * TO CHIP. Converting between them needs Chip's position AT THE TICK THE CLICK WAS MADE.
 *
 * TWSWriter walks the savestate manager's move list to do that conversion, and
 * restart()/replay() only move SavestateManager's internal cursor -- they do not apply the state
 * back to the Level. Without a load() inside the loop, `level.getChip().getPosition()` returned the
 * same position on every iteration: Chip's position at the END of the solution. So every click was
 * written relative to the wrong cell. Measured at the time: of the 19 click-bearing solutions in
 * the corpus, 13 exported the wrong target, and BlakeE1 #118 "Technical Difficulties" became
 * unreplayable in Tile World -- both its clicks target 31,14 and the writer emitted 30,14, the cell
 * Chip is already sliding into.
 *
 * THE FIXTURE HAS TO BE AT A MAP EDGE, and getting that wrong is easy.
 *
 * The first version of this test clicked from 13,5 and ended at 13,17, and the bug did not show:
 * planting it changed no assertion. The reason is Position.screenPosition, which CLAMPS. For any
 * 5 <= chipX < 27 the viewport is placed at chipX - 4, so Chip sits at offset (4,4) inside it no
 * matter where he is. A click char is an offset into that viewport, so when Chip's offset within
 * the viewport is the same in both states, the subtraction cancels and the wrong position produces
 * the right answer. Two squares in the middle of the map cannot tell the two apart.
 *
 * Where it does show is against an edge, which is exactly where the original was caught: BlakeE1
 * #118's clicks target 31,14, hard against the right-hand clamp.
 *
 * So Chip walks to 2,2 -- inside the left and top clamps, viewport at 0,0, Chip at offset (2,2) --
 * clicks there, and then walks back into the middle:
 *
 *     click made at 2,2    target 4,3     correct relative offset = (+2, +1)
 *     solution ends at 13,13                     with the bug     = (0, -1)
 *
 * because a level left at 13,13 puts the viewport at 9,9 and reads the same click char as 13,12.
 *
 * WHY THIS NEEDS THE REAL EMULATOR
 * ---------------------------------
 * TWSWriter takes a SavestateManager and only touches it when `level.supportsClick()` -- true for
 * MS, false for Lynx, which is why TwsRoundTripTest uses Lynx and explicitly does not cover this.
 * SavestateManager's constructor is package-private in `emulator`, so there is no way to build one
 * from a test in the default package. The way in is the way the application does it: create a
 * SuperCC, open a level set, load a level, and play the moves -- which also means this exercises
 * the real recording path rather than a hand-built stand-in.
 */
public class TwsClickTest {

    public static void main(String[] args) {
        System.exit(Harness.run("TwsClickTest", TwsClickTest::body));
    }

    private static int cx(Level lv) { return lv.getChip().getPosition().getX(); }
    private static int cy(Level lv) { return lv.getChip().getPosition().getY(); }

    /** The relative click offsets a .tws round trip recovers, in order. */
    private static java.util.List<int[]> clicksIn(Solution s) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        char[] m = s.basicMoves;
        for (int i = 0; i < m.length; i++) {
            if (m[i] == SuperCC.CHIP_RELATIVE_CLICK && i + 2 < m.length) {
                out.add(new int[]{ m[i + 1] - 9, m[i + 2] - 9 });
                i += 2;
            }
        }
        return out;
    }

    private static void body() throws Exception {
        Path dir = Harness.tempDir("scc-click-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        b.level().title("Click").password("ABCD").tile(5, 5, Tile.CHIP_DOWN).end();
        File dat = b.writeTo(dir, "click.dat").toFile();

        SuperCC emulator = new SuperCC(false);
        emulator.openLevelset(dat);
        emulator.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Level level = emulator.getLevel();

        Harness.check("MS supports clicks -- this is the half Lynx cannot reach",
                      level.supportsClick());
        Harness.eq("Chip starts where the fixture put him", cx(level) + "," + cy(level), "5,5");

        /* ================================================================
         * 1. One click, made early, with Chip moving a long way afterwards
         * ================================================================ */

        /* Into the top-left clamp, where the viewport stops following Chip. */
        for (int i = 0; i < 3; i++) emulator.tick('l', TickFlags.PRELOADING);
        for (int i = 0; i < 3; i++) emulator.tick('u', TickFlags.PRELOADING);
        Harness.eq("Chip is in the corner clamp before clicking", cx(level) + "," + cy(level), "2,2");
        Harness.eq("and the viewport has stopped following him",
                   Position.screenPosition(level.getChip().getPosition()).getX() + ","
                   + Position.screenPosition(level.getChip().getPosition()).getY(), "0,0");

        Position atClick = level.getChip().getPosition();
        Position target  = new Position(atClick.getX() + 2, atClick.getY() + 1);
        char clickChar   = target.clickChar(atClick);
        Harness.check("the target encodes as a click char", SuperCC.isClick(clickChar));
        emulator.tick(clickChar, TickFlags.PRELOADING);

        /* Back into the middle, where the viewport centers him again -- a different offset inside
         * the viewport from the one the click was made at, which is what makes the bug visible. */
        for (int i = 0; i < 11; i++) emulator.tick('r', TickFlags.PRELOADING);
        for (int i = 0; i < 11; i++) emulator.tick('d', TickFlags.PRELOADING);
        Harness.eq("and Chip ends the solution back in the centered band",
                   cx(level) + "," + cy(level), "13,13");

        CharList moves = emulator.getSavestates().getMoveList();
        Solution solution = new Solution(moves, level.getRngSeed(), level.getStep(),
                                         level.getRuleset(), level.getInitialRFFDirection());

        File twsFile = dir.resolve("click.tws").toFile();
        Files.write(twsFile.toPath(), TWSWriter.write(level, solution, emulator.getSavestates()));
        Solution back = new TWSReader(twsFile).readSolution(level);

        java.util.List<int[]> clicks = clicksIn(back);
        Harness.eq("exactly one click survived the round trip", clicks.size(), 1);
        Harness.eq("its X offset is relative to where Chip stood when the click was MADE",
                   clicks.get(0)[0], 2);
        Harness.eq("and so is its Y offset -- the pre-jc-2 bug writes -1 here, because a level "
                   + "left at 13,13 reads the same click char as 13,12",
                   clicks.get(0)[1], 1);

        /* ================================================================
         * 2. Writing a solution leaves the level where it was
         * ================================================================
         * The conversion walks the level through the whole solution to find each click's moment,
         * so it MUTATES the level and then restores it with savestates.load(-1, level).
         *
         * ⚠ This assertion alone does NOT test that restore, and saying so matters: deleting
         * `savestates.load(-1, level)` leaves every assertion here passing, because the loop's own
         * final load already puts the level at the end of the move list -- which is where the
         * solution ended anyway. Section 6 is the one that actually pins it. */

        Harness.eq("after writing, Chip is back where the solution left him",
                   cx(level) + "," + cy(level), "13,13");

        /* ================================================================
         * 3. TWO clicks, made from different squares
         * ================================================================
         * This is what a single-click test cannot show. The old bug used ONE position -- the final
         * one -- for every click, so with two clicks made from different places it gets both wrong
         * and by different amounts. Each offset here has to be right on its own terms. */

        SuperCC e2 = new SuperCC(false);
        e2.openLevelset(dat);
        e2.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Level lv2 = e2.getLevel();

        for (int i = 0; i < 8; i++) e2.tick('r', TickFlags.PRELOADING);
        Position firstAt = lv2.getChip().getPosition();
        char firstClick = new Position(firstAt.getX() + 2, firstAt.getY() + 1).clickChar(firstAt);
        e2.tick(firstClick, TickFlags.PRELOADING);

        for (int i = 0; i < 12; i++) e2.tick('d', TickFlags.PRELOADING);
        Position secondAt = lv2.getChip().getPosition();
        Harness.check("the two clicks are made from different squares",
                      !secondAt.equals(firstAt));
        char secondClick = new Position(secondAt.getX() - 1, secondAt.getY() + 2).clickChar(secondAt);
        e2.tick(secondClick, TickFlags.PRELOADING);

        for (int i = 0; i < 8; i++) e2.tick('l', TickFlags.PRELOADING);

        Solution sol2 = new Solution(e2.getSavestates().getMoveList(), lv2.getRngSeed(),
                                     lv2.getStep(), lv2.getRuleset(), lv2.getInitialRFFDirection());
        File tws2 = dir.resolve("click2.tws").toFile();
        Files.write(tws2.toPath(), TWSWriter.write(lv2, sol2, e2.getSavestates()));
        java.util.List<int[]> two = clicksIn(new TWSReader(tws2).readSolution(lv2));

        Harness.eq("both clicks survived", two.size(), 2);
        Harness.eq("the first click's X is relative to the FIRST square", two.get(0)[0], 2);
        Harness.eq("the first click's Y is relative to the FIRST square", two.get(0)[1], 1);
        Harness.eq("the second click's X is relative to the SECOND square", two.get(1)[0], -1);
        Harness.eq("the second click's Y is relative to the SECOND square", two.get(1)[1], 2);
        Harness.check("the two offsets differ, so one shared position cannot produce both",
                      two.get(0)[0] != two.get(1)[0] || two.get(0)[1] != two.get(1)[1]);

        /* ================================================================
         * 4. A solution with no clicks at all still writes correctly
         * ================================================================
         * The click block runs for every MS level, clicks or not. It must be a no-op when the move
         * list holds none -- and in particular must not disturb the level, since the same
         * addSavestate/restart/load dance happens either way. */

        SuperCC e3 = new SuperCC(false);
        e3.openLevelset(dat);
        e3.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Level lv3 = e3.getLevel();
        for (int i = 0; i < 8; i++) e3.tick('r', TickFlags.PRELOADING);
        String restingPlace = cx(lv3) + "," + cy(lv3);

        Solution sol3 = new Solution(e3.getSavestates().getMoveList(), lv3.getRngSeed(),
                                     lv3.getStep(), lv3.getRuleset(), lv3.getInitialRFFDirection());
        File tws3 = dir.resolve("noclick.tws").toFile();
        Files.write(tws3.toPath(), TWSWriter.write(lv3, sol3, e3.getSavestates()));

        Harness.eq("a click-free MS solution leaves the level where it was", cx(lv3) + "," + cy(lv3), restingPlace);
        Solution back3 = new TWSReader(tws3).readSolution(lv3);
        Harness.eq("and it round-trips with no clicks in it", clicksIn(back3).size(), 0);
        /* MS records a move and then a wait for each tick pair, so eight rightward steps come back
         * as "r-r-r-r-r-r-r-r-". Comparing with the waits stripped states the thing worth stating --
         * eight moves right, none lost -- without pinning the tick padding, which is the format's
         * business rather than this test's. */
        String recovered = new String(back3.basicMoves);
        Harness.eq("the recorded and recovered forms agree exactly",
                   recovered, new String(sol3.basicMoves));
        Harness.eq("with its moves intact: eight steps right, waits stripped",
                   recovered.replace("-", ""), "rrrrrrrr");

        /* ================================================================
         * 5. A click as the VERY FIRST move
         * ================================================================
         * The conversion loop has two load() calls: one before it starts and one at the bottom of
         * each iteration. Only this case can tell them apart. With a click anywhere but first, the
         * in-loop load has already corrected the level by the time the click is reached, so
         * deleting the pre-loop one changes nothing -- verified by planting exactly that and
         * watching every assertion above still pass.
         *
         * So Chip STARTS in the corner clamp and clicks before moving at all. Now iteration zero is
         * the click, nothing has loaded yet, and the pre-loop call is the only thing standing
         * between the writer and Chip's end-of-solution position. */

        DatBuilder cb = new DatBuilder().signature(DatBuilder.SIG_MS);
        cb.level().title("First").password("WXYZ").tile(2, 2, Tile.CHIP_DOWN).end();
        File cornerDat = cb.writeTo(dir, "corner.dat").toFile();

        SuperCC e4 = new SuperCC(false);
        e4.openLevelset(cornerDat);
        e4.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Level lv4 = e4.getLevel();
        Harness.eq("Chip starts in the corner clamp", cx(lv4) + "," + cy(lv4), "2,2");

        Position startAt = lv4.getChip().getPosition();
        char firstMoveClick = new Position(startAt.getX() + 2, startAt.getY() + 1).clickChar(startAt);
        e4.tick(firstMoveClick, TickFlags.PRELOADING);          // the click IS move zero
        for (int i = 0; i < 11; i++) e4.tick('r', TickFlags.PRELOADING);
        for (int i = 0; i < 11; i++) e4.tick('d', TickFlags.PRELOADING);
        Harness.eq("and ends in the centered band", cx(lv4) + "," + cy(lv4), "13,13");

        Solution sol4 = new Solution(e4.getSavestates().getMoveList(), lv4.getRngSeed(),
                                     lv4.getStep(), lv4.getRuleset(), lv4.getInitialRFFDirection());
        File tws4 = dir.resolve("first.tws").toFile();
        Files.write(tws4.toPath(), TWSWriter.write(lv4, sol4, e4.getSavestates()));
        java.util.List<int[]> firstClicks = clicksIn(new TWSReader(tws4).readSolution(lv4));

        Harness.eq("the leading click survived", firstClicks.size(), 1);
        Harness.eq("its X is relative to Chip's STARTING square, not his last one",
                   firstClicks.get(0)[0], 2);
        Harness.eq("and its Y likewise -- this is the assertion the pre-loop load exists for",
                   firstClicks.get(0)[1], 1);

        /* ================================================================
         * 6. Exporting while REWOUND must not move the player
         * ================================================================
         * This is what savestates.load(-1, level) is for, and nothing above reaches it: the loop's
         * last iteration already leaves the level at the end of the move list, which is where an
         * un-rewound solution ends. The restore only earns its keep when the level is somewhere
         * else -- which is exactly the case when someone has stepped back through their solution to
         * look at something and then exports it.
         *
         * Without the restore, saving would silently jump them to the end of their own solution.
         * That is a save with a side effect on the thing being saved, which is the shape of bug
         * that costs work. */

        SuperCC e5 = new SuperCC(false);
        e5.openLevelset(cornerDat);
        e5.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Level lv5 = e5.getLevel();
        Position start5 = lv5.getChip().getPosition();
        e5.tick(new Position(start5.getX() + 2, start5.getY() + 1).clickChar(start5),
                TickFlags.PRELOADING);
        for (int i = 0; i < 11; i++) e5.tick('r', TickFlags.PRELOADING);
        for (int i = 0; i < 11; i++) e5.tick('d', TickFlags.PRELOADING);
        Harness.eq("the solution ends in the middle", cx(lv5) + "," + cy(lv5), "13,13");

        /* Step back through it the way the GUI does: move the cursor, then apply the state. */
        for (int i = 0; i < 6; i++) e5.getSavestates().rewind();
        lv5.load(e5.getSavestates().getSavestate());
        String rewoundTo = cx(lv5) + "," + cy(lv5);
        Harness.check("rewinding actually moved the level back (now at " + rewoundTo + ")",
                      !rewoundTo.equals("13,13"));

        Solution sol5 = new Solution(e5.getSavestates().getMoveList(), lv5.getRngSeed(),
                                     lv5.getStep(), lv5.getRuleset(), lv5.getInitialRFFDirection());
        File tws5 = dir.resolve("rewound.tws").toFile();
        Files.write(tws5.toPath(), TWSWriter.write(lv5, sol5, e5.getSavestates()));

        Harness.eq("exporting while rewound leaves the player exactly where they were",
                   cx(lv5) + "," + cy(lv5), rewoundTo);

        /* And the file is still correct -- the restore must not cost the click its position. */
        java.util.List<int[]> rewoundClicks = clicksIn(new TWSReader(tws5).readSolution(lv5));
        Harness.eq("the click is still written from the right square", rewoundClicks.size(), 1);
        Harness.eq("with the same X as when not rewound", rewoundClicks.get(0)[0], 2);
        Harness.eq("and the same Y", rewoundClicks.get(0)[1], 1);
    }
}
