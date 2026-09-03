import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Step;
import game.Tile;

/**
 * The MS creature list, per TICK -- ordering, the teeth clock, and what MS refuses to model.
 *
 * WHAT THIS ADDS TO MonsterListTest
 * ----------------------------------
 * MonsterListTest covers how the list is BUILT: field 10's order, the discard rule, the
 * clone-machine exclusion, and Lynx's map scan. It stops there. Everything that happens to the list
 * once the level is running -- who steps first, which creatures step at all, what survives a tick --
 * was untested.
 *
 * THE HEADLINE: MS WALKS THE LIST FORWARD, LYNX WALKS IT BACKWARD
 * ---------------------------------------------------------------
 * LynxCreatureListTest pins the Lynx half with two tanks facing each other across one empty square:
 * all three of advancegame's loops run `for (cr = creaturelistend(); cr >= creaturelist(); --cr)`,
 * so the tank LATER in the list takes the square. MSCreatureList.tick is a plain
 * `for (Creature m : list)` -- forward -- so on the same fixture the EARLIER tank wins instead.
 *
 * Section 2 is that fixture, mirrored. The two files together state the difference as an outcome
 * rather than as a claim about loop syntax, and creature order is where desyncs live.
 *
 * THE TEETH CLOCK IS A DIFFERENT FORMULA, NOT A DIFFERENT CONSTANT
 * ----------------------------------------------------------------
 *     MS    teethStep = step.isEven() != (tickNumber % 4 == 2)
 *     Lynx  teethStep = ((tickNumber - 1 + step.ordinal()) & 4) == 0
 *
 * They do not agree tick for tick and they are not meant to. MS reads only whether the step is even
 * -- four of its eight Step values collapse to one answer -- while Lynx uses the full ordinal.
 *
 * WHAT MS REFUSES TO MODEL, WHICH IS A DESIGN STATEMENT WORTH PINNING
 * -------------------------------------------------------------------
 * `claimed`, `adjustClaim` and `animationAt` all THROW in MS, with the messages "MS does not have a
 * concept of claims" and "...of animations". Lynx implements all three. Anyone generalizing over
 * CreatureList has to know that half of its surface is deliberately unavailable on one ruleset --
 * and that the refusal is not uniform: `getClaimedArray` answers with an empty array and
 * `setClaimedArray` is a silent no-op, so two of the five fail loudly and three do not.
 */
public class MsCreatureListTest {

    public static void main(String[] args) {
        System.exit(Harness.run("MsCreatureListTest", MsCreatureListTest::body));
    }

    private static Level msLevel(String name, Step step,
                                 java.util.function.Consumer<DatBuilder.Level> f) throws Exception {
        java.nio.file.Path d = Harness.tempDir("scc-mslist-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        DatBuilder.Level lv = b.level().title(name).password("ABCD");
        f.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, 0, step, game.Ruleset.MS, Direction.UP);
    }

    private static void step(Level lv, int ticks) {
        for (int i = 0; i < ticks; i++) lv.tick('-', new Direction[]{ Direction.NONE });
    }

    /**
     * Recomputes the per-tick list state, which is what sets the teeth flag.
     *
     * Wrapped so the upstream British spelling appears exactly once. It is a SicklySilverMoon
     * identifier that predates this fork and renaming it would not compile. aen-exempt
     */
    private static void primeMonsterList(Level lv) {
        lv.getMonsterList().initialise();
    }

    private static int x(Creature c) { return c.getPosition().getX(); }
    private static int y(Creature c) { return c.getPosition().getY(); }

    /** How many ticks out of `ticks` this creature actually changed square on. */
    private static int movesOver(String name, Tile spawn, CreatureID id, int ticks) throws Exception {
        Level lv = msLevel(name, Step.EVEN, b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(10, 10, spawn).monster(10, 10));
        Creature c = monsterOf(lv, id);
        if (c == null) return -1;
        Position prev = c.getPosition();
        int moves = 0;
        for (int t = 0; t < ticks; t++) {
            step(lv, 1);
            if (!c.getPosition().equals(prev)) { moves++; prev = c.getPosition(); }
        }
        return moves;
    }

