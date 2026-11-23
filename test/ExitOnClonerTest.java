import emulator.SuperCC;
import emulator.TickFlags;
import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;
import tools.TSPGUI;
import tools.tsp.ActingWallParameters;
import tools.tsp.SimulatedAnnealingParameters;
import tools.tsp.TSPSolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * An EXIT sitting on a CLONE MACHINE refuses Chip, under MS (jc-47).
 *
 * THE RULE, AND WHERE IT COMES FROM
 * ---------------------------------
 * In MSCC Chip cannot step onto a square whose top layer is an exit and whose bottom layer is a
 * clone machine. That is the maintainer's ruling (2026-11-23), and it is also what official Tile
 * World 2.3.1 does: mslogic.c's canmakemove() ends with
 *
 *     if (cellat(to)->bot.id == CloneMachine)
 *         return FALSE;
 *
 * -- the same `return FALSE` that a wall gets. Upstream SuperCC instead judged Chip against the top
 * tile whenever it was not a creature, so he walked onto the exit and won. 247 levels in the
 * maintainer's collection carry an exit on a clone machine (40 sets); level designers use it as an
 * exit Chip cannot take.
 *
 * So the oracle here is not an observed output: a refused square must behave EXACTLY as a wall in
 * the same place does, tick for tick -- the position, the facing, the sliding flag, the RNG, the
 * win -- on foot, on ice, on force floors (random ones included), out of a teleport, and toward
 * mouse clicks. The RNG is in the comparison because it is what decides a desync: a refusal that
 * drew one more random number than a wall would replay every later random force floor and blob
 * differently. Each case also has a CONTROL, a plain exit in the same place, which must be won;
 * without it a fixture that never reached the square at all would pass every refusal check.
 *
 * THE SCOPE IS EXIT ONLY, ON PURPOSE
 * ----------------------------------
 * Tile World refuses Chip EVERY square with a buried clone machine. Whether MSCC does for a chip, a
 * key, boots, a door or plain floor sitting on one is not settled, and the maintainer chose not to
 * assume it. Section 4 pins that those are still entered as before, so a later change that widens
 * the rule has to do so on purpose.
 */
public class ExitOnClonerTest {

    public static void main(String[] args) {
        System.exit(Harness.run("ExitOnClonerTest", ExitOnClonerTest::body));
    }

    private static Level build(String name, Consumer<DatBuilder.Level> f) throws Exception {
        return build(name, 0, f);
    }

    private static Level build(String name, int seed, Consumer<DatBuilder.Level> f) throws Exception {
        Path d = Harness.tempDir("scc-exitcloner-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        DatBuilder.Level lv = b.level().title(name).password("ABCD");
        f.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, seed, Step.EVEN, Ruleset.MS, Direction.UP);
    }

