import game.CreatureID;
import game.Direction;
import game.Position;
import game.RNG;
import game.MS.MSCreature;

/**
 * Creature move ORDER -- transcribed from Tile World's choosecreaturemove().
 *
 * THE ORACLE
 * ----------
 * The reference is `mslogic.c` in the maintainer's own Tile World fork, which is the engine that
 * provably replays his solutions. Its choosecreaturemove() builds a four-slot preference list per
 * creature, and the order of that list IS the movement rule:
 *
 *     Tank:       dir
 *     Ball:       dir, back
 *     Glider:     dir, left,  right, back
 *     Fireball:   dir, right, left,  back
 *     Bug:        left,  dir, right, back
 *     Paramecium: right, dir, left,  back
 *     Walker:     dir, then randomp3 over {left, back, right}
 *     Blob:       randomp4 over {dir, left, back, right}
 *     Teeth:      toward Chip, the LARGER axis first
 *
 * left/right/back are TW's own macros, and this file reimplements them from the compass rather
 * than calling SuperCC's Direction.turn():
 *
 *     NORTH 1, WEST 2, SOUTH 4, EAST 8                                          [gen.h]
 *     left(dir)  = ((dir << 1) | (dir >> 3)) & 15                               [logic.h]
 *     back(dir)  = ((dir << 2) | (dir >> 2)) & 15
 *     right(dir) = ((dir << 3) | (dir >> 1)) & 15
 *
 * That independence is the point. If SuperCC's turn() were used to build the expectations, an edit
 * to turn() would move the expectations with it and this file would pass regardless.
 *
 * ⚠ ONE CAVEAT WORTH KNOWING BEFORE READING A FAILURE HERE. Tile World was modified 79 times to
 * match SuperCC, not the other way round, so the oracle is that fork specifically. If an assertion
 * in this file ever fails, do NOT assume the test is wrong: a genuine divergence between the two
 * engines is a desync, and finding one is more valuable than the assertion was.
 *
 * NOT covered here, deliberately: choosecreaturemove has a SECOND switch for a creature standing on
 * a clone machine or beartrap, where Tank/Ball/Glider/Fireball/Walker get only `dir` with no
 * alternatives and Bug/Paramecium/Teeth take controllerdir(). SuperCC's getDirectionPriority has no
 * such branch -- it handles that case elsewhere -- so comparing them here would be comparing
 * architectures rather than behavior. That belongs with the movement loop, not the priority table.
 */
public class CreatureMoveTest {

    public static void main(String[] args) {
        System.exit(Harness.run("CreatureMoveTest", CreatureMoveTest::body));
    }

    /* ---- Tile World's compass, reimplemented from gen.h / logic.h ---- */

    private static final int N = 1, W = 2, S = 4, E = 8;

    private static int twBits(Direction d) {
        switch (d) {
            case UP:    return N;
            case LEFT:  return W;
            case DOWN:  return S;
            case RIGHT: return E;
            default:    return 0;
        }
    }

    private static Direction fromTw(int bits) {
        switch (bits) {
            case N: return Direction.UP;
            case W: return Direction.LEFT;
            case S: return Direction.DOWN;
            case E: return Direction.RIGHT;
            default: return Direction.NONE;
        }
    }

    private static Direction left(Direction d)  { int b = twBits(d); return fromTw(((b << 1) | (b >> 3)) & 15); }
    private static Direction back(Direction d)  { int b = twBits(d); return fromTw(((b << 2) | (b >> 2)) & 15); }
    private static Direction right(Direction d) { int b = twBits(d); return fromTw(((b << 3) | (b >> 1)) & 15); }

    private static final Direction[] FACINGS = {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT};