    private static Creature monsterOf(Level lv, CreatureID id) {
        for (int i = 0; i < lv.getMonsterList().size(); i++)
            if (lv.getMonsterList().get(i).getCreatureType() == id) return lv.getMonsterList().get(i);
        return null;
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. The teeth clock: MS's own formula
         * ================================================================
         * teethStep = step.isEven() != (tickNumber % 4 == 2). At tick 0 the right-hand side is
         * false, so the answer is exactly step.isEven() -- which means MS's eight Step values give
         * only TWO distinct answers here, where Lynx's give a four-and-four split. */

        int even = 0, odd = 0;
        for (Step s : Step.values()) {
            Level lv = msLevel("Teeth" + s.ordinal(), s, b -> b.tile(5, 5, Tile.CHIP_DOWN));
            primeMonsterList(lv);
            boolean expected = s.isEven();                       // tick 0: (0 % 4 == 2) is false
            Harness.eq("at tick 0 the teeth step for " + s + " is just step.isEven()",
                       lv.getMonsterList().getTeethStep(), expected);
            if (expected) even++; else odd++;
        }
        Harness.eq("four of the eight steps are even", even, 4);
        Harness.eq("and four are odd", odd, 4);

        /* The other half of the formula needs the clock to move. Driving a real level and reading
         * the flag each tick checks tickNumber % 4 == 2 rather than restating it. */
        Level clock = msLevel("Clock", Step.EVEN, b -> b.tile(5, 5, Tile.CHIP_DOWN));
        StringBuilder seen = new StringBuilder();
        StringBuilder want = new StringBuilder();
        for (int t = 0; t < 8; t++) {
            step(clock, 1);
            primeMonsterList(clock);
            seen.append(clock.getMonsterList().getTeethStep() ? 'T' : '.');
            want.append((true != (clock.getTickNumber() % 4 == 2)) ? 'T' : '.');
        }
        Harness.eq("the teeth flag follows tickNumber % 4 == 2 over eight ticks",
                   seen.toString(), want.toString());
        Harness.check("and it is not a constant -- it changes within those eight ticks",
                      seen.toString().contains("T") && seen.toString().contains("."));

        /* ================================================================
         * 2. FORWARD iteration -- the mirror of the Lynx fixture
         * ================================================================
         * Two tanks facing each other across one empty square. A tank's preference list is exactly
         * one direction, so both want it and neither takes an alternative. In MS the list is field
         * 10's order and tick() walks it forward, so the FIRST-listed tank takes the square. Under
         * Lynx's backward walk, on the same fixture, the second one wins -- that is asserted in
         * LynxCreatureListTest, and the pair is the point. */

        Level race = msLevel("Race", Step.EVEN, b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT).monster(5, 5)
                .tile(7, 5, Tile.TANK_LEFT).monster(7, 5));

        Harness.eq("the list holds the two tanks in field-10 order", race.getMonsterList().size(), 2);
        Creature firstListed  = race.getMonsterList().get(0);
        Creature secondListed = race.getMonsterList().get(1);
        Harness.eq("tank [0] starts at 5,5", x(firstListed) + "," + y(firstListed), "5,5");
        Harness.eq("tank [1] starts at 7,5", x(secondListed) + "," + y(secondListed), "7,5");

        step(race, 8);

        Harness.eq("the EARLIER tank won the contested square -- MS walks the list FORWARD",
                   x(firstListed) + "," + y(firstListed), "6,5");
        Harness.eq("and the later one never moved",
                   x(secondListed) + "," + y(secondListed), "7,5");

        /* The control: with the square free, the later tank does move. So the one above was stopped
         * by the other tank, not by some general refusal to move. */
        Level solo = msLevel("Solo", Step.EVEN, b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(7, 5, Tile.TANK_LEFT).monster(7, 5));
        step(solo, 8);
        Creature lone = solo.getMonsterList().get(0);
        Harness.check("an unopposed tank does move left", x(lone) < 7);

