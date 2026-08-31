import game.Creature;
import game.Direction;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;
import game.button.BlueButton;
import game.button.BrownButton;
import game.button.GreenButton;
import game.button.RedButton;
import io.DatParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The four button types.
 *
 * They split cleanly in two, and the split is the thing worth pinning: green and blue act on
 * EVERYTHING of a kind, level-wide, and have no connection records at all; brown and red act on ONE
 * target each, named by optional fields 4 and 5 (see ConnectionTest for that wiring).
 *
 *     green  -> flips every toggle wall on the level
 *     blue   -> reverses every tank on the level
 *     brown  -> holds ITS trap open while pressed
 *     red    -> clones from ITS clone machine
 *
 * The failure that matters here is the same one as everywhere else in the emulator: a button that
 * acts on the wrong set, or on one target too many, produces a level that plays perfectly and plays
 * differently from Tile World. So the assertions below are as much about what a press does NOT
 * touch as about what it does -- section 4 (green leaves non-toggles alone) and section 8 (brown
 * buttons are independent) are the two that would catch a leak.
 *
 * Expectations are the CC1 behaviors the reference engine implements, not a transcript of what
 * these classes currently do; see MonsterListTest's header for why that distinction is load-bearing.
 */
public class ButtonTest {

    public static void main(String[] args) {
        System.exit(Harness.run("ButtonTest", ButtonTest::body));
    }

    private static Path dir(String name) throws IOException {
        return Harness.tempDir("scc-button-" + name + "-");
    }

    private static Level firstLevel(Path dat) throws IOException {
        return new DatParser(dat.toFile()).parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
    }

    private static int idx(int x, int y) { return 32 * y + x; }

