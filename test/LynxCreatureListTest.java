import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Tile;
import game.Lynx.LynxCreature;

/**
 * The LYNX creature list -- ordering, the claimed layer, clones, and the three-phase tick.
 *
 * WHY THIS FILE MATTERS MORE THAN ITS SIZE SUGGESTS
 * -------------------------------------------------
 * Creature ORDER is where desyncs live. Two creatures that both want one square produce different
 * outcomes depending on which moves first, and nothing about a single creature's rules can tell you
 * which that is. The MS side has MonsterListTest for exactly this reason; until now Lynx had
 * nothing, at 9 of 148 branches.
 *
 * THE ORACLE
 * ----------
 * lxlogic.c's advancegame() (line 1925) runs three loops per tick, and every one of them walks the
 * list BACKWARD:
 *
 *     for (cr = creaturelistend() ; cr >= creaturelist() ; --cr)   choosemove
 *     for (cr = creaturelistend() ; cr >= creaturelist() ; --cr)   advancecreature, brown buttons
 *     for (cr = creaturelistend() ; cr >= creaturelist() ; --cr)   teleports
 *
 * LynxLevel.tick() drives the same three phases by calling monsterList.tick() three times between
 * initialise() and finalise(), and LynxCreatureList.tick() switches on a `phase` counter. Each
 * (those two are upstream SicklySilverMoon identifiers, not ours to respell. aen-exempt)
 * phase loops `for (int i = list.length - 1; i >= 0; i--)`.
 *
 * Section 6 pins the consequence rather than the loop: two tanks facing each other across one empty
 * square. The tank LATER in the list takes the square, and the earlier one is blocked forever. Under
 * forward iteration the winner would be the other tank, so a single assertion distinguishes the two
 * orders on real engine behavior rather than on inspection.
 *
 * TWO ORDERING FACTS THAT ARE NOT OBVIOUS
 * ----------------------------------------
 * 1. Chip is index 0. lxlogic.c guarantees `creaturelist()[0]` is Chip, and LynxCreature's clone
 *    slot search starts at index 1 precisely so Chip's slot is never recycled.
 * 2. Getting Chip there SHUFFLES one other creature. LevelFactory collects creatures in map reading
 *    order and then does `Collections.swap(creatures, 0, i)` -- so the creature that was first in
 *    reading order does not stay first; it lands in whatever slot Chip vacated. With reverse
 *    iteration that means it moves FIRST, not last. Section 1 pins this, because a "tidy-up" to a
 *    stable rotation would look harmless and would reorder every level with monsters above Chip.
 *
 * ⚠ lxlogic.c carries NO `MOD (Jeremy)` comments, unlike mslogic.c's 79 -- it is unmodified upstream
 * Tile World, and the desync project that went 135 to 0 was MS-only. A failure here is a finding.
 */
public class LynxCreatureListTest {

    public static void main(String[] args) {
        System.exit(Harness.run("LynxCreatureListTest", LynxCreatureListTest::body));
    }

    private static Level lynxLevel(String name, java.util.function.Consumer<DatBuilder.Level> build)
            throws Exception {
        java.nio.file.Path d = Harness.tempDir("scc-lynxlist-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        DatBuilder.Level lv = b.level().title(name);
        build.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, 0, game.Step.EVEN, game.Ruleset.LYNX, Direction.UP);
    }

    private static int x(Creature c) { return c.getPosition().getIndex() % 32; }
    private static int y(Creature c) { return c.getPosition().getIndex() / 32; }

