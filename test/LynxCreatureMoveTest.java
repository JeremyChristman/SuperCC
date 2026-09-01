import game.CreatureID;
import game.Direction;
import game.Position;
import game.RNG;
import game.Step;
import game.Lynx.LynxCreature;

/**
 * LYNX creature move ORDER -- transcribed from Tile World's lxlogic.c choosecreaturemove().
 *
 * WHY THIS FILE EXISTS SEPARATELY FROM CreatureMoveTest
 * -----------------------------------------------------
 * MS and Lynx are two different implementations of the same game, and `game\Lynx\**` was the least
 * covered code in the repo -- 15 of 732 branches, essentially untested, while every engine test
 * written before this one targeted MS. The two rulesets agree on most of the preference table and
 * disagree on exactly the places that matter, so testing one proves nothing about the other.
 *
 * THE ORACLE, AND ONE IMPORTANT DIFFERENCE FROM THE MS TESTS
 * ----------------------------------------------------------
 * The reference is `lxlogic.c` in the maintainer's Tile World fork. Unlike `mslogic.c`, which
 * carries 79 `MOD (Jeremy)` comments recording places Tile World was changed to match SuperCC,
 * **lxlogic.c carries none -- it is unmodified upstream Tile World.**
 *
 * That cuts both ways. It is a cleaner oracle, because nothing in it was bent toward SuperCC. But
 * it also means a failure here is NOT necessarily a test bug and NOT necessarily already-known: the
 * desync project that took the count from 135 to 0 was an MS effort, so a genuine Lynx divergence
 * would simply never have been looked for. **Treat a failure in this file as a finding, not as a
 * broken assertion.**
 *
 * The preference table, from lxlogic.c:828 choosecreaturemove():
 *
 *     Tank:       dir
 *     Ball:       dir, back
 *     Glider:     dir, left,  right, back
 *     Fireball:   dir, right, left,  back
 *     Bug:        left,  dir, right, back
 *     Paramecium: right, dir, left,  back
 *     Walker:     dir, WALKER_TURN
 *     Blob:       BLOB_TURN
 *     Teeth:      toward Chip, LARGER axis first -- but only on a teeth step
 *
 * left/right/back are TW's own macros, and this file reimplements them from the compass rather
 * than calling SuperCC's Direction.turn():
 *
 *     NORTH 1, WEST 2, SOUTH 4, EAST 8                                          [gen.h]
 *     left(dir)  = ((dir << 1) | (dir >> 3)) & 15                               [logic.h]
 *     back(dir)  = ((dir << 2) | (dir >> 2)) & 15
 *     right(dir) = ((dir << 3) | (dir >> 1)) & 15
 *
 * That independence is the point. Building the expectations out of SuperCC's own turn() would make
 * an edit to turn() move the expectations with it, and the file would pass regardless.
 *
 * WHERE LYNX AND MS GENUINELY DIVERGE, and why section 2 exists
 * -------------------------------------------------------------
 * Walker and Blob differ between the rulesets, and they differ in SHAPE, not just in values:
 *
 *     MS   Walker: dir, then a random permutation of the other three  (randomp3)
 *     MS   Blob:   a random permutation of all four                   (randomp4)
 *     Lynx Walker: dir, then ONE direction -- lynx_prng() & 3 right-turns from dir
 *     Lynx Blob:   ONE direction -- cw[random4()] over {N, E, S, W}
 *
 * SuperCC represents the Lynx forms with the pseudo-directions WALKER_TURN and BLOB_TURN, resolved
 * later rather than in getDirectionPriority. Section 2 pins that, because returning an MS-shaped
 * list here would be a silent ruleset confusion that every other test would miss.
 *
 * NOT covered here, deliberately: lxlogic.c returns early for a creature standing on a clone
 * machine or beartrap (`cr->tdir = cr->dir`), and returns without choosing when getfdir() is set.
 * SuperCC handles both outside getDirectionPriority, so comparing them here would be comparing
 * architectures rather than behavior. Those belong with the movement loop.
 */
public class LynxCreatureMoveTest {

