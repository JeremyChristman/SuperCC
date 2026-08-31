import emulator.SuperCC;
import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Ruleset;
import game.Step;
import game.Tile;
import io.DatParser;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The monster list, under both rulesets.
 *
 * WHY THIS FILE IS WRITTEN AGAINST THE SPEC AND NOT AGAINST THE CODE
 * ------------------------------------------------------------------
 * Creature ORDER drives MS behavior. Two engines that agree on every tile but disagree on the order
 * of the monster list will diverge partway through a solution -- that is what a desync IS, and it
 * is the reason this fork exists. So an assertion here that was written by observing what SuperCC
 * currently prints would be worse than no assertion at all: it would freeze whatever it does today
 * and report green forever.
 *
 * Every expectation below is therefore derived from the reference implementation, Tile World, whose
 * rule for decoding optional field 10 is:
 *
 *     #define readpos(x, y)  (*(x) < CXGRID ? *(x) + CYGRID * *(y) : POS_INVALID)   [encoding.c]
 *     POS_INVALID = CXGRID * (CYGRID + 1) = 1056
 *     if (pos &lt; 0 || pos &gt;= CXGRID * CYGRID) continue;                             [mslogic.c]
 *
 * with CXGRID = CYGRID = 32. Read literally, that says an entry is discarded if and only if
 * x &gt;= 32 (flagged immediately, it never aliases onto another cell) or y &gt;= 32 (the product lands
 * at 1024 or beyond) -- and that surviving entries keep their FILE ORDER, with no slot left behind
 * for the ones dropped. Those two sentences are what this file tests.
 *
 * See docs/adr/0007 for why the fixtures are synthesized, and FORK.md's jc-7 section for the bug
 * that made this rule matter.
 */
public class MonsterListTest {

    public static void main(String[] args) {
        System.exit(Harness.run("MonsterListTest", MonsterListTest::body));
    }

    private static Path dir(String name) throws IOException {
        return Harness.tempDir("scc-monsters-" + name + "-");
    }

    private static Level firstLevel(Path dat) throws IOException {
        return new DatParser(dat.toFile()).parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
    }