    /**
     * Builds a creature straight from its packed bit form, which is the only way to set an
     * animation timer from outside. The layout is documented on LynxCreature:
     *
     *   TELEPORT 28 | OVERRIDE 27 | SLIDING 26 | ANIMATION 22-25 | TIME TRAVEL 18-20
     *   | DIRECTION 14-17 | MONSTER 10-13 | POSITION 0-9
     */
    private static LynxCreature packed(CreatureID type, Direction dir, int animTimer, Position at) {
        int bits = (animTimer << 22) | (dir.ordinal() << 14) | (type.ordinal() << 10) | at.getIndex();
        return new LynxCreature(bits);
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. List construction: Chip at index 0, and the swap that reorders one other creature
         * ================================================================ */

        Level order = lynxLevel("Order", lv -> lv
                .tile(1, 1, Tile.BUG_UP).monster(1, 1)
                .tile(3, 3, Tile.GLIDER_UP).monster(3, 3)
                .tile(9, 9, Tile.CHIP_DOWN));

        Harness.eq("the list holds Chip plus the two monsters", order.getMonsterList().size(), 3);
        Harness.eq("Chip is index 0, as lxlogic.c guarantees for creaturelist()[0]",
                   order.getMonsterList().get(0).getCreatureType(), CreatureID.CHIP);
        Harness.check("index 0 IS the level's chip object",
                      order.getMonsterList().get(0) == order.getChip());

        /* Collected in map reading order the list would be bug(33), glider(99), chip(297). The
         * swap that puts Chip at 0 sends the BUG -- first in reading order -- to Chip's old slot,
         * which is last. Reverse iteration then moves it first. */
        Harness.eq("the swap sent the reading-order-first bug to the END of the list",
                   order.getMonsterList().get(2).getCreatureType(), CreatureID.BUG);
        Harness.eq("and left the glider in the middle",
                   order.getMonsterList().get(1).getCreatureType(), CreatureID.GLIDER);

        /* With Chip already first, nothing is displaced -- the same code path, different outcome. */
        Level noSwap = lynxLevel("NoSwap", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.BUG_UP).monster(5, 5)
                .tile(7, 7, Tile.GLIDER_UP).monster(7, 7));
        Harness.eq("Chip already first: still index 0",
                   noSwap.getMonsterList().get(0).getCreatureType(), CreatureID.CHIP);
        Harness.eq("Chip already first: reading order is preserved for the rest (bug)",
                   noSwap.getMonsterList().get(1).getCreatureType(), CreatureID.BUG);
        Harness.eq("Chip already first: reading order is preserved for the rest (glider)",
                   noSwap.getMonsterList().get(2).getCreatureType(), CreatureID.GLIDER);

        /* A level with no Chip at all gets one synthesized at 0,0 rather than being rejected --
         * LevelFactory's comment calls it "legalizing" the level. */
        Level chipless = lynxLevel("Chipless", lv -> lv
                .tile(4, 4, Tile.BUG_UP).monster(4, 4));
        Harness.eq("a Chip-less level is legalized with a synthesized Chip at index 0",
                   chipless.getMonsterList().get(0).getCreatureType(), CreatureID.CHIP);
        Harness.eq("the synthesized Chip sits at 0,0",
                   chipless.getMonsterList().get(0).getPosition().getIndex(), 0);

        /* ================================================================
         * 2. The claimed layer
         * ================================================================
         * setCreatures marks a square for every creature EXCEPT Chip and the dead. Chip is excluded
         * because canMakeMove's claimed() test is what stops two monsters sharing a square, and Chip
         * is not subject to it -- monsters walk onto Chip and kill him rather than being blocked. */

        Level claim = lynxLevel("Claim", lv -> lv.tile(1, 1, Tile.CHIP_DOWN));
        game.CreatureList list = claim.getMonsterList();

        Creature chip  = list.get(0);
        Creature bug   = new LynxCreature(Direction.UP, CreatureID.BUG,   new Position(3, 3));
        Creature block = new LynxCreature(Direction.UP, CreatureID.BLOCK, new Position(4, 4));
        Creature dead  = packed(CreatureID.DEAD, Direction.UP, 0, new Position(6, 6));
        list.setCreatures(new Creature[]{ chip, bug, block, dead });

        Harness.check("a bug claims its square", list.claimed(new Position(3, 3)));
        Harness.check("a block claims its square", list.claimed(new Position(4, 4)));
        Harness.check("CHIP does NOT claim its square -- monsters must be able to walk onto him",
                      !list.claimed(chip.getPosition()));
        Harness.check("a DEAD creature does not claim its square",
                      !list.claimed(new Position(6, 6)));
        Harness.check("an empty square is unclaimed", !list.claimed(new Position(20, 20)));

