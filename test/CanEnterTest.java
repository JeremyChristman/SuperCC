import game.CreatureID;
import game.Direction;
import game.Position;
import game.Tile;
import game.MS.MSCreature;

/**
 * Which tiles a creature may enter, and from which direction -- transcribed from Tile World's
 * movelaws[] table and canmakemove().
 *
 * THE ORACLE, AND ITS EXACT SHAPE
 * -------------------------------
 * mslogic.c holds a per-tile table with one direction mask per entity class:
 *
 *     static struct { unsigned char chip, block, creature; } const movelaws[] = ...
 *
 * and canmakemove() applies it, then adds one creature-specific rule on top:
 *
 *     if (!(movelaws[floor].creature & dir))  return FALSE;
 *     if (floor == Fire && (cr->id == Bug || cr->id == Walker))  return FALSE;
 *
 * SuperCC folds BOTH steps into one creature-type-aware canEnter, which is why its FIRE case reads
 * `getCreatureType() != BUG && getCreatureType() != WALKER`. Tile World's own source quotes that
 * very line in a MOD comment, so the correspondence is deliberate and documented rather than
 * coincidental.
 *
 * The direction in the mask is the direction of TRAVEL, and the thin-wall rows read the way you
 * would hope: Wall_North = {NORTH|WEST|EAST} refuses only SOUTH, because a wall on the north edge
 * is what you cross when moving south into the cell.
 *
 * ⚠ movelaws IS NOT THE WHOLE RULE, and Tile World says so itself: "movement rules are only the
 * first check; a creature may be occasionally permitted a particular type of move but still
 * prevented in a specific situation." Rows where the rest of the rule lives elsewhere -- doors,
 * sockets, the clone machine, the two revealed-wall tiles -- are handled in section 8 as SuperCC
 * contracts, explicitly NOT as Tile World comparisons. Do not extend the 1:1 rows without checking
 * canmakemove for a second condition.
 *
 * Doors and sockets are absent entirely: canEnter consults level.getKeys() and getChipsLeft() for
 * those, so they need a loaded level and belong with the movement loop rather than here.
 */
public class CanEnterTest {

    public static void main(String[] args) {
        System.exit(Harness.run("CanEnterTest", CanEnterTest::body));
    }

