import game.Direction;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;
import game.button.BrownButton;
import game.button.RedButton;
import io.DatParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Trap and clone-machine wiring -- CC1 optional fields 4 and 5.
 *
 * WHY THIS MATTERS MORE THAN IT LOOKS
 * -----------------------------------
 * These two fields are read with different strides, and nothing else in the codebase checks that
 * arithmetic. A trap record is TEN bytes and a clone record is EIGHT, differing by exactly one
 * trailing unused word:
 *
 *     field 4 (traps):   button x | button y | trap x  | trap y  | unused     (2 bytes each)
 *     field 5 (cloners): button x | button y | clone x | clone y
 *
 * Get either stride wrong and every record after the first is read from the wrong offset -- so the
 * level still opens, every tile is still correct, and the brown buttons are silently wired to the
 * wrong traps. That is a desync, not a crash: the level plays, and it plays differently from Tile
 * World. Exactly the failure class this fork exists to hunt.
 *
 * Two consequences for how these tests are written, both deliberate:
 *
 *   * EVERY fixture uses TWO records, never one. With a single record a wrong stride is invisible,
 *     because there is no second record to misalign.
 *   * Every coordinate is asymmetric (x != y) and distinct across records, so an x/y swap or a
 *     byte-for-word confusion cannot coincidentally produce the right index.
 *
 * Expectations come from the CC1 format specification (seasip.info/ccfile.html), not from reading
 * DatParser -- see MonsterListTest's header for why that distinction is the whole point.
 */
public class ConnectionTest {

    public static void main(String[] args) {
        System.exit(Harness.run("ConnectionTest", ConnectionTest::body));
    }

    private static Path dir(String name) throws IOException {
        return Harness.tempDir("scc-conn-" + name + "-");
    }

    private static Level firstLevel(Path dat) throws IOException {
        return new DatParser(dat.toFile()).parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
    }

    private static int idx(int x, int y) { return 32 * y + x; }

    /**
     * The target index of the button at (x,y), or -1 when there is no button there.
     *
     * Everything in this file goes through this rather than dereferencing directly, because the
     * bugs it hunts DELETE buttons: a wrong stride makes the second record vanish, and a bare
     * getTargetPosition() on the missing button throws and aborts the whole file, hiding every
     * assertion after it. A failing assertion should report and let the rest of the suite run.
     */
    private static int brownTarget(Level level, int x, int y) {
        BrownButton b = brownAt(level, x, y);
        return b == null ? -1 : b.getTargetPosition().getIndex();
    }

    private static int redTarget(Level level, int x, int y) {
        RedButton b = redAt(level, x, y);
        return b == null ? -1 : b.getTargetPosition().getIndex();
    }

    private static int trapIndexAt(Level level, int x, int y) {
        BrownButton b = brownAt(level, x, y);
        return b == null ? -1 : b.getTrapIndex();
    }

    /** The single brown button at (x,y), or null. Fails loudly rather than throwing on absence. */
    private static BrownButton brownAt(Level level, int x, int y) {
        List<BrownButton> at = level.getBrownButtons().getList(new Position(x, y));
        return (at == null || at.isEmpty()) ? null : at.get(0);
    }

    private static RedButton redAt(Level level, int x, int y) {
        List<RedButton> at = level.getRedButtons().getList(new Position(x, y));
        return (at == null || at.isEmpty()) ? null : at.get(0);
    }