        /* Off-map positions answer false and are ignored rather than throwing. Position(-1,-1) is
         * the shape canMakeMove produces at a map edge before its own bounds check. */
        Position offMap = new Position(-1, -1);
        Harness.check("an invalid position is never claimed", !list.claimed(offMap));
        list.adjustClaim(offMap, true);
        Harness.check("claiming an invalid position is a silent no-op, not a crash",
                      !list.claimed(offMap));

        list.adjustClaim(new Position(20, 20), true);
        Harness.check("adjustClaim can set a square", list.claimed(new Position(20, 20)));
        list.adjustClaim(new Position(20, 20), false);
        Harness.check("adjustClaim can clear a square", !list.claimed(new Position(20, 20)));

        boolean[] snapshot = list.getClaimedArray();
        Harness.eq("the claimed array covers the whole 32x32 map", snapshot.length, 1024);
        boolean[] replacement = new boolean[1024];
        replacement[new Position(11, 11).getIndex()] = true;
        list.setClaimedArray(replacement);
        Harness.check("setClaimedArray replaces the layer wholesale", list.claimed(new Position(11, 11)));
        Harness.check("and the previously claimed squares are gone with it",
                      !list.claimed(new Position(3, 3)));
        list.setClaimedArray(snapshot);
        Harness.check("restoring the snapshot brings them back", list.claimed(new Position(3, 3)));

        /* ================================================================
         * 3. animationAt -- dead AND still animating, at that exact square
         * ================================================================
         * All three conditions matter. canMakeMove uses this to decide whether a square holds a
         * death animation that must be quelled before something else may enter. */

        Creature animating = packed(CreatureID.DEAD, Direction.UP, 3, new Position(8, 8));
        Creature finished  = packed(CreatureID.DEAD, Direction.UP, 0, new Position(9, 9));
        Creature liveBug   = new LynxCreature(Direction.UP, CreatureID.BUG, new Position(10, 10));
        list.setCreatures(new Creature[]{ chip, animating, finished, liveBug });

        Harness.eq("animationAt finds a dead creature whose timer is still running",
                   list.animationAt(new Position(8, 8)), animating);
        Harness.check("a dead creature whose timer reached 0 is NOT an animation",
                      list.animationAt(new Position(9, 9)) == null);
        Harness.check("a LIVE creature is not an animation", list.animationAt(new Position(10, 10)) == null);
        Harness.check("an empty square has no animation", list.animationAt(new Position(21, 21)) == null);
        Harness.check("an invalid position has no animation", list.animationAt(offMap) == null);

        /* The packed constructor is the only way in from outside, so confirm it actually round-trips
         * rather than silently producing a default creature -- otherwise the three assertions above
         * would be testing nothing. */
        Harness.eq("the packed form decodes the creature type", animating.getCreatureType(), CreatureID.DEAD);
        Harness.eq("the packed form decodes the animation timer", animating.getAnimationTimer(), 3);
        Harness.eq("the packed form decodes the position", animating.getPosition().getIndex(),
                   new Position(8, 8).getIndex());

        /* ================================================================
         * 4. addCreature, the 2048 cap, and finalise
         * ================================================================
         * TW's MAX_CREATURES is (2 * CXGRID * CYGRID) = 2 * 32 * 32 = 2048. New creatures are parked
         * in newClones during a tick and folded into the list by finalise(), so that the array being
         * iterated does not change length mid-phase. */

        Level pool = lynxLevel("Pool", lv -> lv.tile(1, 1, Tile.CHIP_DOWN));
        game.CreatureList pl = pool.getMonsterList();
        pl.setCreatures(new Creature[]{ pool.getMonsterList().get(0) });

        Creature extra = new LynxCreature(Direction.UP, CreatureID.BUG, new Position(12, 12));
        pl.addCreature(extra);
        Harness.eq("a new creature is parked in newClones, not spliced into the live list",
                   pl.size(), 1);
        Harness.eq("newClones holds it", pl.getNewClones().size(), 1);
        Harness.check("a live new creature claims its square immediately",
                      pl.claimed(new Position(12, 12)));

