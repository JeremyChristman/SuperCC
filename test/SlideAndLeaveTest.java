import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.RNG;
import game.Ruleset;
import game.Step;
import game.Tile;
import game.MS.MSCreature;
import io.DatParser;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Sliding, and leaving a square -- the last two pieces of movement legality.
 *
 * TWO ORACLES, both from mslogic.c.
 *
 * 1. WHERE A SLIDE SENDS YOU, from getslidedir() and icewallturn():
 *
 *      getslidedir:  Slide_North->NORTH  Slide_West->WEST  Slide_South->SOUTH
 *                    Slide_East->EAST    Slide_Random-> 1 << random4(mainprng())
 *
 *      icewallturn:  IceWall_Northeast: SOUTH->EAST, WEST->NORTH, else unchanged
 *                    IceWall_Southwest: NORTH->WEST, EAST->SOUTH, else unchanged
 *                    IceWall_Northwest: SOUTH->WEST, EAST->NORTH, else unchanged
 *                    IceWall_Southeast: NORTH->EAST, WEST->SOUTH, else unchanged
 *
 *    The random force floor is worth a second look: TW takes `1 << random4()` over the compass
 *    bits NORTH 1, WEST 2, SOUTH 4, EAST 8, while SuperCC takes `fromOrdinal(random4())` over the
 *    ordinals UP 0, LEFT 1, DOWN 2, RIGHT 3. Different arithmetic, identical mapping -- 0 is north
 *    in both, 1 west, 2 south, 3 east -- which is why the shared RNG stream produces the same
 *    slide in both engines.
 *
 * 2. WHETHER YOU MAY LEAVE, from the switch above canmakemove()'s entry branch:
 *
 *      case Wall_North:     if (dir == NORTH) return FALSE;
 *      case Wall_West:      if (dir == WEST)  return FALSE;
 *      case Wall_South:     if (dir == SOUTH) return FALSE;
 *      case Wall_East:      if (dir == EAST)  return FALSE;
 *      case Wall_Southeast: if (dir & (SOUTH | EAST)) return FALSE;
 *      case Beartrap:       if (!(cr->state & CS_RELEASED)) return FALSE;
 *
 *    Note this is the MIRROR of the entry rule in CanEnterTest: a north thin wall refuses entry
 *    travelling SOUTH and refuses exit travelling NORTH. Same wall, two sides. Getting one of the
 *    two tables and not the other is a classic way to make a level nearly right.
 */
public class SlideAndLeaveTest {

    public static void main(String[] args) {
        System.exit(Harness.run("SlideAndLeaveTest", SlideAndLeaveTest::body));
    }