    /** The creature list as "type@index" in list order -- the thing that has to match Tile World. */
    private static String order(Level level) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < level.getMonsterList().size(); i++) {
            Creature c = level.getMonsterList().get(i);
            if (i > 0) b.append(',');
            b.append(c == null ? "NULL" : c.getCreatureType() + "@" + c.getPosition().getIndex());
        }
        return b.toString();
    }

    private static void body() throws Exception {

        Harness.section("1. MS: surviving entries keep FILE order, not scan order");
        /* The decisive property. Tile World walks field 10 in the order the file stores it and
         * appends each surviving entry, so the list order is the FILE's order. If SuperCC instead
         * sorted by position, or walked the map, the two engines would step creatures in different
         * sequences and desync -- while every tile still matched. Deliberately listed in an order
         * that is neither ascending nor descending by index, so scan order and file order cannot
         * coincide by luck. */
        Path d1 = dir("fileorder");
        DatBuilder b1 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b1.level().title("Order")
          .tile(9, 9, Tile.BUG_UP)          // index 32*9+9  = 297
          .tile(1, 1, Tile.FIREBALL_UP)     // index 32*1+1  = 33
          .tile(5, 5, Tile.GLIDER_UP)       // index 32*5+5  = 165
          .monster(9, 9).monster(1, 1).monster(5, 5)
          .end();
        Level l1 = firstLevel(b1.writeTo(d1, "order.dat"));
        Harness.eq("three creatures, in the order field 10 lists them",
                   order(l1), "BUG@297,FIREBALL@33,GLIDER@165");

        Harness.section("2. MS: an invalid entry is DROPPED, and does not shift the survivors");
        /* Tile World's `continue` keeps no slot for a discarded entry, so the creatures after it
         * move up. A engine that instead left a gap -- or worse, kept a null -- would still have
         * the right creatures at the right tiles and still desync. The invalid entry is placed in
         * the MIDDLE precisely so that "dropped" and "slot preserved" give different answers. */
        Path d2 = dir("dropmiddle");
        DatBuilder b2 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b2.level().title("Drop")
          .tile(9, 9, Tile.BUG_UP)
          .tile(5, 5, Tile.GLIDER_UP)
          .monster(9, 9)        // valid
          .monster(40, 1)       // x >= 32 -> POS_INVALID -> discarded
          .monster(5, 5)        // valid
          .end();
        Level l2 = firstLevel(b2.writeTo(d2, "drop.dat"));
        Harness.eq("the survivors close ranks, in file order",
                   order(l2), "BUG@297,GLIDER@165");

        Harness.section("3. MS: the exact discard rule is x >= 32 OR y >= 32");
        /* The boundary, taken straight from readpos(). x = 31 is the last legal column and x = 32
         * the first illegal one; same for y. jc-7 existed because the old code computed 32*y+x
         * unconditionally, so x = 40, y = 1 ALIASED onto index 72 instead of being discarded --
         * an in-bounds cell belonging to a different creature. That is the aliasing class, and it
         * is why the x >= 32 case is checked with a real creature sitting on the aliased cell. */
        Path d3 = dir("boundary");

        DatBuilder b3a = new DatBuilder().signature(DatBuilder.SIG_MS);
        b3a.level().title("Edge").tile(31, 31, Tile.BUG_UP).monster(31, 31).end();
        Harness.eq("(31,31) is the last legal cell and is KEPT",
                   order(firstLevel(b3a.writeTo(d3, "edge.dat"))), "BUG@1023");

        DatBuilder b3b = new DatBuilder().signature(DatBuilder.SIG_MS);
        // (40,1) would alias onto 32*1+40 = 72 = (8,2). Put a real creature there: if the engine
        // aliased, the list would come back with a BUG; Tile World's rule says it must be empty.
        b3b.level().title("AliasX").tile(8, 2, Tile.BUG_UP).monster(40, 1).end();
        Harness.eq("x >= 32 is discarded, NOT aliased onto (8,2)",
                   order(firstLevel(b3b.writeTo(d3, "aliasx.dat"))), "");

        DatBuilder b3c = new DatBuilder().signature(DatBuilder.SIG_MS);
        b3c.level().title("BigY").tile(1, 1, Tile.BUG_UP).monster(1, 40).end();
        Harness.eq("y >= 32 is discarded (32*40+1 is past the map)",
                   order(firstLevel(b3c.writeTo(d3, "bigy.dat"))), "");

        DatBuilder b3d = new DatBuilder().signature(DatBuilder.SIG_MS);
        b3d.level().title("Far").tile(1, 1, Tile.BUG_UP).monster(160, 160).end();
        Harness.eq("both far out of range is discarded, and the level still opens",
                   order(firstLevel(b3d.writeTo(d3, "far.dat"))), "");

        Harness.section("4. MS: field 10 is the ONLY source of the list");
        /* A creature on the map that field 10 never names is not in the monster list, and an entry
         * naming a cell with no creature contributes nothing. Both halves matter: the first stops
         * an engine from "helpfully" scanning the map, the second stops it from storing a null. */
        Path d4 = dir("onlysource");
        DatBuilder b4a = new DatBuilder().signature(DatBuilder.SIG_MS);
        b4a.level().title("Unlisted").tile(3, 3, Tile.BUG_UP).tile(4, 4, Tile.GLIDER_UP)
                   .monster(3, 3).end();
        Harness.eq("a creature field 10 does not list is not in the list",
                   order(firstLevel(b4a.writeTo(d4, "unlisted.dat"))), "BUG@99");

        DatBuilder b4b = new DatBuilder().signature(DatBuilder.SIG_MS);
        b4b.level().title("Empty").monster(7, 7).end();   // points at bare floor
        Harness.eq("an entry pointing at a cell with no creature adds nothing",
                   order(firstLevel(b4b.writeTo(d4, "empty.dat"))), "");

        Harness.section("5. MS: a creature standing on a clone machine is excluded");
        /* Clone-machine contents are not free creatures -- they are spawned by a button and must
         * not be stepped as part of the list. The BG tile is what distinguishes them. */
        Path d5 = dir("clone");
        DatBuilder b5 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b5.level().title("Cloner")
          .tile(6, 6, Tile.BUG_UP).under(6, 6, Tile.CLONE_MACHINE)
          .tile(7, 7, Tile.BUG_UP)
          .monster(6, 6).monster(7, 7)
          .end();
        Harness.eq("only the free bug is listed", order(firstLevel(b5.writeTo(d5, "clone.dat"))),
                   "BUG@" + (32 * 7 + 7));

        Harness.section("6. Lynx: the list is built by MAP SCAN, and Chip is first");
        /* Lynx has no optional field 10 at all -- the creature list comes from the map itself, in
         * reading order. Two Lynx-specific rules on top of that, both required for the engine to
         * run at all: Chip must be creature 0, and blocks are creatures (in MS they are not).
         * Chip is deliberately placed LAST in reading order so "Chip is first" cannot pass by
         * accident. */
        Path d6 = dir("lynxorder");
        DatBuilder b6 = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        b6.level().title("Lynx")
          .tile(1, 1, Tile.BUG_UP)          // 33
          .tile(2, 2, Tile.BLOCK)           // 66  -- a creature under Lynx
          .tile(3, 3, Tile.GLIDER_UP)       // 99
          .tile(10, 10, Tile.CHIP_DOWN)     // 330 -- last in reading order
          .end();
        Level l6 = firstLevel(b6.writeTo(d6, "lynx.dat"));
        Harness.eq("ruleset is Lynx", l6.getRuleset(), Ruleset.LYNX);
        Harness.eq("Chip is swapped to slot 0; the creature it displaced takes Chip's old slot",
                   order(l6), "CHIP@330,BLOCK@66,GLIDER@99,BUG@33");
        Harness.eq("Chip really is creature 0",
                   l6.getMonsterList().get(0).getCreatureType(), CreatureID.CHIP);

        Harness.section("7. Lynx: a level with no Chip is legalized rather than rejected");
        /* Lynx cannot run without a player, so rather than refuse the level the engine inserts one
         * at (0,0). "Legalize instead of reject" is the deliberate choice; the alternative is a
         * level nobody can open, which is precisely what jc-7 was about. */
        Path d7 = dir("nochip");
        DatBuilder b7 = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        b7.level().title("Chipless").tile(5, 5, Tile.BUG_UP).end();
        Level l7 = firstLevel(b7.writeTo(d7, "nochip.dat"));
        Harness.eq("a Chip is inserted at index 0 and placed first",
                   order(l7), "CHIP@0,BUG@165");

        Harness.section("8. Lynx: only the FIRST Chip becomes the player");
        /* A malformed level with two Chips must not produce two players. The later one is left as
         * a map tile rather than becoming a creature. */
        Path d8 = dir("twochips");
        DatBuilder b8 = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        b8.level().title("TwoChips")
          .tile(1, 1, Tile.CHIP_DOWN)
          .tile(2, 2, Tile.CHIP_DOWN)
          .end();
        Level l8 = firstLevel(b8.writeTo(d8, "twochips.dat"));
        Harness.eq("exactly one Chip in the list", order(l8), "CHIP@33");

        Harness.section("9. Lynx: a creature's tile is popped up, leaving what was beneath it");
        /* Lynx has no lower layer. The engine lifts each creature off the map into the creature
         * list and leaves the tile that was underneath in its place -- so the WATER stays and the
         * bug does not remain painted on the map. Getting this wrong leaves a phantom creature
         * that the renderer draws and the engine never moves. */
        Path d9 = dir("pop");
        DatBuilder b9 = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        b9.level().title("Pop")
          .tile(4, 4, Tile.CHIP_DOWN)
          .tile(8, 8, Tile.GLIDER_UP).under(8, 8, Tile.WATER)
          .end();
        Level l9 = firstLevel(b9.writeTo(d9, "pop.dat"));
        Harness.eq("the glider is in the creature list", order(l9), "CHIP@132,GLIDER@264");
        Harness.eq("and what was under it is now the map tile",
                   l9.getLayerFG().get(32 * 8 + 8), Tile.WATER);

        Harness.section("10. the two rulesets disagree about blocks, and that is correct");
        /* The same map, read under each signature. Under Lynx a BLOCK is a creature and appears in
         * the list; under MS it is scenery and only field 10 can name creatures. This is a real
         * behavioral fork between the rulesets, and pinning it stops a future "unification" from
         * quietly changing one of them. */
        Path d10 = dir("blocks");
        DatBuilder lynx = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        lynx.level().title("Blocks").tile(1, 1, Tile.CHIP_DOWN).tile(2, 2, Tile.BLOCK).end();
        Harness.eq("Lynx: a block is a creature",
                   order(firstLevel(lynx.writeTo(d10, "lynxblock.dat"))), "CHIP@33,BLOCK@66");

        DatBuilder ms = new DatBuilder().signature(DatBuilder.SIG_MS);
        ms.level().title("Blocks").tile(1, 1, Tile.CHIP_DOWN).tile(2, 2, Tile.BLOCK).end();
        Harness.eq("MS: with no field 10, the list is empty",
                   order(firstLevel(ms.writeTo(d10, "msblock.dat"))), "");

        Harness.section("11. Lynx: the starting timer is 95 hundredths, not 90");
        /* MS and Lynx seed the sub-second remainder differently -- getTimer(t, 90) for MS and
         * (t, 95) for Lynx. A one-tick difference at the start is enough to change what a
         * tick-exact solution does at the end. */
        Path d11 = dir("timer");
        DatBuilder tl = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        tl.level().title("T").timeLimit(100).tile(1, 1, Tile.CHIP_DOWN).end();
        Harness.eq("Lynx starts at time*100 + 95",
                   firstLevel(tl.writeTo(d11, "lynxtime.dat")).getStartTime(), 100 * 100 + 95);

        DatBuilder tm = new DatBuilder().signature(DatBuilder.SIG_MS);
        tm.level().title("T").timeLimit(100).tile(1, 1, Tile.CHIP_DOWN).end();
        Harness.eq("MS starts at time*100 + 90",
                   firstLevel(tm.writeTo(d11, "mstime.dat")).getStartTime(), 100 * 100 + 90);

        Harness.section("12. the headless emulator agrees with the parser");
        /* The list the GUI-less emulator ends up holding is the same one DatParser produced -- so
         * nothing between openLevelset and the loaded level reorders it. */
        Path d12 = dir("headless");
        DatBuilder b12 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b12.level().title("Headless")
           .tile(9, 9, Tile.BUG_UP).tile(1, 1, Tile.FIREBALL_UP)
           .monster(9, 9).monster(1, 1)
           .end();
        java.io.File set = b12.writeTo(d12, "headless.dat").toFile();
        SuperCC emu = new SuperCC(false);
        emu.openLevelset(set);
        Harness.eq("same order through the emulator", order(emu.getLevel()), "BUG@297,FIREBALL@33");
    }
}