    public static void main(String[] args) {
        System.exit(Harness.run("LynxCreatureMoveTest", LynxCreatureMoveTest::body));
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

    private static final Direction[] ALL =
        { Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT };

    private static String show(Direction[] ds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ds.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ds[i]);
        }
        return sb.append(']').toString();
    }

    private static LynxCreature creature(CreatureID id, Direction dir) {
        return new LynxCreature(dir, id, new Position(10, 10));
    }

    private static void body() throws Exception {
        RNG rng = new RNG(0, 0, 0);
        LynxCreature chip = creature(CreatureID.CHIP, Direction.UP);

        /* ================================================================
         * 1. The six deterministic creature types, every starting direction
         * ================================================================
         * Each expectation is built from TW's macros above, not from SuperCC's turn(). */

        for (Direction d : ALL) {
            Harness.eq("Lynx tank facing " + d + " considers only straight ahead",
                       show(creature(CreatureID.TANK_MOVING, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ d }));

            Harness.eq("Lynx ball facing " + d + " bounces: ahead then back",
                       show(creature(CreatureID.PINK_BALL, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ d, back(d) }));

            Harness.eq("Lynx glider facing " + d + " prefers ahead, left, right, back",
                       show(creature(CreatureID.GLIDER, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ d, left(d), right(d), back(d) }));

            Harness.eq("Lynx fireball facing " + d + " prefers ahead, right, left, back",
                       show(creature(CreatureID.FIREBALL, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ d, right(d), left(d), back(d) }));

            Harness.eq("Lynx bug facing " + d + " hugs the left wall: left, ahead, right, back",
                       show(creature(CreatureID.BUG, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ left(d), d, right(d), back(d) }));

            Harness.eq("Lynx paramecium facing " + d + " hugs the right wall: right, ahead, left, back",
                       show(creature(CreatureID.PARAMECIUM, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ right(d), d, left(d), back(d) }));
        }

        /* Bug and paramecium are mirror images of one another, and glider and fireball are too.
         * Stating it directly catches a copy-paste that swapped one pair's tables -- which the
         * per-direction assertions above would also catch, but this says WHY it matters. */
        for (Direction d : ALL) {
            Direction[] bug  = creature(CreatureID.BUG, d).getDirectionPriority(chip, rng);
            Direction[] para = creature(CreatureID.PARAMECIUM, d).getDirectionPriority(chip, rng);
            /* left(d) and right(d) are 180 degrees apart, so the relation between the two first
             * choices is back(), not right(). Writing right() here is what the first draft of this
             * file did, and the four assertions failed while all 24 transcribed comparisons above
             * passed -- which is the correct way round for a mistake in a derived invariant. */
            Harness.check("bug and paramecium facing " + d + " turn opposite ways from the same facing",
                          bug[0] == back(para[0]) && para[0] == back(bug[0]));
            Harness.check("bug facing " + d + " turns left, paramecium turns right",
                          bug[0] == left(d) && para[0] == right(d));

            Direction[] gl = creature(CreatureID.GLIDER, d).getDirectionPriority(chip, rng);
            Direction[] fb = creature(CreatureID.FIREBALL, d).getDirectionPriority(chip, rng);
            Harness.check("glider and fireball facing " + d + " agree on ahead and back, swap the middle",
                          gl[0] == fb[0] && gl[3] == fb[3] && gl[1] == fb[2] && gl[2] == fb[1]);
        }

        /* ================================================================
         * 2. Walker and Blob -- the two the rulesets genuinely disagree on
         * ================================================================ */

        for (Direction d : ALL) {
            /* lxlogic.c: choices[0] = dir; choices[1] = WALKER_TURN.
             * NOT MS's "dir then a random permutation of the other three". */
            Harness.eq("Lynx walker facing " + d + " is ahead then ONE pseudo-random turn",
                       show(creature(CreatureID.WALKER, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ d, Direction.WALKER_TURN }));

            /* lxlogic.c: choices[0] = BLOB_TURN, and nothing else. A blob gets ONE try.
             * NOT MS's random permutation of all four. */
            Harness.eq("Lynx blob facing " + d + " gets exactly one pseudo-random direction",
                       show(creature(CreatureID.BLOB, d).getDirectionPriority(chip, rng)),
                       show(new Direction[]{ Direction.BLOB_TURN }));
        }

        /* The sentinels must be distinct from every real direction, or a later resolver could treat
         * one as a compass direction and move the creature somewhere the rules never allowed. */
        for (Direction d : ALL) {
            Harness.check("WALKER_TURN is not the real direction " + d, Direction.WALKER_TURN != d);
            Harness.check("BLOB_TURN is not the real direction " + d,   Direction.BLOB_TURN != d);
        }
        Harness.check("WALKER_TURN and BLOB_TURN are distinct from each other",
                      Direction.WALKER_TURN != Direction.BLOB_TURN);

        /* ================================================================
         * 3. A creature that has already committed to a move chooses nothing
         * ================================================================
         * SuperCC's gate is `if (tDirection != NONE) return {NONE}`. Its counterpart in lxlogic.c
         * is the early return when getfdir(cr) is set -- different field, same purpose: a creature
         * mid-move does not get to re-choose. This pins SuperCC's contract. */

        for (CreatureID id : new CreatureID[]{ CreatureID.BUG, CreatureID.GLIDER, CreatureID.TANK_MOVING,
                                               CreatureID.WALKER, CreatureID.BLOB, CreatureID.PINK_BALL }) {
            LynxCreature c = creature(id, Direction.UP);
            c.setTDirection(Direction.LEFT);
            Harness.eq("a " + id + " already committed to a move chooses nothing",
                       show(c.getDirectionPriority(chip, rng)),
                       show(new Direction[]{ Direction.NONE }));
        }

        /* And the gate is specifically "not NONE" -- a creature whose tDirection is NONE still
         * chooses normally. Without this, a gate written as `if (tDirection == NONE) return NONE`
         * would pass every assertion above. */
        LynxCreature uncommitted = creature(CreatureID.GLIDER, Direction.UP);
        uncommitted.setTDirection(Direction.NONE);
        Harness.eq("tDirection == NONE does not gate: the glider still chooses",
                   show(uncommitted.getDirectionPriority(chip, rng)),
                   show(new Direction[]{ Direction.UP, left(Direction.UP),
                                         right(Direction.UP), back(Direction.UP) }));

        /* ================================================================
         * 4. The teeth step gate
         * ================================================================
         * lxlogic.c:894   if ((currenttime() + stepping()) & 4) return;
         * SuperCC        teethStep = ((tickNumber - 1 + step.ordinal()) & 4) == 0
         *
         * Teeth move on four ticks out of every eight, and WHICH four is set by the step. The -1 is
         * the offset between SuperCC's tick numbering and TW's currenttime, and it is the whole
         * reason this is worth a test: get the offset wrong and teeth move on the complementary
         * ticks, which is a desync that only shows up on levels with teeth.
         *
         * All eight Step values are checked at a known tick rather than driving the clock, so the
         * expectation comes from TW's formula rather than from SuperCC's. */

        java.nio.file.Path dTeeth = Harness.tempDir("scc-lynx-teeth-");
        DatBuilder tb = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        tb.level().title("Teeth")
          .tile(5, 5, game.Tile.CHIP_UP)
          .tile(9, 5, game.Tile.TEETH_LEFT).monster(9, 5)
          .end();
        java.io.File teethDat = tb.writeTo(dTeeth, "teeth.dat").toFile();

        int gateOpen = 0, gateShut = 0;
        for (Step step : Step.values()) {
            game.Level lv = new io.DatParser(teethDat)
                    .parseLevel(1, 0, step, game.Ruleset.LYNX, Direction.UP);
            primeMonsterList(lv);

            /* TW's own test, written out: teeth move when (currenttime() + stepping()) & 4 == 0.
             * SuperCC's currenttime is its tick number minus one. */
            int twTime = lv.getTickNumber() - 1;
            boolean twSaysMove = ((twTime + step.ordinal()) & 4) == 0;

            Harness.eq("teeth step at tick " + lv.getTickNumber() + " with " + step
                       + " matches TW's (currenttime + stepping) & 4",
                       lv.getMonsterList().getTeethStep(), twSaysMove);
            if (twSaysMove) gateOpen++; else gateShut++;
        }
        /* If the formula were a constant, or the & 4 became & 0, every step would agree and the
         * eight assertions above would still pass. The split is the real assertion. */
        Harness.eq("the eight steps split four-and-four, not all one way", gateOpen, 4);
        Harness.eq("the eight steps split four-and-four, not all one way", gateShut, 4);

        /* ================================================================
         * 5. Teeth seek: the larger axis first, ties vertical
         * ================================================================
         * lxlogic.c:
         *     y = chip.y - teeth.y;  n = y<0 ? NORTH : y>0 ? SOUTH : NIL
         *     x = chip.x - teeth.x;  m = x<0 ? WEST  : x>0 ? EAST  : NIL
         *     if (|x| > |y|) choices = {m, n}  else  choices = {n, m}
         *
         * So a tie goes VERTICAL first, because the test is strictly greater-than. That asymmetry
         * is the part worth pinning: written as >= it would flip every diagonal approach. */

        Step openStep = null;
        for (Step step : Step.values()) {
            game.Level probe = new io.DatParser(teethDat)
                    .parseLevel(1, 0, step, game.Ruleset.LYNX, Direction.UP);
            primeMonsterList(probe);
            if (probe.getMonsterList().getTeethStep()) { openStep = step; break; }
        }
        Harness.check("found a Step on which teeth may move", openStep != null);

        /* chip sits at (5,5) in the fixture. Each case moves the teeth and states what TW's
         * choosecreaturemove would pick, computed from the C above rather than from seek(). */
        int[][] cases = {
            //  teethX, teethY,  expected first,        expected second
            {  5, 9, S_UP,    S_NONE  },   // directly below chip -> straight up
            {  5, 1, S_DOWN,  S_NONE  },   // directly above chip -> straight down
            {  9, 5, S_LEFT,  S_NONE  },   // directly right of chip -> straight left
            {  1, 5, S_RIGHT, S_NONE  },   // directly left of chip -> straight right
            {  9, 9, S_UP,    S_LEFT  },   // |dx| == |dy| -> TIE, vertical first
            {  8, 9, S_UP,    S_LEFT  },   // dy larger -> vertical first
            {  9, 6, S_LEFT,  S_UP    },   // dx larger -> horizontal first
            {  1, 9, S_UP,    S_RIGHT },   // tie again, other quadrant
            {  2, 4, S_RIGHT, S_DOWN  },   // dx larger, chip below-right
        };

        for (int[] c : cases) {
            java.nio.file.Path dCase = Harness.tempDir("scc-lynx-seek-");
            DatBuilder sb = new DatBuilder().signature(DatBuilder.SIG_LYNX);
            sb.level().title("Seek")
              .tile(5, 5, game.Tile.CHIP_UP)
              .tile(c[0], c[1], game.Tile.TEETH_LEFT).monster(c[0], c[1])
              .end();
            game.Level lv = new io.DatParser(sb.writeTo(dCase, "seek.dat").toFile())
                    .parseLevel(1, 0, openStep, game.Ruleset.LYNX, Direction.UP);
            primeMonsterList(lv);

            game.Creature teeth = null;
            for (int i = 0; i < lv.getMonsterList().size(); i++) {
                game.Creature m = lv.getMonsterList().get(i);
                if (m.getCreatureType() == CreatureID.TEETH) { teeth = m; break; }
            }
            Harness.check("the teeth at " + c[0] + "," + c[1] + " is in the monster list", teeth != null);
            if (teeth == null) continue;

            Direction[] got = teeth.getDirectionPriority(lv.getChip(), rng);
            Harness.eq("teeth at " + c[0] + "," + c[1] + " seeking chip at 5,5",
                       show(got),
                       show(new Direction[]{ decode(c[2]), decode(c[3]) }));
        }

        /* ================================================================
         * 6. The cheat path still consumes the RNG
         * ================================================================
         * getDirectionPriority honors nextMoveDirectionCheat, but a blob still burns a random4()
         * and a walker still burns a pseudoRandom4() on the way past. That is deliberate: the
         * generator is shared with Tile World tick for tick, so a cheat that skipped the draw would
         * desync every creature downstream of it rather than just the one being cheated. */

        RNG blobRng = new RNG(0, 0, 0);
        blobRng.setCurrentValue(12345);
        int blobBefore = blobRng.getCurrentValue();
        LynxCreature blob = creature(CreatureID.BLOB, Direction.UP);
        blob.setNextMoveDirectionCheat(Direction.RIGHT);
        Harness.eq("a cheated blob returns the cheat direction",
                   show(blob.getDirectionPriority(chip, blobRng)),
                   show(new Direction[]{ Direction.RIGHT }));
        Harness.check("a cheated blob still advanced the main RNG",
                      blobRng.getCurrentValue() != blobBefore);
        Harness.eq("a cheated blob advanced it by exactly one random4()",
                   blobRng.getCurrentValue(), oneStep(blobBefore));

        /* The cheat is one-shot: the next call falls through to the normal table. */
        Harness.eq("the blob cheat is consumed after one use",
                   show(blob.getDirectionPriority(chip, blobRng)),
                   show(new Direction[]{ Direction.BLOB_TURN }));

        RNG walkRng = new RNG(0, 0, 0);
        walkRng.setPRNG1(0x1234);
        walkRng.setPRNG2(0x5678);
        int p1 = walkRng.getPRNG1(), p2 = walkRng.getPRNG2();
        int mainBefore = walkRng.getCurrentValue();
        LynxCreature walker = creature(CreatureID.WALKER, Direction.UP);
        walker.setNextMoveDirectionCheat(Direction.DOWN);
        Harness.eq("a cheated walker returns the cheat direction",
                   show(walker.getDirectionPriority(chip, walkRng)),
                   show(new Direction[]{ Direction.DOWN }));
        Harness.check("a cheated walker still advanced the WALKER generator",
                      walkRng.getPRNG1() != p1 || walkRng.getPRNG2() != p2);
        Harness.eq("a cheated walker did NOT touch the main RNG -- walkers use the other generator",
                   walkRng.getCurrentValue(), mainBefore);

        /* A cheated tank touches neither generator: only blob and walker draw. */
        RNG tankRng = new RNG(0, 0, 0);
        tankRng.setCurrentValue(999);
        tankRng.setPRNG1(7); tankRng.setPRNG2(9);
        LynxCreature tank = creature(CreatureID.TANK_MOVING, Direction.LEFT);
        tank.setNextMoveDirectionCheat(Direction.UP);
        Harness.eq("a cheated tank returns the cheat direction",
                   show(tank.getDirectionPriority(chip, tankRng)),
                   show(new Direction[]{ Direction.UP }));
        Harness.check("a cheated tank drew from neither generator",
                      tankRng.getCurrentValue() == 999 && tankRng.getPRNG1() == 7 && tankRng.getPRNG2() == 9);
    }

    /* Small integer codes so the seek table above reads as a table rather than as a wall of
     * Direction.UP. */
    private static final int S_NONE = 0, S_UP = 1, S_LEFT = 2, S_DOWN = 3, S_RIGHT = 4;

    private static Direction decode(int code) {
        switch (code) {
            case S_UP:    return Direction.UP;
            case S_LEFT:  return Direction.LEFT;
            case S_DOWN:  return Direction.DOWN;
            case S_RIGHT: return Direction.RIGHT;
            default:      return Direction.NONE;
        }
    }

    /**
     * Recomputes the per-tick monster-list state, which is what sets the teeth-step flag.
     *
     * Wrapped in a helper for one reason: the upstream method is spelled the British way. It is a
     * SicklySilverMoon identifier that predates this fork and is not ours to rename -- doing so
     * would not compile -- so the spelling is confined to this single line rather than repeated at
     * every call site. aen-exempt
     */
    private static void primeMonsterList(game.Level lv) {
        lv.getMonsterList().initialise();
    }

    /** One step of the shared LCG, for the consumption counts. Same formula as RngTest's oracle. */
    private static int oneStep(int value) {
        return (value * 1103515245 + 12345) & 0x7FFFFFFF;
    }
}