    /** The same, opened through the real emulator, for clicks and the TSP solver. */
    private static SuperCC emulator(String name, int seed, Consumer<DatBuilder.Level> f) throws Exception {
        Path d = Harness.tempDir("scc-exitcloner-emu-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        DatBuilder.Level lv = b.level().title(name).password("ABCD");
        f.accept(lv);
        lv.end();
        SuperCC e = new SuperCC(false);
        e.openLevelset(b.writeTo(d, name + ".dat").toFile());
        e.loadLevel(1, seed, Step.EVEN, false, Ruleset.MS, Direction.UP);
        return e;
    }

    /** What sits on the square under test: the refused square, the wall it must act like, or the
     *  plain exit that proves the fixture reaches the square at all. */
    private enum Target {
        EXIT_ON_CLONER, WALL, PLAIN_EXIT;

        void place(DatBuilder.Level b, int x, int y) {
            switch (this) {
                case EXIT_ON_CLONER -> b.tile(x, y, Tile.EXIT).under(x, y, Tile.CLONE_MACHINE);
                case WALL           -> b.tile(x, y, Tile.WALL);
                case PLAIN_EXIT     -> b.tile(x, y, Tile.EXIT);
            }
        }
    }

    private static final int REFUSED = Target.EXIT_ON_CLONER.ordinal(), WALL = Target.WALL.ordinal(),
                             CONTROL = Target.PLAIN_EXIT.ordinal();

    private static Direction dir(char c) {
        return switch (c) {
            case 'u' -> Direction.UP;
            case 'l' -> Direction.LEFT;
            case 'd' -> Direction.DOWN;
            case 'r' -> Direction.RIGHT;
            default  -> Direction.NONE;
        };
    }

    private static char ch(Direction d) {
        switch (d) {
            case UP: return 'u'; case LEFT: return 'l';
            case DOWN: return 'd'; case RIGHT: return 'r';
            default: return '-';
        }
    }

    private static String at(Position p) { return p.getX() + "," + p.getY(); }

    /** Everything a refusal can show, one line per tick. */
    private static String state(Level lv) {
        Creature c = lv.getChip();
        return at(c.getPosition()) + " " + c.getDirection() + (c.isSliding() ? " sliding" : "")
                + (c.isDead() ? " dead" : "") + (lv.isCompleted() ? " WON" : "")
                + " rng=" + lv.getRNG().getCurrentValue();
    }

    /** Ticks with `first` held for `held` ticks, then nothing for `idle` more, and returns the state
     *  after every tick. */
    private static String run(Level lv, Direction first, int held, int idle) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < held + idle; i++) {
            Direction d = i < held ? first : Direction.NONE;
            lv.tick(ch(d), new Direction[]{ d });
            s.append(state(lv)).append('\n');
            if (lv.isCompleted() || lv.getChip().isDead()) break;
        }
        return s.toString();
    }

    /** One tick per character of `keys` (u l d r, or - for none), and the state after each. */
    private static String play(Level lv, String keys) {
        StringBuilder s = new StringBuilder();
        for (char c : keys.toCharArray()) {
            lv.tick(c, new Direction[]{ dir(c) });
            s.append(state(lv)).append('\n');
            if (lv.isCompleted() || lv.getChip().isDead()) break;
        }
        return s.toString();
    }

    /** Clicks toward wherever `goal` says, one emulator tick at a time (a HALF move under MS), and
     *  the state after each. `first`, if not 0, is ticked once before the clicks. */
    private static String clicks(SuperCC e, char first, java.util.function.Function<Level, Position> goal, int ticks) {
        Level l = e.getLevel();
        StringBuilder s = new StringBuilder();
        if (first != 0) { e.tick(first, TickFlags.PRELOADING); s.append(state(l)).append('\n'); }
        for (int i = 0; i < ticks && !l.isCompleted(); i++) {
            e.tick(goal.apply(l).clickChar(l.getChip().getPosition()), TickFlags.PRELOADING);
            s.append(state(l)).append('\n');
        }
        return s.toString();
    }

    /** The three outcomes every case checks: the control is won, the square is not, and the square
     *  gave exactly what a wall gives. */
    private static void refusedLikeAWall(String what, String[] runs) {
        Harness.check(what + ": the control, a plain exit there, is won", runs[CONTROL].contains("WON"));
        Harness.check(what + ": is NOT won", !runs[REFUSED].contains("WON"));
        Harness.eq(what + ": every tick is what a wall there gives", runs[REFUSED], runs[WALL]);
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. On foot, from all four sides: refused, exactly as a wall refuses
         * ================================================================
         * The square is at 10,10 and Chip starts on the side he walks in from. */

        Direction[] sides = { Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT };
        int[][] from = { { 10, 11 }, { 11, 10 }, { 10, 9 }, { 9, 10 } };
        for (int i = 0; i < sides.length; i++) {
            Direction d = sides[i];
            int sx = from[i][0], sy = from[i][1];
            String[] runs = new String[Target.values().length];
            Level refused = null;
            for (Target t : Target.values()) {
                Level lv = build("Walk" + d + t, b -> {
                    b.tile(sx, sy, Tile.CHIP_DOWN);
                    t.place(b, 10, 10);
                });
                runs[t.ordinal()] = run(lv, d, 4, 2);
                if (t == Target.EXIT_ON_CLONER) refused = lv;
            }
            String what = "walking " + d + " onto an exit on a clone machine";
            refusedLikeAWall(what, runs);
            Harness.check(what + ": Chip is alive", !refused.getChip().isDead());
            Harness.eq(what + ": Chip is still where he started", at(refused.getChip().getPosition()), sx + "," + sy);
            Harness.eq(what + ": the exit is still on top", refused.getLayerFG().get(new Position(10, 10)), Tile.EXIT);
            Harness.eq(what + ": and the clone machine still under it",
                       refused.getLayerBG().get(new Position(10, 10)), Tile.CLONE_MACHINE);
        }

        /* An exit on FLOOR is still an exit: the refusal is the clone machine's, not the exit's.
         * (Section 1's controls have nothing under the exit at all; this names the floor.) */
        Level onFloor = build("ExitOnFloor", b -> b.tile(9, 10, Tile.CHIP_DOWN)
                .tile(10, 10, Tile.EXIT).under(10, 10, Tile.FLOOR));
        run(onFloor, Direction.RIGHT, 4, 2);
        Harness.check("an exit with floor under it is still won", onFloor.isCompleted());

        /* ================================================================
         * 2. Sliding into it: ice, a force floor, and a random force floor
         * ================================================================
         * A slide is where HOW a move is refused matters: the slide flag, the facing and the RNG
         * afterwards decide whether Chip bounces back off ice, rests on the force floor, or which
         * way a random force floor sends him next. Chip steps right onto three tiles of ice (or
         * force floor) that end at the square. */

        Tile[] slides = { Tile.ICE, Tile.FF_RIGHT };
        for (Tile floor : slides) {
            String[] runs = new String[Target.values().length];
            Level refused = null;
            for (Target t : Target.values()) {
                Level lv = build("Slide" + floor + t, b -> {
                    b.tile(5, 10, Tile.CHIP_DOWN);
                    for (int x = 6; x <= 8; x++) b.tile(x, 10, floor);
                    t.place(b, 9, 10);
                });
                runs[t.ordinal()] = run(lv, Direction.RIGHT, 2, 14);
                if (t == Target.EXIT_ON_CLONER) refused = lv;
            }
            String what = "sliding on " + floor + " into an exit on a clone machine";
            refusedLikeAWall(what, runs);
            Harness.check(what + ": Chip is alive", !refused.getChip().isDead());
        }

        /* A random force floor with the square on its other three sides: three of its four draws
         * send Chip into the square (the fourth sends him back where he came from, and he rides it
         * again). Ten seeds, because the floor's draw decides the direction; the control being won
         * on all ten is what proves the draws reach the square. */
        int controlWon = 0, refusedWon = 0, likeWall = 0;
        for (int seed = 0; seed < 10; seed++) {
            String[] runs = new String[Target.values().length];
            for (Target t : Target.values()) {
                final int s = seed;
                Level lv = build("Rff" + t + s, s, b -> {
                    b.tile(8, 10, Tile.CHIP_DOWN).tile(9, 10, Tile.FF_RANDOM);
                    t.place(b, 10, 10); t.place(b, 9, 9); t.place(b, 9, 11);
                });
                runs[t.ordinal()] = run(lv, Direction.RIGHT, 1, 29);
            }
            if (runs[CONTROL].contains("WON")) controlWon++;
            if (runs[REFUSED].contains("WON")) refusedWon++;
            if (runs[REFUSED].equals(runs[WALL])) likeWall++;
        }
        Harness.eq("a random force floor ringed by the square: the control is won on every seed", controlWon, 10);
        Harness.eq("a random force floor ringed by the square: no seed is won", refusedWon, 0);
        Harness.eq("a random force floor ringed by the square: every seed is what walls give, RNG and all",
                   likeWall, 10);

        /* A KEY into the square from a random force floor. A key is not a slide move, and when one
         * is refused there the wall's path keeps Chip's old facing and draws NO random number; a
         * refusal that went down the slide's path instead would turn him and draw one. A pop-up
         * wall closes behind Chip, so he stays sealed on the floor while the keys keep coming.
         * (From the jc-47 review: without this case a refusal built that way passed every check.) */
        int[] seeds = { 0, 1, 7, 12345 };
        for (int seed : seeds) {
            String[] runs = new String[Target.values().length];
            for (Target t : Target.values()) {
                Level lv = build("RffKey" + t + seed, seed, b -> {
                    b.tile(8, 10, Tile.CHIP_DOWN).tile(9, 10, Tile.POP_UP_WALL).tile(10, 10, Tile.FF_RANDOM)
                     .tile(11, 10, Tile.WALL).tile(10, 11, Tile.WALL);
                    t.place(b, 10, 9);
                });
                runs[t.ordinal()] = play(lv, "r-r-uuuuuuuu----");
            }
            refusedLikeAWall("a key from a random force floor into it, seed " + seed, runs);
        }

        /* ================================================================
         * 3. Out of a teleport, and toward mouse clicks
         * ================================================================
         * The other ways Chip moves. A teleport whose only way out is the square: choosing an exit
         * already skipped a buried clone machine (MSCreature.teleport), and the step out goes
         * through the same check as a walk. */

        String[] tele = new String[Target.values().length];
        for (Target t : Target.values()) {
            Level lv = build("Teleport" + t, b -> {
                b.tile(5, 10, Tile.CHIP_DOWN).tile(6, 10, Tile.TELEPORT);
                t.place(b, 7, 10);
            });
            tele[t.ordinal()] = run(lv, Direction.RIGHT, 2, 10);
        }
        refusedLikeAWall("out of a teleport onto it", tele);

        /* A click, through the real emulator, as a player makes one: the click sets a goal and Chip
         * walks toward it. Two floor squares, then the square. Each emulator tick is a HALF move
         * here, so three squares take six; ten leave room to be refused and stay refused. */
        String[] click = new String[Target.values().length];
        for (Target t : Target.values()) {
            SuperCC e = emulator("Click" + t, 0, b -> { b.tile(5, 5, Tile.CHIP_DOWN); t.place(b, 8, 5); });
            click[t.ordinal()] = clicks(e, (char) 0, l -> new Position(8, 5), 10);
        }
        refusedLikeAWall("a click on it", click);
        Harness.check("a click on it: Chip walks up to it and ENDS beside it, facing it",
                      click[REFUSED].endsWith("\n7,5 RIGHT rng=0\n"));

        /* A click past it, diagonally, so Chip is offered TWO directions in one move (seek gives
         * right, then down) and the first is the square. */
        String[] click2 = new String[Target.values().length];
        for (Target t : Target.values()) {
            SuperCC e = emulator("ClickTwo" + t, 0, b -> { b.tile(5, 5, Tile.CHIP_DOWN); t.place(b, 6, 5); });
            click2[t.ordinal()] = clicks(e, (char) 0, l -> new Position(8, 7), 12);
        }
        refusedLikeAWall("a two-direction click whose first direction is it", click2);

        /* Riding a force floor up a column with the square beside every tile of it, clicking to
         * the side each tick: the click overrides the floor (MSLevel's CLICK_EARLY) into it. */
        String[] ride = new String[Target.values().length];
        for (Target t : Target.values()) {
            SuperCC e = emulator("Ride" + t, 0, b -> {
                b.tile(10, 12, Tile.CHIP_DOWN);
                for (int y = 7; y <= 11; y++) { b.tile(10, y, Tile.FF_UP); t.place(b, 11, y); }
            });
            ride[t.ordinal()] = clicks(e, SuperCC.UP, l -> new Position(13, l.getChip().getPosition().getY()), 10);
        }
        refusedLikeAWall("a click overriding a force floor into it", ride);

        /* ================================================================
         * 4. The scope: ONLY the exit. Other tiles on a clone machine are entered as before
         * ================================================================
         * The maintainer's decision (2026-11-23), not a claim about MSCC: Tile World refuses all of
         * these, and whether MSCC does is not settled. These pass on the build before jc-47 too;
         * they fail only if the rule is widened. A wall beyond the square stops Chip ON it, so
         * "he is on it" is the check rather than "he got past it". */

        Tile[] stillOpen = { Tile.FLOOR, Tile.CHIP, Tile.KEY_BLUE, Tile.BOOTS_FIRE, Tile.HINT };
        for (Tile top : stillOpen) {
            Level lv = build("Open" + top, b -> b.tile(9, 10, Tile.CHIP_DOWN).tile(11, 10, Tile.WALL)
                    .tile(10, 10, top).under(10, 10, Tile.CLONE_MACHINE));
            if (top == Tile.CHIP) lv.setChipsLeft(3);
            run(lv, Direction.RIGHT, 4, 0);
            Harness.eq("Chip still walks onto " + top + " on a clone machine",
                       at(lv.getChip().getPosition()), "10,10");
            if (top == Tile.CHIP) Harness.eq("and collects the chip", lv.getChipsLeft(), 2);
            if (top == Tile.KEY_BLUE) Harness.eq("and picks up the key", lv.getKeys()[0], (short) 1);
            if (top == Tile.BOOTS_FIRE) Harness.eq("and picks up the boots", lv.getBoots()[1], (byte) 1);
        }

        Level door = build("OpenDoor", b -> b.tile(9, 10, Tile.CHIP_DOWN).tile(11, 10, Tile.WALL)
                .tile(10, 10, Tile.DOOR_RED).under(10, 10, Tile.CLONE_MACHINE));
        door.getKeys()[1] = 1;
        run(door, Direction.RIGHT, 4, 0);
        Harness.eq("Chip still opens a door on a clone machine and walks in",
                   at(door.getChip().getPosition()), "10,10");

        /* ================================================================
         * 5. Nobody else gets in either -- the rest of the same expression
         * ================================================================
         * The new condition shares its line with the one that turns monsters and blocks away from a
         * clone machine (`!isChip`), so a slip in that line shows here. A tank aimed at an exit
         * would test nothing -- the exit refuses monsters by itself -- so it is aimed at a KEY on a
         * clone machine, which a monster may otherwise enter. It is found BEFORE it runs: a tank
         * that cannot move becomes TANK_STATIONARY. A pushed block is the case the exit does not
         * refuse by itself (blocks may enter an exit), so it is aimed at the exit. */

        Level tank = build("Tank", b -> b.tile(1, 1, Tile.CHIP_DOWN)
                .tile(9, 10, Tile.TANK_RIGHT).monster(9, 10)
                .tile(10, 10, Tile.KEY_BLUE).under(10, 10, Tile.CLONE_MACHINE));
        Creature tk = null;
        for (int i = 0; i < tank.getMonsterList().size(); i++)
            if (tank.getMonsterList().get(i).getCreatureType() == CreatureID.TANK_MOVING) tk = tank.getMonsterList().get(i);
        Harness.check("the fixture has a tank", tk != null);
        run(tank, Direction.NONE, 0, 12);
        Harness.eq("a tank does not drive onto a key on a clone machine",
                   tk == null ? "none" : at(tk.getPosition()), "9,10");

        /* Its control: the same tank, the key on plain floor (a wall beyond stops it there). */
        Level tankControl = build("TankControl", b -> b.tile(1, 1, Tile.CHIP_DOWN)
                .tile(9, 10, Tile.TANK_RIGHT).monster(9, 10).tile(10, 10, Tile.KEY_BLUE).tile(11, 10, Tile.WALL));
        Creature tc = null;
        for (int i = 0; i < tankControl.getMonsterList().size(); i++)
            if (tankControl.getMonsterList().get(i).getCreatureType() == CreatureID.TANK_MOVING) tc = tankControl.getMonsterList().get(i);
        run(tankControl, Direction.NONE, 0, 12);
        Harness.eq("the control: the same tank drives onto a key on plain floor",
                   tc == null ? "none" : at(tc.getPosition()), "10,10");

        Level block = build("Block", b -> b.tile(8, 10, Tile.CHIP_DOWN).tile(9, 10, Tile.BLOCK)
                .tile(10, 10, Tile.EXIT).under(10, 10, Tile.CLONE_MACHINE));
        run(block, Direction.RIGHT, 4, 0);
        Harness.eq("a block is not pushed onto an exit on a clone machine",
                   block.getLayerFG().get(new Position(9, 10)), Tile.BLOCK);
        Harness.eq("so Chip stays behind it", at(block.getChip().getPosition()), "8,10");
        Harness.check("and nothing is won", !block.isCompleted());

        /* Its control: the same push onto a plain exit goes through (one tick: the block moves,
         * and Chip steps where it was). */
        Level blockControl = build("BlockControl", b -> b.tile(8, 10, Tile.CHIP_DOWN).tile(9, 10, Tile.BLOCK)
                .tile(10, 10, Tile.EXIT));
        run(blockControl, Direction.RIGHT, 1, 0);
        Harness.eq("the control: the same block is pushed onto a plain exit",
                   at(blockControl.getChip().getPosition()), "9,10");

        /* ================================================================
         * 6. The TSP solver plans around it
         * ================================================================
         * Tools > TSP Solver wipes the bottom layer before it plans (TSPSolver.normalizeState), so
         * without help it sees an ordinary exit there, routes Chip to it, plays the route on the
         * real level -- where the engine refuses the last step -- and still says "Finished!".
         * One chip, the square two steps past it, and a plain exit far beyond. Headless:
         * TSPGUI(emulator, false) builds no window. */

        SuperCC tspEmu = emulator("Tsp", 0, b -> b.chips(1)
                .tile(2, 2, Tile.CHIP_DOWN).tile(4, 2, Tile.CHIP)
                .tile(6, 2, Tile.EXIT).under(6, 2, Tile.CLONE_MACHINE)
                .tile(20, 2, Tile.EXIT));
        String tspDone = tsp(tspEmu);
        Level tl = tspEmu.getLevel();
        Position end = tl.getChip().getPosition();
        Harness.eq("the TSP solver finishes", tspDone, "ok");
        Harness.check("and its route is won",
                      tl.isCompleted() || tl.getLayerFG().get(end) == Tile.EXITED_CHIP);
        Harness.eq("at the plain exit, not the one on the clone machine", at(end), "20,2");

        SuperCC tspOnly = emulator("TspOnly", 0, b -> b.chips(1)
                .tile(2, 2, Tile.CHIP_DOWN).tile(4, 2, Tile.CHIP)
                .tile(6, 2, Tile.EXIT).under(6, 2, Tile.CLONE_MACHINE));
        Harness.check("with ONLY an exit on a clone machine, the solver says no exit can be reached",
                      tsp(tspOnly).contains("No exit accessible"));
    }

    /** Runs the TSP solver as Tools > TSP Solver does, on every chip and exit. "ok", or the message
     *  it refused with. */
    private static String tsp(SuperCC e) {
        try {
            TSPGUI gui = new TSPGUI(e, false);
            TSPSolver s = new TSPSolver(e, gui, gui.getAllChips(), gui.getAllExits(), new ArrayList<>(),
                    new SimulatedAnnealingParameters(100, 0.1, 0.95, 1000),
                    new ActingWallParameters(false, false, false, false, false), new javax.swing.JTextPane());
            s.solve();
            return "ok";
        } catch (Exception ex) {
            return String.valueOf(ex.getMessage());
        }
    }
}