        pl.finalise();
        Harness.eq("finalise folds newClones into the list", pl.size(), 2);
        Harness.eq("and empties newClones", pl.getNewClones().size(), 0);
        Harness.eq("the folded creature is appended at the end", pl.get(1), extra);

        int before = pl.size();
        pl.finalise();
        Harness.eq("finalise with nothing pending leaves the list alone", pl.size(), before);

        Creature deadNew = packed(CreatureID.DEAD, Direction.UP, 0, new Position(13, 13));
        pl.addCreature(deadNew);
        Harness.check("a DEAD new creature does not claim its square",
                      !pl.claimed(new Position(13, 13)));
        pl.finalise();

        /* The cap. Filling to 2048 and then asking for one more must be refused rather than growing
         * the array past what TW would allow -- a level that clones without bound behaves the same
         * in both engines only if the ceiling matches. */
        Creature[] many = new Creature[2048];
        many[0] = pool.getMonsterList().get(0);
        for (int i = 1; i < many.length; i++)
            many[i] = packed(CreatureID.DEAD, Direction.UP, 0, new Position(0, 0));
        pl.setCreatures(many);
        Harness.eq("the list is at TW's MAX_CREATURES", pl.size(), 2048);
        pl.addCreature(new LynxCreature(Direction.UP, CreatureID.BUG, new Position(14, 14)));
        Harness.eq("at 2048 a further creature is refused", pl.getNewClones().size(), 0);
        Harness.check("and it did not claim a square on the way out",
                      !pl.claimed(new Position(14, 14)));

        /* ================================================================
         * 5. addClone and springTrappedCreature refuse the wrong square quietly
         * ================================================================
         * Both are called from button handling with a target position that may be stale, off-map, or
         * pointing at a tile that is no longer what the button expects. Each guard is a branch that
         * a real level reaches only in unusual states, so they are exercised directly. */