    private static void body() throws Exception {

        Harness.section("1. two traps: the SECOND one proves the 10-byte stride");
        /* The decisive test in this file. One trap would pass under any stride; the second is read
         * from an offset that only comes out right if the reader consumed four coordinate words
         * AND skipped the trailing unused one. A reader that forgot the skip lands two bytes early
         * and produces garbage here while the level still opens perfectly. */
        Path d1 = dir("twotraps");
        DatBuilder b1 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b1.level().title("Traps")
          .tile(1, 2, Tile.BUTTON_BROWN).tile(3, 4, Tile.TRAP)
          .tile(5, 6, Tile.BUTTON_BROWN).tile(7, 8, Tile.TRAP)
          .trap(1, 2, 3, 4)
          .trap(5, 6, 7, 8)
          .end();
        Level l1 = firstLevel(b1.writeTo(d1, "traps.dat"));
        Harness.eq("two brown buttons were wired", l1.getBrownButtons().size(), 2);

        BrownButton t1 = brownAt(l1, 1, 2);
        Harness.check("the first button exists at (1,2)", t1 != null);
        Harness.eq("and points at the trap at (3,4)",
                   t1 == null ? -1 : t1.getTargetPosition().getIndex(), idx(3, 4));

        BrownButton t2 = brownAt(l1, 5, 6);
        Harness.check("the SECOND button exists at (5,6) -- the stride held", t2 != null);
        Harness.eq("and points at the trap at (7,8)",
                   t2 == null ? -1 : t2.getTargetPosition().getIndex(), idx(7, 8));

        Harness.section("2. two cloners: the 8-byte stride is a DIFFERENT number");
        /* Clone records have no trailing word. Applying the trap stride here reads 16 bytes as one
         * record and loses the second cloner entirely, so the count assertion catches it even
         * before the coordinates do. */
        Path d2 = dir("twocloners");
        DatBuilder b2 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b2.level().title("Cloners")
          .tile(10, 11, Tile.BUTTON_RED).tile(12, 13, Tile.CLONE_MACHINE)
          .tile(14, 15, Tile.BUTTON_RED).tile(16, 17, Tile.CLONE_MACHINE)
          .cloner(10, 11, 12, 13)
          .cloner(14, 15, 16, 17)
          .end();
        Level l2 = firstLevel(b2.writeTo(d2, "cloners.dat"));
        Harness.eq("both red buttons were wired", l2.getRedButtons().size(), 2);

        RedButton c1 = redAt(l2, 10, 11);
        Harness.check("the first red button exists at (10,11)", c1 != null);
        Harness.eq("and points at the cloner at (12,13)",
                   c1 == null ? -1 : c1.getTargetPosition().getIndex(), idx(12, 13));

        RedButton c2 = redAt(l2, 14, 15);
        Harness.check("the SECOND red button exists at (14,15) -- the stride held", c2 != null);
        Harness.eq("and points at the cloner at (16,17)",
                   c2 == null ? -1 : c2.getTargetPosition().getIndex(), idx(16, 17));

        Harness.section("3. coordinates are 16-bit WORDS, not the single bytes field 10 uses");
        /* Field 10 stores a coordinate in one byte; fields 4 and 5 store it in two. Reading these
         * as bytes would take the low byte of button x as x and the high byte (always zero) as y,
         * so a button at (1,2) would come back at index 1 instead of 65. Asymmetric coordinates
         * make that unmistakable. */
        Path d3 = dir("words");
        DatBuilder b3 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b3.level().title("Words").trap(1, 2, 3, 4).trap(9, 1, 2, 9).end();
        Level l3 = firstLevel(b3.writeTo(d3, "words.dat"));
        Harness.check("a button at (1,2) is at index 65, not index 1", brownAt(l3, 1, 2) != null);
        Harness.eq("its target at (3,4) is index 131, not index 3",
                   brownTarget(l3, 1, 2), 131);
        /* (9,1) and (2,9) are the same two numbers in the other order: if x and y were swapped
         * anywhere in the chain, these two would land on each other's indices. */
        Harness.check("a button at (9,1) is distinct from one at (1,9)", brownAt(l3, 9, 1) != null);
        Harness.eq("x and y are not transposed",
                   brownTarget(l3, 9, 1), idx(2, 9));

        Harness.section("4. trapIndex is the record's position in the file");
        /* BrownButton carries the record index, and the engine uses it to address trap state. If
         * the indices were assigned by map order, or reversed, traps would open and close in the
         * wrong pairs. Written so file order and map order disagree: the first record's button
         * sits at a HIGHER map index than the second's. */
        Path d4 = dir("trapindex");
        DatBuilder b4 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b4.level().title("Index")
          .trap(20, 20, 21, 21)     // record 0, button index 660
          .trap(1, 1, 2, 2)         // record 1, button index 33
          .end();
        Level l4 = firstLevel(b4.writeTo(d4, "index.dat"));
        Harness.eq("the first record has trapIndex 0", trapIndexAt(l4, 20, 20), 0);
        Harness.eq("the second has trapIndex 1, by FILE order not map order",
                   trapIndexAt(l4, 1, 1), 1);

        Harness.section("5. one button may control several targets");
        /* CC1 permits repeated button coordinates, which is how a single brown button springs more
         * than one trap. The buttons are held in a multimap precisely so the later record does not
         * overwrite the earlier one -- a plain map here would silently drop connections. */
        Path d5 = dir("multi");
        DatBuilder b5 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b5.level().title("Multi")
          .trap(4, 4, 6, 6)
          .trap(4, 4, 8, 8)
          .end();
        Level l5 = firstLevel(b5.writeTo(d5, "multi.dat"));
        List<BrownButton> both = l5.getBrownButtons().getList(new Position(4, 4));
        Harness.eq("both connections from the same button survive", both == null ? 0 : both.size(), 2);
        if (both != null && both.size() == 2) {
            int a = both.get(0).getTargetPosition().getIndex();
            int b = both.get(1).getTargetPosition().getIndex();
            Harness.check("they point at the two different traps",
                          (a == idx(6, 6) && b == idx(8, 8)) || (a == idx(8, 8) && b == idx(6, 6)));
        } else {
            Harness.check("they point at the two different traps", false);
        }

        Harness.section("6. traps, cloners and the monster list coexist without corrupting each other");
        /* The real integration risk. Three optional fields of three different shapes in one level:
         * if any reader over- or under-consumes, the TLV walk desynchronizes and everything after
         * it misparses. Field 10 uses single bytes, field 4 ten-byte records, field 5 eight-byte
         * ones -- and they are emitted in ascending field order, as a real .dat does. */
        Path d6 = dir("together");
        DatBuilder b6 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b6.level().title("All Three").password("KEYS").hint("everything at once").author("Test")
          .timeLimit(150).chips(2)
          .tile(3, 3, Tile.BUG_UP)
          .trap(1, 2, 3, 4).trap(5, 6, 7, 8)
          .cloner(10, 11, 12, 13).cloner(14, 15, 16, 17)
          .monster(3, 3)
          .end();
        Level l6 = firstLevel(b6.writeTo(d6, "all.dat"));
        Harness.eq("title still parses", l6.getTitle(), "All Three");
        Harness.eq("password still parses", l6.getPassword(), "KEYS");
        Harness.eq("hint still parses", l6.getHint(), "everything at once");
        Harness.eq("author still parses", l6.getAuthor(), "Test");
        Harness.eq("timer still parses", l6.getStartTime(), 150 * 100 + 90);
        Harness.eq("chips still parse", l6.getChipsLeft(), 2);
        Harness.eq("both traps survive", l6.getBrownButtons().size(), 2);
        Harness.eq("both cloners survive", l6.getRedButtons().size(), 2);
        Harness.eq("the monster list survives", l6.getMonsterList().size(), 1);
        Harness.eq("and the second trap is still correct",
                   brownTarget(l6, 5, 6), idx(7, 8));
        Harness.eq("and the second cloner is still correct",
                   redTarget(l6, 14, 15), idx(16, 17));

        Harness.section("7. a level with neither field has no connection buttons");
        /* The empty case has to be empty rather than null, because the engine iterates these on
         * every press without checking. */
        Path d7 = dir("none");
        DatBuilder b7 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b7.level().title("Bare").end();
        Level l7 = firstLevel(b7.writeTo(d7, "bare.dat"));
        Harness.eq("no brown buttons", l7.getBrownButtons().size(), 0);
        Harness.eq("no red buttons", l7.getRedButtons().size(), 0);

        Harness.section("8. green and blue buttons come from the MAP, not from a field");
        /* Unlike brown and red, these two have no connection records at all -- they act on every
         * toggle wall or every tank, so the engine finds them by scanning the map. Pinning that
         * difference stops someone from "unifying" the four button types. Both layers are scanned,
         * so a button under a creature still counts. */
        Path d8 = dir("greenblue");
        DatBuilder b8 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b8.level().title("GB")
          .tile(2, 3, Tile.BUTTON_GREEN)
          .tile(4, 5, Tile.BUTTON_BLUE)
          .tile(6, 7, Tile.BUG_UP).under(6, 7, Tile.BUTTON_GREEN)   // buried under a creature
          .end();
        Level l8 = firstLevel(b8.writeTo(d8, "gb.dat"));
        Harness.eq("both green buttons are found, including the buried one",
                   l8.getGreenButtons().size(), 2);
        Harness.eq("the blue button is found", l8.getBlueButtons().size(), 1);
        Harness.check("no connection records were needed for either",
                      l8.getBrownButtons().size() == 0 && l8.getRedButtons().size() == 0);
    }
}
