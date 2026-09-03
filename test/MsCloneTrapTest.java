import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Step;
import game.Tile;
import game.button.BrownButton;
import game.button.RedButton;

import java.nio.file.Path;
import java.util.List;

/**
 * MS clone machines and beartraps -- the dispatch, not the wiring.
 *
 * WHAT IS ALREADY COVERED, AND WHERE THIS PICKS UP
 * -------------------------------------------------
 * Three files already touch this machinery and each stops in a different place:
 *
 *   ConnectionTest  the .dat records -- field 4 traps, field 5 cloners, and the stride arithmetic
 *   ButtonTest §10  a red button produces a clone, and pressing again does not stack a second
 *   ButtonTest §7   a brown button holds its trap open and closes it on release
 *
 * All of that is the WIRING. None of it drives a creature through the machinery afterwards, and
 * MSCreatureList's three dispatch paths -- `tickClonedMonster`, `tickTrappedMonster`, and the
 * `addClone` preconditions -- were the concentrated gap MsCreatureListTest left behind. That is
 * this file.
 *
 * THE PRECONDITION THAT MAKES A CLONE MACHINE A MACHINE
 * ------------------------------------------------------
 * addClone does not simply copy whatever is on the cloner. It computes where the copy would go and
 * refuses unless it can get there:
 *
 *     if (clone.canEnter(direction, newTile) || newTile == clone.toTile())
 *
 * So a cloner facing a wall is inert, and one whose previous clone has not moved off is inert too
 * -- the second is ButtonTest's duplicate guard, the first is not tested anywhere. Together they
 * are why a held red button does not fill a level with monsters, and why a level designer can point
 * a cloner at a wall to disable it.
 *
 * A CREATURE ON A TRAP IS NOT A CREATURE THAT IS STUCK
 * -----------------------------------------------------
 * MSCreatureList.tick dispatches on the BACKGROUND tile: clone machine, beartrap, or neither. A
 * creature on a closed trap still gets its turn -- it is `tickTrappedMonster` that runs, and that
 * path forces the creature's own direction rather than asking for a preference list. The difference
 * only shows once the trap opens, which is why this drives a brown button rather than inspecting
 * flags.
 */
public class MsCloneTrapTest {

    public static void main(String[] args) {
        System.exit(Harness.run("MsCloneTrapTest", MsCloneTrapTest::body));
    }

    private static Level msLevel(String name, java.util.function.Consumer<DatBuilder.Level> f)
            throws Exception {
        Path d = Harness.tempDir("scc-msclone-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        DatBuilder.Level lv = b.level().title(name).password("ABCD");
        f.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, 0, Step.EVEN, game.Ruleset.MS, Direction.UP);
    }

    private static void step(Level lv, int ticks) {
        for (int i = 0; i < ticks; i++) lv.tick('-', new Direction[]{ Direction.NONE });
    }