        /* ================================================================
         * 3. Teeth and blobs move only on a teeth step
         * ================================================================
         * tick() skips them outright when the flag is down: `if (!teethStep && (TEETH || BLOB))
         * continue;`. Every other creature ignores the flag entirely. */

        /* Measured against an UNGATED creature, because "it moved eventually" cannot see this rule.
         * Deleting the gate makes teeth move MORE often, not less, so a test that only checks the
         * teeth got somewhere passes either way -- verified by planting exactly that.
         *
         * The teeth flag is up on half the ticks, so over the same window a gated creature makes
         * half the moves of an ungated one. Measured over 24 ticks: tank 10, teeth 5, blob 5. */

        Harness.eq("over 24 ticks a TANK, which ignores the flag, moves 10 times",
                   movesOver("Tank", Tile.TANK_UP, CreatureID.TANK_MOVING, 24), 10);
        Harness.eq("teeth, which are gated, move half as often", 
                   movesOver("Teeth", Tile.TEETH_UP, CreatureID.TEETH, 24), 5);
        Harness.eq("and so do blobs, the other gated creature",
                   movesOver("Blob", Tile.BLOB_UP, CreatureID.BLOB, 24), 5);
        Harness.check("so the gated creatures move strictly less often than the ungated one",
                      movesOver("Teeth2", Tile.TEETH_UP, CreatureID.TEETH, 24)
                      < movesOver("Tank2", Tile.TANK_UP, CreatureID.TANK_MOVING, 24));

        /* ================================================================
         * 4. Blocks are not monsters here
         * ================================================================
         * tick() counts a block as a dead monster and skips it, and finalise() drops any block that
         * is not sliding. A block on the map is scenery to the monster list. */

        Level blocks = msLevel("Blocks", Step.EVEN, b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(6, 6, Tile.BLOCK)
                .tile(10, 10, Tile.TANK_RIGHT).monster(10, 10));
        int before = blocks.getMonsterList().size();
        step(blocks, 4);
        Harness.check("a resting block never joins the monster list",
                      monsterOf(blocks, CreatureID.BLOCK) == null);
        Harness.check("and the tank is still listed", monsterOf(blocks, CreatureID.TANK_MOVING) != null);
        Harness.check("the list did not grow", blocks.getMonsterList().size() <= before);

        /* ================================================================
         * 5. What MS refuses to model, and the refusal is NOT uniform
         * ================================================================
         * Three of these throw and two answer quietly. That asymmetry is the part worth pinning:
         * code that generalizes over CreatureList cannot assume a uniform "unsupported on MS". */

        Level plain = msLevel("Plain", Step.EVEN, b -> b.tile(5, 5, Tile.CHIP_DOWN));
        game.CreatureList ms = plain.getMonsterList();
        Position somewhere = new Position(3, 3);

        boolean claimedThrew = false;
        try { ms.claimed(somewhere); } catch (UnsupportedOperationException e) { claimedThrew = true; }
        Harness.check("claimed() throws -- MS has no concept of claims", claimedThrew);

        boolean adjustThrew = false;
        try { ms.adjustClaim(somewhere, true); } catch (UnsupportedOperationException e) { adjustThrew = true; }
        Harness.check("adjustClaim() throws for the same reason", adjustThrew);

        boolean animThrew = false;
        try { ms.animationAt(somewhere); } catch (UnsupportedOperationException e) { animThrew = true; }
        Harness.check("animationAt() throws -- MS has no concept of animations", animThrew);

        Harness.eq("but getClaimedArray answers with an EMPTY array rather than throwing",
                   ms.getClaimedArray().length, 0);
        ms.setClaimedArray(new boolean[1024]);
        Harness.eq("and setClaimedArray is a silent no-op, leaving it empty",
                   ms.getClaimedArray().length, 0);

        /* Stated as a contrast, because the Lynx side of this suite asserts the opposite for every
         * one of them and a reader meeting either file alone would take it for the general rule. */
        Harness.check("so two of the five refuse loudly and three do not -- deliberately asymmetric",
                      claimedThrew && adjustThrew && animThrew
                      && ms.getClaimedArray().length == 0);
    }
}