    private static final Direction[] DIRS = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};

    /* One representative of each of Tile World's three movelaws columns. */
    private static final CreatureID CHIP = CreatureID.CHIP;
    private static final CreatureID BLOCK = CreatureID.BLOCK;
    private static final CreatureID MONSTER = CreatureID.GLIDER;

    private static boolean canEnter(CreatureID type, Direction travelling, Tile tile) {
        return new MSCreature(Direction.UP, type, new Position(10, 10)).canEnter(travelling, tile);
    }

    /** Asserts a tile's answer for one entity class across all four directions. */
    private static void allDirs(String what, CreatureID type, Tile tile, boolean expected) {
        boolean ok = true;
        StringBuilder got = new StringBuilder();
        for (Direction d : DIRS) {
            boolean v = canEnter(type, d, tile);
            if (v != expected) ok = false;
            got.append(d).append('=').append(v).append(' ');
        }
        Harness.check(what + (ok ? "" : "  [" + got.toString().trim() + "]"), ok);
    }

    /** Asserts a tile admits exactly the listed directions, for one entity class. */
    private static void onlyDirs(String what, CreatureID type, Tile tile, Direction... allowed) {
        boolean ok = true;
        StringBuilder got = new StringBuilder();
        for (Direction d : DIRS) {
            boolean want = false;
            for (Direction a : allowed) if (a == d) want = true;
            boolean v = canEnter(type, d, tile);
            if (v != want) ok = false;
            if (v) got.append(d).append(' ');
        }
        Harness.check(what + (ok ? "" : "  [admits: " + got.toString().trim() + "]"), ok);
    }

    private static void body() throws Exception {

        Harness.section("1. thin walls block exactly the direction that crosses them");
        /* movelaws: Wall_North {NORTH|WEST|EAST}, Wall_West {NORTH|WEST|SOUTH},
         *           Wall_South {WEST|SOUTH|EAST}, Wall_East {NORTH|SOUTH|EAST}.
         * A wall on the north edge refuses SOUTHWARD travel, and so on round the compass. Getting
         * one of these mirrored is a classic desync: the level plays, and a creature rounds a
         * corner one square differently. */
        onlyDirs("a north thin wall refuses only downward travel", MONSTER, Tile.THIN_WALL_UP,
                 Direction.UP, Direction.LEFT, Direction.RIGHT);
        onlyDirs("a west thin wall refuses only rightward travel", MONSTER, Tile.THIN_WALL_LEFT,
                 Direction.UP, Direction.LEFT, Direction.DOWN);
        onlyDirs("a south thin wall refuses only upward travel", MONSTER, Tile.THIN_WALL_DOWN,
                 Direction.LEFT, Direction.DOWN, Direction.RIGHT);
        onlyDirs("an east thin wall refuses only leftward travel", MONSTER, Tile.THIN_WALL_RIGHT,
                 Direction.UP, Direction.DOWN, Direction.RIGHT);
        onlyDirs("a south-east thin wall admits only down and right", MONSTER, Tile.THIN_WALL_DOWN_RIGHT,
                 Direction.DOWN, Direction.RIGHT);

        Harness.section("2. thin walls treat Chip, blocks and monsters identically");
        /* All three movelaws columns carry the same mask on every thin-wall row. A rule that let
         * Chip through a wall a monster could not pass would be visible only in play. */
        Tile[] thin = {Tile.THIN_WALL_UP, Tile.THIN_WALL_LEFT, Tile.THIN_WALL_DOWN,
                       Tile.THIN_WALL_RIGHT, Tile.THIN_WALL_DOWN_RIGHT};
        boolean sameForAll = true;
        for (Tile t : thin) {
            for (Direction d : DIRS) {
                boolean c = canEnter(CHIP, d, t), b = canEnter(BLOCK, d, t), m = canEnter(MONSTER, d, t);
                if (c != b || b != m) sameForAll = false;
            }
        }
        Harness.check("every thin wall answers the same for all three entity classes", sameForAll);

        Harness.section("3. ice corners admit exactly the two directions that round them");
        /* movelaws: IceWall_Northwest {SOUTH|EAST}, Northeast {SOUTH|WEST},
         *           Southwest {NORTH|EAST}, Southeast {NORTH|WEST}.
         * The naming is the CORNER, and the admitted pair is the two directions that do not run
         * into it. These four are easy to rotate by one and impossible to notice without play. */
        onlyDirs("a north-west ice corner admits down and right", MONSTER, Tile.ICE_SLIDE_NORTHWEST,
                 Direction.DOWN, Direction.RIGHT);
        onlyDirs("a north-east ice corner admits down and left", MONSTER, Tile.ICE_SLIDE_NORTHEAST,
                 Direction.DOWN, Direction.LEFT);
        onlyDirs("a south-west ice corner admits up and right", MONSTER, Tile.ICE_SLIDE_SOUTHWEST,
                 Direction.UP, Direction.RIGHT);
        onlyDirs("a south-east ice corner admits up and left", MONSTER, Tile.ICE_SLIDE_SOUTHEAST,
                 Direction.UP, Direction.LEFT);

        Harness.section("4. solid walls refuse everyone, from every direction");
        /* movelaws {0,0,0}. Toggle walls in the closed state belong here too -- when open they are
         * ordinary floor, which is what makes the green button matter. */
        for (CreatureID who : new CreatureID[]{CHIP, BLOCK, MONSTER}) {
            allDirs("a wall refuses " + who, who, Tile.WALL, false);
            allDirs("a permanent invisible wall refuses " + who, who, Tile.INVISIBLE_WALL, false);
            allDirs("a closed toggle wall refuses " + who, who, Tile.TOGGLE_CLOSED, false);
        }

        Harness.section("5. open terrain admits everyone, from every direction");
        /* movelaws NWSE on all three columns. Water is on this list on purpose: entering is always
         * legal, and what happens on arrival -- drowning, or a block becoming dirt -- is a separate
         * question handled after entry. */
        Tile[] open = {Tile.FLOOR, Tile.ICE, Tile.WATER, Tile.BOMB, Tile.TRAP, Tile.TELEPORT,
                       Tile.HINT, Tile.BUTTON_GREEN, Tile.BUTTON_RED, Tile.BUTTON_BROWN,
                       Tile.BUTTON_BLUE, Tile.TOGGLE_OPEN,
                       Tile.FF_UP, Tile.FF_DOWN, Tile.FF_LEFT, Tile.FF_RIGHT};
        for (Tile t : open) {
            for (CreatureID who : new CreatureID[]{CHIP, BLOCK, MONSTER}) {
                allDirs(t + " admits " + who + " from any direction", who, t, true);
            }
        }

        Harness.section("6. gravel and random force floors refuse MONSTERS only");
        /* movelaws Gravel and Slide_Random both read {NWSE, NWSE, 0}: Chip and blocks pass,
         * creatures do not. This is the row where the three columns first disagree, so it is the
         * one that proves canEnter is genuinely entity-aware rather than a per-tile lookup. */
        allDirs("gravel admits Chip", CHIP, Tile.GRAVEL, true);
        allDirs("gravel admits a block", BLOCK, Tile.GRAVEL, true);
        allDirs("gravel refuses a monster", MONSTER, Tile.GRAVEL, false);
        allDirs("a random force floor admits Chip", CHIP, Tile.FF_RANDOM, true);
        allDirs("a random force floor admits a block", BLOCK, Tile.FF_RANDOM, true);
        allDirs("a random force floor refuses a monster", MONSTER, Tile.FF_RANDOM, false);

        Harness.section("7. dirt and the thief admit CHIP ONLY -- not even a block");
        /* movelaws Dirt and Burglar read {NWSE, 0, 0}. The block column being zero is the part
         * worth pinning: pushing a block onto dirt is illegal in MS, which is a real puzzle
         * constraint and not an oversight. */
        allDirs("dirt admits Chip", CHIP, Tile.DIRT, true);
        allDirs("dirt refuses a BLOCK", BLOCK, Tile.DIRT, false);
        allDirs("dirt refuses a monster", MONSTER, Tile.DIRT, false);
        allDirs("a thief admits Chip", CHIP, Tile.THIEF, true);
        allDirs("a thief refuses a block", BLOCK, Tile.THIEF, false);
        allDirs("a thief refuses a monster", MONSTER, Tile.THIEF, false);

        Harness.section("8. fire: the second check, which movelaws does NOT encode");
        /* movelaws[Fire] is {NWSE, NWSE, NWSE} -- the table alone permits everyone. The refusal
         * comes from the line immediately after it in canmakemove:
         *
         *     if (floor == Fire && (cr->id == Bug || cr->id == Walker))  return FALSE;
         *
         * SuperCC folds that into canEnter, and Tile World's own MOD comment quotes the resulting
         * Java line. Anyone "simplifying" canEnter toward a pure per-tile table loses exactly this
         * and nothing else -- and it is measured: Jacques #922 at ct 834 turns on it. */
        allDirs("fire admits Chip", CHIP, Tile.FIRE, true);
        allDirs("fire admits a block", BLOCK, Tile.FIRE, true);
        allDirs("fire admits a glider", MONSTER, Tile.FIRE, true);
        allDirs("fire admits a fireball", CreatureID.FIREBALL, Tile.FIRE, true);
        allDirs("fire REFUSES a bug", CreatureID.BUG, Tile.FIRE, false);
        allDirs("fire REFUSES a walker", CreatureID.WALKER, Tile.FIRE, false);
        allDirs("but a bug may still enter water", CreatureID.BUG, Tile.WATER, true);
        allDirs("and a walker may still enter water", CreatureID.WALKER, Tile.WATER, true);

        Harness.section("9. blocks are obstacles, and the exit is not for monsters");
        /* A block tile in either resting form refuses everyone -- pushing is handled by the mover,
         * not by the entry test. The exit refuses monsters so they cannot finish the level. */
        for (CreatureID who : new CreatureID[]{CHIP, BLOCK, MONSTER}) {
            allDirs("a block tile refuses " + who, who, Tile.BLOCK, false);
        }
        allDirs("the exit admits Chip", CHIP, Tile.EXIT, true);
        allDirs("the exit admits a block", BLOCK, Tile.EXIT, true);
        allDirs("the exit REFUSES a monster", MONSTER, Tile.EXIT, false);

        Harness.section("10. SuperCC contracts where the rest of the rule lives elsewhere");
        /* NOT Tile World comparisons. These are rows where movelaws is only the opening move and
         * the two engines split the remaining work differently, so asserting equality here would
         * compare architectures rather than behavior. Recorded so a future reader knows the
         * difference is understood rather than missed.
         *
         *   CLONE_MACHINE   movelaws refuses it to everyone; SuperCC's canEnter does too, and
         *                   allows Chip the exception later in tryMove. Tile World carries a MOD
         *                   for the same exception (FIX_CHIP_ONTO_CLONER).
         *   HIDDENWALL_TEMP movelaws gives Chip NWSE -- he bumps it and it becomes a wall.
         *   BLUEWALL_REAL   same shape: the bump is what reveals it.
         *                   SuperCC answers false at the entry test and resolves the reveal
         *                   elsewhere. */
        allDirs("SuperCC: a clone machine refuses even Chip at the entry test", CHIP, Tile.CLONE_MACHINE, false);
        allDirs("SuperCC: a clone machine refuses monsters", MONSTER, Tile.CLONE_MACHINE, false);
        allDirs("SuperCC: a temporary hidden wall refuses Chip at the entry test",
                CHIP, Tile.HIDDENWALL_TEMP, false);
        allDirs("SuperCC: a real blue wall refuses Chip at the entry test", CHIP, Tile.BLUEWALL_REAL, false);
        allDirs("but a FAKE blue wall admits Chip", CHIP, Tile.BLUEWALL_FAKE, true);
        allDirs("and refuses a monster", MONSTER, Tile.BLUEWALL_FAKE, false);
    }
}