    private static RedButton red(Level l, int x, int y) {
        List<RedButton> b = l.getRedButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    private static BrownButton brown(Level l, int x, int y) {
        List<BrownButton> b = l.getBrownButtons().getList(new Position(x, y));
        return (b == null || b.isEmpty()) ? null : b.get(0);
    }

    /** Everything the list holds right now, live entries plus clones queued for finalise. */
    private static int population(Level l) {
        return l.getMonsterList().size() + l.getMonsterList().getNewClones().size();
    }

    private static Creature monsterOf(Level lv, CreatureID id) {
        for (int i = 0; i < lv.getMonsterList().size(); i++)
            if (lv.getMonsterList().get(i).getCreatureType() == id) return lv.getMonsterList().get(i);
        return null;
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. A cloner that cannot discharge is inert
         * ================================================================
         * The bug faces UP, so the copy would go to 4,3. Put a wall there and addClone's
         * canEnter test refuses before anything is created. ButtonTest covers the duplicate guard;
         * this is the other precondition, and nothing covered it. */

        Level blocked = msLevel("Blocked", b -> b
                .tile(1, 1, Tile.BUTTON_RED)
                .tile(4, 4, Tile.BUG_UP).under(4, 4, Tile.CLONE_MACHINE)
                .tile(4, 3, Tile.WALL)
                .cloner(1, 1, 4, 4));
        RedButton rb = red(blocked, 1, 1);
        Harness.check("the red button was wired", rb != null);
        Harness.eq("nothing is listed before the press", population(blocked), 0);
        rb.press(blocked);
        Harness.eq("a cloner facing a wall produces NOTHING", population(blocked), 0);
        Harness.eq("and the machine still holds its bug",
                   blocked.getLayerFG().get(new Position(4, 4)), Tile.BUG_UP);

        /* The same fixture with the wall removed does produce one, so the refusal above is the
         * wall and not something wrong with the fixture. */
        Level clear = msLevel("Clear", b -> b
                .tile(1, 1, Tile.BUTTON_RED)
                .tile(4, 4, Tile.BUG_UP).under(4, 4, Tile.CLONE_MACHINE)
                .cloner(1, 1, 4, 4));
        RedButton rc = red(clear, 1, 1);
        rc.press(clear);
        Harness.eq("with the way clear, the same press produces one", population(clear), 1);

        /* ================================================================
         * 2. A clone only SURVIVES if the button is pressed during a tick
         * ================================================================
         * addClone queues into newClones, and MSCreatureList.initialise() -- which runs at the top
         * of every tick -- begins with newClones.clear(). So a clone queued from outside the tick
         * cycle is thrown away by the next tick before finalise() ever sees it, and the list never
         * grows.
         *
         * That is not a defect. In a real level nothing presses a button except a creature or Chip
         * standing on it, which happens DURING a tick, after initialise() and before finalise().
         * But it means the population counted immediately after a bare press() -- which is what
         * section 1 and ButtonTest section 10 measure -- is a transient that does not necessarily
         * become a creature. Both halves are pinned here so the difference is on the record. */

        Level discarded = msLevel("Discarded", b -> b
                .tile(1, 1, Tile.BUTTON_RED)
                .tile(4, 8, Tile.BUG_UP).under(4, 8, Tile.CLONE_MACHINE)
                .cloner(1, 1, 4, 8));
        red(discarded, 1, 1).press(discarded);
        Harness.eq("a bare press queues one", discarded.getMonsterList().getNewClones().size(), 1);
        step(discarded, 1);
        Harness.eq("but the next tick's initialise clears the queue",
                   discarded.getMonsterList().getNewClones().size(), 0);
        Harness.eq("and it never reached the live list",
                   discarded.getMonsterList().size(), 0);

        /* Driven the way the game does it: Chip walks onto the button. Now the press lands inside
         * the tick and the clone is finalised into the list, where it stays. */
        Level walked = msLevel("Walked", b -> b
                .tile(1, 1, Tile.CHIP_RIGHT)
                .tile(2, 1, Tile.BUTTON_RED)
                .tile(4, 8, Tile.BUG_UP).under(4, 8, Tile.CLONE_MACHINE)
                .cloner(2, 1, 4, 8));
        Harness.eq("nothing is listed before Chip reaches the button",
                   walked.getMonsterList().size(), 0);
        walked.tick('r', new Direction[]{ Direction.RIGHT });
        Harness.eq("Chip stepped onto the button", walked.getChip().getPosition().getX(), 2);
        Harness.eq("and the clone is in the LIVE list, not the queue",
                   walked.getMonsterList().size(), 1);
        Harness.eq("with the queue emptied by finalise",
                   walked.getMonsterList().getNewClones().size(), 0);

        step(walked, 6);
        Harness.eq("and it is still there several ticks later", walked.getMonsterList().size(), 1);
        Harness.eq("the machine still holds its template bug",
                   walked.getLayerFG().get(new Position(4, 8)), Tile.BUG_UP);

        /* Press it AGAIN with the first clone now in the live list rather than the queue.
         *
         * addClone has two duplicate guards, one scanning `list` and one scanning `newClones`, and
         * which of them fires depends on where the previous clone currently is. ButtonTest's
         * fixture only ever reaches the newClones one -- deleting the list guard passes every
         * assertion there, verified by planting it. This reaches the other. */
        walked.tick('l', new Direction[]{ Direction.LEFT });      // step off the button
        walked.tick('r', new Direction[]{ Direction.RIGHT });     // and back onto it
        Harness.eq("pressing again, with the first clone LIVE, still adds nothing",
                   walked.getMonsterList().size(), 1);

        /* ================================================================
         * 3. Blocks clone down a different path from monsters
         * ================================================================
         * addClone sends a block straight to tickClonedMonster and queues everything else in
         * newClones for finalise. The two are observable apart: right after the press, a monster is
         * waiting in the queue and a block is not. */

        Level monsterClone = msLevel("MonClone", b -> b
                .tile(1, 1, Tile.BUTTON_RED)
                .tile(6, 8, Tile.GLIDER_UP).under(6, 8, Tile.CLONE_MACHINE)
                .cloner(1, 1, 6, 8));
        red(monsterClone, 1, 1).press(monsterClone);
        Harness.eq("a cloned MONSTER waits in the queue until finalise",
                   monsterClone.getMonsterList().getNewClones().size(), 1);

        Level blockClone = msLevel("BlkClone", b -> b
                .tile(1, 1, Tile.BUTTON_RED)
                .tile(6, 8, Tile.BLOCK_UP).under(6, 8, Tile.CLONE_MACHINE)
                .cloner(1, 1, 6, 8));
        red(blockClone, 1, 1).press(blockClone);
        Harness.eq("a cloned BLOCK does not -- it is ticked immediately instead",
                   blockClone.getMonsterList().getNewClones().size(), 0);

        /* ================================================================
         * 4. A creature on a beartrap
         * ================================================================
         * The trap starts closed. tick() still dispatches the creature, through
         * tickTrappedMonster rather than tickFreeMonster, and the visible difference is that it
         * does not go anywhere until a brown button opens the trap. */

        Level trapped = msLevel("Trapped", b -> b
                .tile(1, 2, Tile.BUTTON_BROWN)
                .tile(8, 8, Tile.TANK_RIGHT).under(8, 8, Tile.TRAP).monster(8, 8)
                .trap(1, 2, 8, 8));
        BrownButton bb = brown(trapped, 1, 2);
        Harness.check("the brown button was wired", bb != null);
        Creature tank = monsterOf(trapped, CreatureID.TANK_MOVING);
        Harness.check("the trapped tank is in the list", tank != null);
        Harness.check("the trap starts closed", !bb.isOpen(trapped));

        Position held = tank.getPosition();
        step(trapped, 8);
        Harness.eq("a tank on a CLOSED trap does not move, however long you wait",
                   tank.getPosition().getX() + "," + tank.getPosition().getY(),
                   held.getX() + "," + held.getY());

        bb.press(trapped);
        Harness.check("pressing the brown button opens the trap", bb.isOpen(trapped));
        step(trapped, 8);
        Harness.check("and now the tank leaves",
                      !(tank.getPosition().getX() == held.getX()
                        && tank.getPosition().getY() == held.getY()));

        /* ================================================================
         * 5. springTrappedCreature refuses the wrong square, quietly
         * ================================================================
         * It is called from button handling with a target that may be stale, may hold nothing, or
         * may name a trap that is shut. Each guard is a branch a real level reaches only in unusual
         * states, so they are exercised directly. */

        Level guards = msLevel("Guards", b -> b
                .tile(1, 2, Tile.BUTTON_BROWN)
                .tile(8, 8, Tile.TANK_RIGHT).under(8, 8, Tile.TRAP).monster(8, 8)
                .tile(12, 12, Tile.FLOOR)
                .trap(1, 2, 8, 8));
        game.CreatureList gl = guards.getMonsterList();
        Creature gTank = monsterOf(guards, CreatureID.TANK_MOVING);
        Position gHeld = gTank.getPosition();

        gl.springTrappedCreature(new Position(12, 12));
        Harness.eq("springing a square that is not a trap moves nobody",
                   gTank.getPosition().getX() + "," + gTank.getPosition().getY(),
                   gHeld.getX() + "," + gHeld.getY());

        gl.springTrappedCreature(new Position(20, 20));
        Harness.eq("springing an empty square moves nobody",
                   gTank.getPosition().getX() + "," + gTank.getPosition().getY(),
                   gHeld.getX() + "," + gHeld.getY());

        /* The trap is real and occupied, but shut.
         *
         * ⚠ This assertion does NOT isolate springTrappedCreature's `!level.isTrapOpen` guard, and
         * saying so matters more than the assertion does. Deleting that guard changes no result
         * here, and it is an EQUIVALENT MUTANT rather than a hole: the rule that actually holds a
         * creature in a shut trap lives one layer down, in MSCreature.canLeave --
         *
         *     case TRAP -> level.isTrapOpen(position);
         *
         * -- so a creature sprung from a closed trap still cannot go anywhere. The guard in
         * springTrappedCreature is a cheap early-out in front of a refusal that would happen
         * regardless. The behavior it stands for IS covered, by section 4, which walks a tank
         * through a real tick and finds it still on its closed trap.
         *
         * The ticks below are still needed: springTrappedCreature drives the creature through the
         * shared `direction` field, which is null until a tick has set it, so without them this
         * would be a no-op for a third reason again. */
        step(guards, 4);
        gHeld = gTank.getPosition();
        Harness.check("the trap under the tank is shut", !guards.isTrapOpen(new Position(8, 8)));
        gl.springTrappedCreature(new Position(8, 8));
        Harness.eq("springing a SHUT trap moves nobody either",
                   gTank.getPosition().getX() + "," + gTank.getPosition().getY(),
                   gHeld.getX() + "," + gHeld.getY());

        /* And with the trap open it does move, so the three refusals above are the guards and not
         * a fixture that could never move at all. */
        brown(guards, 1, 2).press(guards);
        Harness.check("with the button held the trap is open", guards.isTrapOpen(new Position(8, 8)));
        gl.springTrappedCreature(new Position(8, 8));
        Harness.check("and springing it now DOES move the tank",
                      !(gTank.getPosition().getX() == gHeld.getX()
                        && gTank.getPosition().getY() == gHeld.getY()));
    }
}