    private static GreenButton green(Level l, int x, int y) {
        List<GreenButton> b = l.getGreenButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    private static BlueButton blue(Level l, int x, int y) {
        List<BlueButton> b = l.getBlueButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    private static BrownButton brown(Level l, int x, int y) {
        List<BrownButton> b = l.getBrownButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    private static RedButton red(Level l, int x, int y) {
        List<RedButton> b = l.getRedButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    /** The direction of the first creature in the monster list, or null when there is none. */
    private static Direction firstCreatureDir(Level l) {
        if (l.getMonsterList().size() == 0) return null;
        Creature c = l.getMonsterList().get(0);
        return c == null ? null : c.getDirection();
    }

    private static void body() throws Exception {

        Harness.section("1. green flips every toggle wall, in both directions");
        /* One press swaps open for closed and closed for open, everywhere at once -- there is no
         * such thing as a green button wired to one door. Both directions are checked in the same
         * press, because an implementation that only closed open doors would still look right on a
         * level where they all start open. */
        Path d1 = dir("greenflip");
        DatBuilder b1 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b1.level().title("Green")
          .tile(2, 2, Tile.BUTTON_GREEN)
          .tile(5, 5, Tile.TOGGLE_OPEN)
          .tile(9, 9, Tile.TOGGLE_CLOSED)
          .end();
        Level l1 = firstLevel(b1.writeTo(d1, "green.dat"));
        Harness.check("the green button was found on the map", green(l1, 2, 2) != null);
        Harness.eq("an open door starts open", l1.getLayerFG().get(idx(5, 5)), Tile.TOGGLE_OPEN);
        Harness.eq("a closed door starts closed", l1.getLayerFG().get(idx(9, 9)), Tile.TOGGLE_CLOSED);

        green(l1, 2, 2).press(l1);
        Harness.eq("after one press the open door is closed",
                   l1.getLayerFG().get(idx(5, 5)), Tile.TOGGLE_CLOSED);
        Harness.eq("and the closed door is open",
                   l1.getLayerFG().get(idx(9, 9)), Tile.TOGGLE_OPEN);

        green(l1, 2, 2).press(l1);
        Harness.eq("a second press restores the first door",
                   l1.getLayerFG().get(idx(5, 5)), Tile.TOGGLE_OPEN);
        Harness.eq("and the second", l1.getLayerFG().get(idx(9, 9)), Tile.TOGGLE_CLOSED);

        Harness.section("2. green acts level-wide, and has no target of its own");
        /* Green and blue are plain Buttons, not ConnectionButtons: there is no field 4 or 5 record
         * behind them and nothing to point at. Doors at opposite corners must both flip from one
         * press, which is what distinguishes "acts on everything" from "acts on what is near". */
        Path d2 = dir("greenwide");
        DatBuilder b2 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b2.level().title("Wide")
          .tile(0, 0, Tile.BUTTON_GREEN)
          .tile(1, 0, Tile.TOGGLE_OPEN)
          .tile(31, 31, Tile.TOGGLE_OPEN)
          .end();
        Level l2 = firstLevel(b2.writeTo(d2, "wide.dat"));
        green(l2, 0, 0).press(l2);
        Harness.eq("the near door flipped", l2.getLayerFG().get(idx(1, 0)), Tile.TOGGLE_CLOSED);
        Harness.eq("and so did the one in the far corner",
                   l2.getLayerFG().get(idx(31, 31)), Tile.TOGGLE_CLOSED);
        /* Checked reflectively rather than with instanceof, because javac REJECTS the direct
         * `green instanceof ConnectionButton` outright -- the two types are unrelated, so the
         * comparison can never be true and the compiler says so. That is a stronger guarantee than
         * any runtime assertion; this line just records it somewhere a reader will see it. */
        Harness.check("green and blue are plain Buttons -- they cannot carry a target",
                      !game.button.ConnectionButton.class.isAssignableFrom(GreenButton.class)
                      && !game.button.ConnectionButton.class.isAssignableFrom(BlueButton.class));
        Harness.check("brown and red are ConnectionButtons -- they must carry one",
                      game.button.ConnectionButton.class.isAssignableFrom(BrownButton.class)
                      && game.button.ConnectionButton.class.isAssignableFrom(RedButton.class));

        Harness.section("3. green reaches a toggle wall buried under a creature");
        /* MS keeps two layers, and a creature standing on a toggle wall pushes it to the lower one.
         * The door still has to flip -- otherwise a monster parked on a door freezes it, and the
         * level diverges the moment that monster moves. */
        Path d3 = dir("greenburied");
        DatBuilder b3 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b3.level().title("Buried")
          .tile(2, 2, Tile.BUTTON_GREEN)
          .tile(6, 6, Tile.BUG_UP).under(6, 6, Tile.TOGGLE_OPEN)
          .end();
        Level l3 = firstLevel(b3.writeTo(d3, "buried.dat"));
        Harness.eq("the buried door starts open",
                   l3.getLayerBG().get(idx(6, 6)), Tile.TOGGLE_OPEN);
        green(l3, 2, 2).press(l3);
        Harness.eq("and flips even though a creature is standing on it",
                   l3.getLayerBG().get(idx(6, 6)), Tile.TOGGLE_CLOSED);
        Harness.eq("the creature itself is untouched",
                   l3.getLayerFG().get(idx(6, 6)), Tile.BUG_UP);

        Harness.section("4. green touches NOTHING that is not a toggle wall");
        /* The containment test. A press that also rewrote walls, floors or doors would be caught
         * here and nowhere else -- every other assertion in this file only looks at what SHOULD
         * change. */
        Path d4 = dir("greenscope");
        DatBuilder b4 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b4.level().title("Scope")
          .tile(2, 2, Tile.BUTTON_GREEN)
          .tile(4, 4, Tile.WALL)
          .tile(5, 4, Tile.DOOR_BLUE)
          .tile(6, 4, Tile.WATER)
          .tile(7, 4, Tile.TOGGLE_OPEN)
          .end();
        Level l4 = firstLevel(b4.writeTo(d4, "scope.dat"));
        green(l4, 2, 2).press(l4);
        Harness.eq("a wall is still a wall", l4.getLayerFG().get(idx(4, 4)), Tile.WALL);
        Harness.eq("a blue door is untouched", l4.getLayerFG().get(idx(5, 4)), Tile.DOOR_BLUE);
        Harness.eq("water is untouched", l4.getLayerFG().get(idx(6, 4)), Tile.WATER);
        Harness.eq("the button itself is untouched", l4.getLayerFG().get(idx(2, 2)), Tile.BUTTON_GREEN);
        Harness.eq("and only the toggle wall moved",
                   l4.getLayerFG().get(idx(7, 4)), Tile.TOGGLE_CLOSED);

        Harness.section("5. blue reverses a tank");
        /* A tank facing up faces down afterwards. Direction is the whole point of the blue button;
         * anything else about the tank stays as it was. */
        Path d5 = dir("bluetank");
        DatBuilder b5 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b5.level().title("Blue")
          .tile(3, 3, Tile.BUTTON_BLUE)
          .tile(8, 8, Tile.TANK_UP).monster(8, 8)
          .end();
        Level l5 = firstLevel(b5.writeTo(d5, "blue.dat"));
        Harness.eq("the tank starts facing up", firstCreatureDir(l5), Direction.UP);
        Harness.check("the blue button was found", blue(l5, 3, 3) != null);
        blue(l5, 3, 3).press(l5);
        Harness.eq("after the press it faces down", firstCreatureDir(l5), Direction.DOWN);
        blue(l5, 3, 3).press(l5);
        Harness.eq("and pressing again turns it back", firstCreatureDir(l5), Direction.UP);

        Harness.section("6. blue reverses EVERY tank, and leaves other creatures alone");
        /* Same level-wide rule as green, plus the containment half: a bug must not be spun around
         * by a blue button. Tanks are placed facing different ways so a press that merely SET a
         * direction, rather than reversing, would show up. */
        Path d6 = dir("bluemany");
        DatBuilder b6 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b6.level().title("Many")
          .tile(3, 3, Tile.BUTTON_BLUE)
          .tile(10, 10, Tile.TANK_UP).monster(10, 10)
          .tile(20, 20, Tile.TANK_LEFT).monster(20, 20)
          .tile(25, 25, Tile.BUG_UP).monster(25, 25)
          .end();
        Level l6 = firstLevel(b6.writeTo(d6, "many.dat"));
        Harness.eq("three creatures are listed", l6.getMonsterList().size(), 3);
        blue(l6, 3, 3).press(l6);
        Harness.eq("the up tank now faces down",
                   l6.getMonsterList().get(0).getDirection(), Direction.DOWN);
        Harness.eq("the left tank now faces right -- reversed, not set to one direction",
                   l6.getMonsterList().get(1).getDirection(), Direction.RIGHT);
        Harness.eq("the bug is not a tank and did not turn",
                   l6.getMonsterList().get(2).getDirection(), Direction.UP);

        Harness.section("7. brown holds its trap open while pressed, and closes it on release");
        /* The brown button is the only one with a released state, and the trap follows it exactly.
         * A trap that stayed open after release would let anything walk out of it forever. */
        Path d7 = dir("browntrap");
        DatBuilder b7 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b7.level().title("Trap")
          .tile(1, 2, Tile.BUTTON_BROWN).tile(3, 4, Tile.TRAP)
          .trap(1, 2, 3, 4)
          .end();
        Level l7 = firstLevel(b7.writeTo(d7, "trap.dat"));
        BrownButton t7 = brown(l7, 1, 2);
        Harness.check("the brown button was wired", t7 != null);
        Harness.check("the trap starts closed", !t7.isOpen(l7));
        t7.press(l7);
        Harness.check("pressing opens it", t7.isOpen(l7));
        t7.release(l7);
        Harness.check("releasing closes it again", !t7.isOpen(l7));

        Harness.section("8. brown buttons are INDEPENDENT of one another");
        /* The leak test, and the reason trapIndex exists. Two buttons, two traps: pressing one must
         * not spring the other. An implementation keyed on anything coarser than the individual
         * record -- say, "is any trap open" -- passes section 7 and fails here. */
        Path d8 = dir("brownindep");
        DatBuilder b8 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b8.level().title("Two")
          .tile(1, 2, Tile.BUTTON_BROWN).tile(3, 4, Tile.TRAP)
          .tile(5, 6, Tile.BUTTON_BROWN).tile(7, 8, Tile.TRAP)
          .trap(1, 2, 3, 4)
          .trap(5, 6, 7, 8)
          .end();
        Level l8 = firstLevel(b8.writeTo(d8, "two.dat"));
        BrownButton a8 = brown(l8, 1, 2), c8 = brown(l8, 5, 6);
        Harness.check("both buttons exist", a8 != null && c8 != null);
        a8.press(l8);
        Harness.check("pressing the first opens the first trap", a8.isOpen(l8));
        Harness.check("and leaves the SECOND trap shut", !c8.isOpen(l8));
        c8.press(l8);
        Harness.check("pressing the second opens the second too", c8.isOpen(l8));
        a8.release(l8);
        Harness.check("releasing the first closes only the first", !a8.isOpen(l8));
        Harness.check("the second is still open", c8.isOpen(l8));

        Harness.section("9. two buttons on ONE trap both control it");
        /* CC1 allows repeated button coordinates and repeated targets. Two buttons wired to the
         * same trap share its state -- which is why setTrap walks every button pointing at the
         * position rather than just the one that was pressed. */
        Path d9 = dir("shared");
        DatBuilder b9 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b9.level().title("Shared")
          .trap(1, 1, 9, 9)
          .trap(2, 2, 9, 9)
          .end();
        Level l9 = firstLevel(b9.writeTo(d9, "shared.dat"));
        BrownButton p1 = brown(l9, 1, 1), p2 = brown(l9, 2, 2);
        Harness.check("both buttons exist", p1 != null && p2 != null);
        Harness.check("they name the same trap",
                      p1 != null && p2 != null
                      && p1.getTargetPosition().equals(p2.getTargetPosition()));
        p1.press(l9);
        Harness.check("pressing one reports the shared trap open from the other",
                      p2 != null && p2.isOpen(l9));

        Harness.section("10. red clones the creature on its machine, and will not duplicate it");
        /* A red button spawns a copy of whatever sits on the connected clone machine. Pressing it
         * again while that copy is still standing on the machine must NOT stack a second one --
         * the guard against that is what stops a held button from filling the level. */
        Path d10 = dir("red");
        DatBuilder b10 = new DatBuilder().signature(DatBuilder.SIG_MS);
        /* Note the cloner() record. A BUTTON_RED tile on the map is NOT enough -- unlike green and
         * blue, red and brown buttons exist only because optional field 5 (or 4) names them, which
         * is the split section 2 pins. Leaving the record out produced a null button here, which is
         * the correct behavior and a fixture mistake. */
        b10.level().title("Red")
           .tile(1, 1, Tile.BUTTON_RED)
           .tile(4, 4, Tile.BUG_UP).under(4, 4, Tile.CLONE_MACHINE)
           .cloner(1, 1, 4, 4)
           .end();
        Level l10 = firstLevel(b10.writeTo(d10, "red.dat"));
        RedButton r10 = red(l10, 1, 1);
        Harness.check("the red button was wired", r10 != null);
        Harness.eq("the creature on the machine is not in the monster list to begin with",
                   l10.getMonsterList().size(), 0);
        if (r10 != null) {
            r10.press(l10);
            int afterFirst = l10.getMonsterList().size() + l10.getMonsterList().getNewClones().size();
            Harness.check("pressing produces a clone (" + afterFirst + ")", afterFirst >= 1);
            r10.press(l10);
            int afterSecond = l10.getMonsterList().size() + l10.getMonsterList().getNewClones().size();
            Harness.eq("pressing again while it is still there adds nothing", afterSecond, afterFirst);
        } else {
            Harness.check("pressing produces a clone", false);
            Harness.check("pressing again while it is still there adds nothing", false);
        }

        Harness.section("11. every button knows where it is, and connection buttons know their target");
        /* Position identity is what lets the engine find the button under Chip's feet. Getting it
         * wrong wires the whole level to the wrong squares, which ConnectionTest checks at the
         * parsing end; this checks the objects themselves. */
        Path d11 = dir("identity");
        DatBuilder b11 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b11.level().title("Identity")
           .tile(2, 3, Tile.BUTTON_GREEN)
           .tile(4, 5, Tile.BUTTON_BLUE)
           .trap(6, 7, 8, 9)
           .cloner(10, 11, 12, 13)
           .end();
        Level l11 = firstLevel(b11.writeTo(d11, "identity.dat"));
        Harness.eq("the green button knows its square",
                   green(l11, 2, 3).getButtonPosition().getIndex(), idx(2, 3));
        Harness.eq("the blue button knows its square",
                   blue(l11, 4, 5).getButtonPosition().getIndex(), idx(4, 5));
        Harness.eq("the brown button knows its square",
                   brown(l11, 6, 7).getButtonPosition().getIndex(), idx(6, 7));
        Harness.eq("and its trap", brown(l11, 6, 7).getTargetPosition().getIndex(), idx(8, 9));
        Harness.eq("the red button knows its square",
                   red(l11, 10, 11).getButtonPosition().getIndex(), idx(10, 11));
        Harness.eq("and its machine", red(l11, 10, 11).getTargetPosition().getIndex(), idx(12, 13));
    }
}