    private static String show(Direction[] ds) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ds.length; i++) { if (i > 0) b.append(','); b.append(ds[i]); }
        return b.toString();
    }

    private static MSCreature creature(CreatureID type, Direction facing) {
        return new MSCreature(facing, type, new Position(10, 10));
    }

    /** A stand-in Chip. Only Teeth consult it, and only for its position. */
    private static MSCreature chipAt(int x, int y) {
        return new MSCreature(Direction.UP, CreatureID.CHIP, new Position(x, y));
    }

    private static Direction[] priority(CreatureID type, Direction facing, MSCreature chip, RNG rng) {
        return creature(type, facing).getDirectionPriority(chip, rng);
    }

    private static void body() throws Exception {

        RNG rng = new RNG(0, 0, 0);
        MSCreature chip = chipAt(10, 10);

        Harness.section("1. the six deterministic creatures, every facing");
        /* Twenty-four orderings, each built from TW's own macros. A creature whose list is right
         * for one facing and wrong for another -- which is what a hardcoded compass rather than a
         * relative turn produces -- shows up here and in almost no other test. */
        for (Direction f : FACINGS) {
            Harness.eq("tank facing " + f + " tries only straight ahead",
                       show(priority(CreatureID.TANK_MOVING, f, chip, rng)), show(new Direction[]{f}));
        }
        for (Direction f : FACINGS) {
            Harness.eq("ball facing " + f + " tries ahead then back",
                       show(priority(CreatureID.PINK_BALL, f, chip, rng)),
                       show(new Direction[]{f, back(f)}));
        }
        for (Direction f : FACINGS) {
            Harness.eq("glider facing " + f + " tries ahead, left, right, back",
                       show(priority(CreatureID.GLIDER, f, chip, rng)),
                       show(new Direction[]{f, left(f), right(f), back(f)}));
        }
        for (Direction f : FACINGS) {
            Harness.eq("fireball facing " + f + " tries ahead, RIGHT, left, back",
                       show(priority(CreatureID.FIREBALL, f, chip, rng)),
                       show(new Direction[]{f, right(f), left(f), back(f)}));
        }
        for (Direction f : FACINGS) {
            Harness.eq("bug facing " + f + " tries LEFT first, then ahead, right, back",
                       show(priority(CreatureID.BUG, f, chip, rng)),
                       show(new Direction[]{left(f), f, right(f), back(f)}));
        }
        for (Direction f : FACINGS) {
            Harness.eq("paramecium facing " + f + " tries RIGHT first, then ahead, left, back",
                       show(priority(CreatureID.PARAMECIUM, f, chip, rng)),
                       show(new Direction[]{right(f), f, left(f), back(f)}));
        }

        Harness.section("2. glider and fireball are mirror images, and bug and paramecium are too");
        /* A cheap structural check that catches a copy-paste between the pairs -- the likeliest way
         * these four ever get swapped, and one that section 1 would also catch but less legibly. */
        Direction f = Direction.UP;
        Direction[] glider = priority(CreatureID.GLIDER, f, chip, rng);
        Direction[] fireball = priority(CreatureID.FIREBALL, f, chip, rng);
        Harness.check("glider prefers left where fireball prefers right",
                      glider[1] == left(f) && fireball[1] == right(f));
        Direction[] bug = priority(CreatureID.BUG, f, chip, rng);
        Direction[] para = priority(CreatureID.PARAMECIUM, f, chip, rng);
        Harness.check("bug leads with left where paramecium leads with right",
                      bug[0] == left(f) && para[0] == right(f));

        Harness.section("3. a stationary tank offers no moves at all");
        /* Not "it prefers to stay" -- an EMPTY list. A single fallback direction here would make
         * stationary tanks drift. */
        for (Direction d : FACINGS) {
            Harness.eq("stationary tank facing " + d + " has an empty priority list",
                       priority(CreatureID.TANK_STATIONARY, d, chip, rng).length, 0);
        }

        Harness.section("4. teeth chase Chip along the LARGER axis first");
        /* TW computes both deltas, makes them positive, and takes the horizontal first only when
         * it is strictly greater:  if (x > y) { m, n } else { n, m }.
         * So a TIE puts the vertical first -- the one case a plausible implementation gets wrong. */
        MSCreature teeth = creature(CreatureID.TEETH, Direction.UP);

        Direction[] farRight = teeth.getDirectionPriority(chipAt(20, 11), rng);   // dx=10, dy=1
        Harness.eq("chip mostly to the east: east first, then south",
                   show(farRight), show(new Direction[]{Direction.RIGHT, Direction.DOWN}));

        Direction[] farUp = teeth.getDirectionPriority(chipAt(11, 2), rng);       // dx=1, dy=8
        Harness.eq("chip mostly to the north: north first, then east",
                   show(farUp), show(new Direction[]{Direction.UP, Direction.RIGHT}));

        Direction[] tie = teeth.getDirectionPriority(chipAt(15, 15), rng);        // dx=5, dy=5
        Harness.eq("on a perfect diagonal the VERTICAL axis wins the tie",
                   show(tie), show(new Direction[]{Direction.DOWN, Direction.RIGHT}));

        Direction[] straightUp = teeth.getDirectionPriority(chipAt(10, 4), rng);  // dx=0
        Harness.eq("directly north: north, and no horizontal component",
                   show(straightUp), show(new Direction[]{Direction.UP, Direction.NONE}));

        Direction[] onTop = teeth.getDirectionPriority(chipAt(10, 10), rng);      // same cell
        Harness.eq("standing on Chip yields no direction at all",
                   show(onTop), show(new Direction[]{Direction.NONE, Direction.NONE}));

        Harness.section("5. walkers keep straight ahead first, then shuffle the other three");
        /* TW: choices = {dir, left, back, right}, then randomp3 over choices+1 -- so slot 0 is
         * FIXED and only the last three are permuted. A walker that shuffled all four would be a
         * blob. Checked over many draws so a wrong slot cannot hide behind one lucky shuffle. */
        boolean walkerHeadOk = true, walkerTailOk = true;
        boolean[] tailSeen = new boolean[3];
        for (int i = 0; i < 300; i++) {
            Direction[] p = priority(CreatureID.WALKER, Direction.UP, chip, rng);
            if (p.length != 4 || p[0] != Direction.UP) walkerHeadOk = false;
            java.util.List<Direction> tail = java.util.Arrays.asList(p[1], p[2], p[3]);
            if (!(tail.contains(left(Direction.UP)) && tail.contains(back(Direction.UP))
                  && tail.contains(right(Direction.UP)))) walkerTailOk = false;
            if (p[1] == left(Direction.UP)) tailSeen[0] = true;
            if (p[1] == back(Direction.UP)) tailSeen[1] = true;
            if (p[1] == right(Direction.UP)) tailSeen[2] = true;
        }
        Harness.check("straight ahead is always first", walkerHeadOk);
        Harness.check("the other three are always all present", walkerTailOk);
        Harness.check("and their order really varies", tailSeen[0] && tailSeen[1] && tailSeen[2]);

        Harness.section("6. blobs shuffle all four, including straight ahead");
        /* TW: randomp4 over the whole array. The difference from a walker is exactly that slot 0
         * moves too, which is why blobs can reverse without being blocked first. */
        boolean blobSetOk = true;
        boolean[] headSeen = new boolean[4];
        Direction[] all = {Direction.UP, left(Direction.UP), back(Direction.UP), right(Direction.UP)};
        for (int i = 0; i < 400; i++) {
            Direction[] p = priority(CreatureID.BLOB, Direction.UP, chip, rng);
            if (p.length != 4) { blobSetOk = false; continue; }
            java.util.List<Direction> got = java.util.Arrays.asList(p);
            for (Direction d : all) if (!got.contains(d)) blobSetOk = false;
            for (int k = 0; k < 4; k++) if (p[0] == all[k]) headSeen[k] = true;
        }
        Harness.check("all four directions are always present", blobSetOk);
        Harness.check("and ANY of them can come first, unlike a walker",
                      headSeen[0] && headSeen[1] && headSeen[2] && headSeen[3]);

        Harness.section("7. RNG consumption per creature -- the desync-critical count");
        /* Established by the desync work: a blob draws one randomPermutation4, a walker one
         * randomPermutation3, and EVERY other creature draws nothing. The engines share one RNG
         * stream, so a single extra or missing draw by any creature on any tick cascades forever.
         * This is the assertion most likely to catch a real desync being introduced. */
        CreatureID[] noDraw = {CreatureID.BUG, CreatureID.FIREBALL, CreatureID.PINK_BALL,
                               CreatureID.GLIDER, CreatureID.PARAMECIUM, CreatureID.TANK_MOVING,
                               CreatureID.TANK_STATIONARY, CreatureID.TEETH};
        for (CreatureID id : noDraw) {
            RNG r = new RNG(0, 0, 0);
            r.setCurrentValue(12345);
            int before = r.getCurrentValue();
            priority(id, Direction.UP, chip, r);
            Harness.eq(id + " consumes no RNG", r.getCurrentValue(), before);
        }

        RNG rw = new RNG(0, 0, 0); rw.setCurrentValue(12345);
        int wBefore = rw.getCurrentValue();
        priority(CreatureID.WALKER, Direction.UP, chip, rw);
        Harness.check("WALKER consumes exactly one draw",
                      rw.getCurrentValue() == oneStep(wBefore));

        RNG rb = new RNG(0, 0, 0); rb.setCurrentValue(12345);
        int bBefore = rb.getCurrentValue();
        priority(CreatureID.BLOB, Direction.UP, chip, rb);
        Harness.check("BLOB consumes exactly one draw",
                      rb.getCurrentValue() == oneStep(bBefore));

        Harness.section("8. a sliding creature only tries ahead, then back");
        /* SuperCC-specific, and NOT a Tile World comparison: TW returns from choosecreaturemove
         * without choosing at all when CS_SLIP or CS_SLIDE is set, and lets its slip logic drive.
         * SuperCC instead answers with the two-entry bounce list. The behaviors line up; the
         * architectures do not, so this pins SuperCC's contract rather than claiming parity.
         *
         * The creatures come from a real parsed level rather than being constructed bare, because
         * setSliding maintains the level's slip list and a detached creature has no level to
         * maintain. */
        java.nio.file.Path d8 = Harness.tempDir("scc-slide-");
        DatBuilder b8 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b8.level().title("Sliders")
          .tile(4, 4, game.Tile.BUG_UP).monster(4, 4)
          .tile(6, 6, game.Tile.BUG_LEFT).monster(6, 6)
          .tile(8, 8, game.Tile.BUG_DOWN).monster(8, 8)
          .tile(10, 12, game.Tile.BUG_RIGHT).monster(10, 12)
          .end();
        game.Level lv8 = new io.DatParser(b8.writeTo(d8, "slide.dat").toFile())
                .parseLevel(1, 0, game.Step.EVEN, game.Ruleset.CURRENT, Direction.UP);
        Harness.eq("four bugs were listed", lv8.getMonsterList().size(), 4);
        for (int i = 0; i < lv8.getMonsterList().size(); i++) {
            MSCreature slider = (MSCreature) lv8.getMonsterList().get(i);
            Direction d = slider.getDirection();
            slider.setSliding(false, true);
            Harness.eq("a sliding bug facing " + d + " tries ahead then back, ignoring its type",
                       show(slider.getDirectionPriority(chip, rng)),
                       show(new Direction[]{d, back(d)}));
        }
    }

    /** One step of the shared LCG, for the consumption counts. Same formula as RngTest's oracle. */
    private static int oneStep(int value) {
        return (value * 1103515245 + 12345) & 0x7FFFFFFF;
    }
}