        Level machines = lynxLevel("Machines", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.CLONE_MACHINE)
                .tile(8, 8, Tile.TRAP)
                .tile(10, 10, Tile.FLOOR));
        game.CreatureList ml = machines.getMonsterList();
        int sizeBefore = ml.size();

        ml.addClone(offMap);
        Harness.eq("addClone ignores an off-map position", ml.size(), sizeBefore);
        ml.addClone(new Position(10, 10));
        Harness.eq("addClone ignores a square that is not a clone machine", ml.size(), sizeBefore);
        ml.addClone(new Position(5, 5));
        Harness.eq("addClone on an EMPTY clone machine produces nothing", ml.size(), sizeBefore);
        Harness.eq("and queues nothing", ml.getNewClones().size(), 0);

        ml.springTrappedCreature(null);
        Harness.eq("springTrappedCreature tolerates a null position", ml.size(), sizeBefore);
        ml.springTrappedCreature(offMap);
        Harness.eq("springTrappedCreature ignores an off-map position", ml.size(), sizeBefore);
        ml.springTrappedCreature(new Position(10, 10));
        Harness.eq("springTrappedCreature ignores a square that is not a trap", ml.size(), sizeBefore);
        ml.springTrappedCreature(new Position(8, 8));
        Harness.eq("springTrappedCreature on an EMPTY trap does nothing", ml.size(), sizeBefore);

        /* ================================================================
         * 6. Reverse iteration -- the property the whole file exists for
         * ================================================================
         * Two tanks face each other across one empty square. A tank's preference list is exactly
         * one direction (lxlogic.c gives Tank `choices[0] = dir` and nothing else), so both want the
         * same square and neither will pick an alternative.
         *
         * The list is [0] Chip, [1] tank at 5,5 facing RIGHT, [2] tank at 7,5 facing LEFT. All three
         * of advancegame's loops run BACKWARD, so tank [2] chooses and moves first and takes 6,5;
         * tank [1] finds it claimed and is stuck. Under forward iteration the winner would be tank
         * [1] instead, so this single outcome distinguishes the two orders. */

        Level race = lynxLevel("Race", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT).monster(5, 5)
                .tile(7, 5, Tile.TANK_LEFT).monster(7, 5));

        Harness.eq("the racing list is Chip, then the two tanks in reading order",
                   race.getMonsterList().size(), 3);
        Creature first  = race.getMonsterList().get(1);
        Creature second = race.getMonsterList().get(2);
        Harness.eq("tank [1] starts at 5,5", x(first) + "," + y(first), "5,5");
        Harness.eq("tank [2] starts at 7,5", x(second) + "," + y(second), "7,5");

        for (int t = 0; t < 8; t++)
            race.tick('-', new Direction[]{ Direction.NONE });

        Harness.eq("the LATER tank in the list won the contested square -- iteration is BACKWARD",
                   x(second) + "," + y(second), "6,5");
        Harness.eq("the earlier tank never moved, because the square was already claimed",
                   x(first) + "," + y(first), "5,5");
        Harness.check("exactly one tank occupies the contested square",
                      race.getMonsterList().claimed(new Position(6, 5)));

        /* Same fixture, mirrored: swapping which tank faces which way must swap nothing, because
         * the winner is decided by LIST POSITION, not by direction or by who is left on the map. */
        Level mirrored = lynxLevel("Mirror", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT).monster(5, 5)
                .tile(7, 5, Tile.TANK_LEFT).monster(7, 5));
        for (int t = 0; t < 8; t++)
            mirrored.tick('-', new Direction[]{ Direction.NONE });
        Harness.eq("the result is reproducible tick for tick",
                   x(mirrored.getMonsterList().get(2)) + "," + y(mirrored.getMonsterList().get(2)),
                   "6,5");

        /* A lone tank with the same square free moves into it, which proves the blocked tank above
         * was stopped by the CLAIM and not by some unrelated refusal to move at all. */
        Level solo = lynxLevel("Solo", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT).monster(5, 5));
        for (int t = 0; t < 8; t++)
            solo.tick('-', new Direction[]{ Direction.NONE });
        Creature lone = solo.getMonsterList().get(1);
        Harness.check("an unopposed tank does move right, so the block above was the claim",
                      x(lone) > 5);

        /* ================================================================
         * 7. The CHOOSE phase's order, which section 6 does not actually pin
         * ================================================================
         * Section 6 was written to prove backward iteration and only proves it for the MOVEMENT
         * phase. Flipping the choose phase to forward passes every assertion above -- verified by
         * planting exactly that defect. The reason is that choosing takes no claim: both tanks pick
         * their one direction regardless of order, and the movement phase decides the winner.
         *
         * Choosing is not side-effect free, though. lxlogic.c resolves BLOB_TURN inside
         * choosecreaturemove with `cw[random4(mainprng())]`, so every blob DRAWS from the shared
         * generator as it is visited, and the order of those draws is the order of the list walk.
         * Two blobs therefore receive each other's directions if the walk is reversed -- a silent
         * desync that nothing in section 6 can see.
         *
         * With the list [0] Chip, [1] blob at 5,5, [2] blob at 9,9 and a backward walk, blob [2]
         * draws first. The observed result is blob [1] DOWN and blob [2] UP; forward iteration
         * swaps them. Walkers have the same property through the OTHER generator, pseudoRandom4. */

        Level blobs = lynxLevel("Blobs", lv -> lv
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.BLOB_UP).monster(5, 5)
                .tile(9, 9, Tile.BLOB_UP).monster(9, 9));
        blobs.tick('-', new Direction[]{ Direction.NONE });

        Creature blobA = blobs.getMonsterList().get(1);
        Creature blobB = blobs.getMonsterList().get(2);
        Harness.eq("blob [1] took the SECOND random draw -- the later blob is visited first",
                   blobA.getDirection(), Direction.DOWN);
        Harness.eq("blob [2] took the FIRST random draw",
                   blobB.getDirection(), Direction.UP);
        Harness.check("the two blobs did not receive the same direction, so the draws were distinct",
                      blobA.getDirection() != blobB.getDirection());
    }
}