    private static final Direction[] DIRS = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};

    private static MSCreature loose(CreatureID type) {
        return new MSCreature(Direction.UP, type, new Position(10, 10));
    }

    private static Direction slide(Direction travelling, Tile tile, RNG rng) {
        return loose(CreatureID.GLIDER).getSlideDirection(travelling, tile, rng, true);
    }

    private static void body() throws Exception {

        RNG rng = new RNG(0, 0, 0);

        Harness.section("1. a fixed force floor sends you its own way, whatever you were doing");
        /* getslidedir ignores the incoming direction entirely for the four fixed slides. A version
         * that preserved momentum on a head-on entry would look right in most levels and wrong in
         * the ones built around bouncing. */
        for (Direction incoming : DIRS) {
            Harness.eq("a north force floor sends you north (entered " + incoming + ")",
                       slide(incoming, Tile.FF_UP, rng), Direction.UP);
            Harness.eq("a west force floor sends you west (entered " + incoming + ")",
                       slide(incoming, Tile.FF_LEFT, rng), Direction.LEFT);
            Harness.eq("a south force floor sends you south (entered " + incoming + ")",
                       slide(incoming, Tile.FF_DOWN, rng), Direction.DOWN);
            Harness.eq("an east force floor sends you east (entered " + incoming + ")",
                       slide(incoming, Tile.FF_RIGHT, rng), Direction.RIGHT);
        }

        Harness.section("2. ice corners deflect exactly two directions and pass the rest through");
        /* icewallturn's four rows. Each corner turns the two directions that run into its walls
         * and leaves the other two alone. The two it turns are the two you are ALLOWED to arrive
         * on; the two it passes through are the ones a wall would have stopped -- see section 3,
         * which pins that relationship and is easy to get backwards. */
        Harness.eq("north-east corner: south becomes east",
                   slide(Direction.DOWN, Tile.ICE_SLIDE_NORTHEAST, rng), Direction.RIGHT);
        Harness.eq("north-east corner: west becomes north",
                   slide(Direction.LEFT, Tile.ICE_SLIDE_NORTHEAST, rng), Direction.UP);
        Harness.eq("north-east corner: north passes through",
                   slide(Direction.UP, Tile.ICE_SLIDE_NORTHEAST, rng), Direction.UP);
        Harness.eq("north-east corner: east passes through",
                   slide(Direction.RIGHT, Tile.ICE_SLIDE_NORTHEAST, rng), Direction.RIGHT);

        Harness.eq("south-west corner: north becomes west",
                   slide(Direction.UP, Tile.ICE_SLIDE_SOUTHWEST, rng), Direction.LEFT);
        Harness.eq("south-west corner: east becomes south",
                   slide(Direction.RIGHT, Tile.ICE_SLIDE_SOUTHWEST, rng), Direction.DOWN);
        Harness.eq("south-west corner: south passes through",
                   slide(Direction.DOWN, Tile.ICE_SLIDE_SOUTHWEST, rng), Direction.DOWN);

        Harness.eq("north-west corner: south becomes west",
                   slide(Direction.DOWN, Tile.ICE_SLIDE_NORTHWEST, rng), Direction.LEFT);
        Harness.eq("north-west corner: east becomes north",
                   slide(Direction.RIGHT, Tile.ICE_SLIDE_NORTHWEST, rng), Direction.UP);
        Harness.eq("north-west corner: west passes through",
                   slide(Direction.LEFT, Tile.ICE_SLIDE_NORTHWEST, rng), Direction.LEFT);

        Harness.eq("south-east corner: north becomes east",
                   slide(Direction.UP, Tile.ICE_SLIDE_SOUTHEAST, rng), Direction.RIGHT);
        Harness.eq("south-east corner: west becomes south",
                   slide(Direction.LEFT, Tile.ICE_SLIDE_SOUTHEAST, rng), Direction.DOWN);
        Harness.eq("south-east corner: east passes through",
                   slide(Direction.RIGHT, Tile.ICE_SLIDE_SOUTHEAST, rng), Direction.RIGHT);

        Harness.section("3. the directions a corner ADMITS are exactly the ones it DEFLECTS");
        /* A consistency check across the two tables rather than against Tile World, and the
         * relationship is the opposite of the intuitive one -- I got it backwards first and this
         * assertion caught it.
         *
         * A north-east corner has its walls on the north and east sides. You may enter it
         * travelling SOUTH or WEST (movelaws {SOUTH|WEST}), and those are precisely the two
         * directions icewallturn bends: south becomes east, west becomes north. The two it leaves
         * alone, north and east, are the two you could never have arrived on, because they would
         * have crossed a wall.
         *
         * So: admitted == deflected, refused == passed through. If someone rotated one table and
         * not the other, a creature would enter a corner and be sent straight through the wall. */
        MSCreature probe = loose(CreatureID.GLIDER);
        Tile[] corners = {Tile.ICE_SLIDE_NORTHEAST, Tile.ICE_SLIDE_SOUTHWEST,
                          Tile.ICE_SLIDE_NORTHWEST, Tile.ICE_SLIDE_SOUTHEAST};
        boolean consistent = true;
        StringBuilder mismatch = new StringBuilder();
        for (Tile corner : corners) {
            for (Direction d : DIRS) {
                boolean deflected = slide(d, corner, rng) != d;
                boolean mayEnter = probe.canEnter(d, corner);
                if (deflected != mayEnter) {
                    consistent = false;
                    mismatch.append(' ').append(corner).append('/').append(d);
                }
            }
        }
        Harness.check("every corner bends exactly the directions it admits"
                      + (consistent ? "" : " [mismatched:" + mismatch + "]"), consistent);

        Harness.section("4. plain ice and other terrain leave your direction alone");
        /* Only the four corners turn you. Plain ice keeps you going, and a trap or ordinary floor
         * returns whatever you arrived with. */
        for (Direction d : DIRS) {
            Harness.eq("plain ice keeps " + d, slide(d, Tile.ICE, rng), d);
            Harness.eq("a trap keeps " + d, slide(d, Tile.TRAP, rng), d);
            Harness.eq("plain floor keeps " + d, slide(d, Tile.FLOOR, rng), d);
        }

        Harness.section("5. a random force floor draws once, and maps the draw the same way TW does");
        /* TW: 1 << random4() over NORTH 1, WEST 2, SOUTH 4, EAST 8.
         * SuperCC: fromOrdinal(random4()) over UP 0, LEFT 1, DOWN 2, RIGHT 3.
         * The arithmetic differs; the mapping must not. Checked by driving the RNG to a known
         * state and comparing against the ordinal the shared LCG will produce. */
        Direction[] byOrdinal = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};
        boolean mappingOk = true, rangeOk = true;
        boolean[] seen = new boolean[4];
        RNG r5 = new RNG(0, 0, 0);
        r5.setCurrentValue(12345);
        for (int i = 0; i < 400; i++) {
            int expectedDraw = ((r5.getCurrentValue() * 1103515245 + 12345) & 0x7FFFFFFF) >>> 29;
            Direction got = loose(CreatureID.CHIP).getSlideDirection(Direction.UP, Tile.FF_RANDOM, r5, true);
            if (got != byOrdinal[expectedDraw]) mappingOk = false;
            boolean cardinal = false;
            for (Direction d : DIRS) if (d == got) cardinal = true;
            if (!cardinal) rangeOk = false;
            for (int k = 0; k < 4; k++) if (got == byOrdinal[k]) seen[k] = true;
        }
        Harness.check("draw 0/1/2/3 maps to north/west/south/east, as TW's 1<<draw does", mappingOk);
        Harness.check("the result is always a cardinal direction", rangeOk);
        Harness.check("all four directions occur", seen[0] && seen[1] && seen[2] && seen[3]);

        Harness.section("6. advanceRFF=false PEEKS without consuming the shared stream");
        /* SuperCC-specific, and NOT a Tile World comparison: TW's getslidedir always draws.
         * SuperCC needs to ask "where would this send me" during lookahead without spending a
         * draw, so it saves and restores the generator. The engines share one stream, so a peek
         * that consumed would desynchronize them on the very next creature. */
        RNG r6 = new RNG(0, 0, 0);
        r6.setCurrentValue(999);
        int before = r6.getCurrentValue();
        Direction peeked = loose(CreatureID.CHIP).getSlideDirection(Direction.UP, Tile.FF_RANDOM, r6, false);
        Harness.eq("a peek leaves the generator exactly where it was", r6.getCurrentValue(), before);
        Direction taken = loose(CreatureID.CHIP).getSlideDirection(Direction.UP, Tile.FF_RANDOM, r6, true);
        Harness.eq("and the real draw returns what the peek promised", taken, peeked);
        Harness.check("which DID advance the generator", r6.getCurrentValue() != before);

        Harness.section("7. thin walls block LEAVING through their own side -- the mirror of entry");
        /* TW refuses exit when dir equals the wall's own side, where entry is refused when dir is
         * the opposite. A north wall stops you walking north out of the cell, and stops anything
         * walking south into it. The two tables must be mirrors; having only one gives a wall that
         * is solid from one side and open from the other. */
        Path d7 = Harness.tempDir("scc-leave-");
        DatBuilder b7 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b7.level().title("Leave")
          .tile(5, 5, Tile.BUG_UP).under(5, 5, Tile.THIN_WALL_UP).monster(5, 5)
          .tile(10, 5, Tile.BUG_UP).under(10, 5, Tile.THIN_WALL_LEFT).monster(10, 5)
          .tile(15, 5, Tile.BUG_UP).under(15, 5, Tile.THIN_WALL_DOWN).monster(15, 5)
          .tile(20, 5, Tile.BUG_UP).under(20, 5, Tile.THIN_WALL_RIGHT).monster(20, 5)
          .tile(25, 5, Tile.BUG_UP).under(25, 5, Tile.THIN_WALL_DOWN_RIGHT).monster(25, 5)
          .end();
        Level l7 = new DatParser(b7.writeTo(d7, "leave.dat").toFile())
                .parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
        Harness.eq("five bugs were listed", l7.getMonsterList().size(), 5);

        checkLeave(l7, 0, "a north thin wall", Direction.UP);
        checkLeave(l7, 1, "a west thin wall", Direction.LEFT);
        checkLeave(l7, 2, "a south thin wall", Direction.DOWN);
        checkLeave(l7, 3, "an east thin wall", Direction.RIGHT);

        MSCreature corner = (MSCreature) l7.getMonsterList().get(4);
        Harness.check("a south-east thin wall blocks leaving downward",
                      !canLeaveVia(l7, corner, Direction.DOWN));
        Harness.check("and blocks leaving rightward",
                      !canLeaveVia(l7, corner, Direction.RIGHT));
        Harness.check("but allows leaving upward", canLeaveVia(l7, corner, Direction.UP));
        Harness.check("and leftward", canLeaveVia(l7, corner, Direction.LEFT));

        Harness.section("8. a beartrap holds you until its button is pressed");
        /* TW: `if (!(cr->state & CS_RELEASED)) return FALSE`. SuperCC asks the level whether the
         * trap is open, which is the same question by a different route -- and it means the brown
         * button and the leave check are wired to each other, which ButtonTest checks from the
         * button's end and this checks from the creature's. */
        Path d8 = Harness.tempDir("scc-trapleave-");
        DatBuilder b8 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b8.level().title("Trapped")
          .tile(8, 8, Tile.BUG_UP).under(8, 8, Tile.TRAP).monster(8, 8)
          .tile(2, 2, Tile.BUTTON_BROWN)
          .trap(2, 2, 8, 8)
          .end();
        Level l8 = new DatParser(b8.writeTo(d8, "trapped.dat").toFile())
                .parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
        MSCreature trapped = (MSCreature) l8.getMonsterList().get(0);
        Harness.check("a creature in a closed trap cannot leave in any direction",
                      !canLeaveVia(l8, trapped, Direction.UP)
                      && !canLeaveVia(l8, trapped, Direction.LEFT)
                      && !canLeaveVia(l8, trapped, Direction.DOWN)
                      && !canLeaveVia(l8, trapped, Direction.RIGHT));
        l8.getBrownButtons().getList(new Position(2, 2)).get(0).press(l8);
        Harness.check("pressing the brown button lets it out", canLeaveVia(l8, trapped, Direction.UP));
    }

    /** A thin wall on side `blocked` must refuse exactly that direction and permit the other three. */
    private static void checkLeave(Level level, int creatureIndex, String what, Direction blocked) {
        MSCreature c = (MSCreature) level.getMonsterList().get(creatureIndex);
        Harness.check(what + " blocks leaving " + blocked, !canLeaveVia(level, c, blocked));
        for (Direction d : DIRS) {
            if (d == blocked) continue;
            Harness.check(what + " permits leaving " + d, canLeaveVia(level, c, d));
        }
    }

    /**
     * Whether the creature may leave its own square in this direction.
     *
     * canLeave is private, so this goes through the public canMakeMove and neutralizes the entry
     * half by naming a destination the creature can always enter -- a plain floor cell far away.
     * What is left is the leave test.
     */
    private static boolean canLeaveVia(Level level, MSCreature c, Direction d) {
        Position openFloor = new Position(30, 30);
        return c.canMakeMove(d, openFloor, false, false, false, false);
    }
}
