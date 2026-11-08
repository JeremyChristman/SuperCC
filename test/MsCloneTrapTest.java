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
         * addClone queues into newClones, and MSCreatureList.initialise() -- which runs at the top  // aen-exempt: upstream method names
         * of every tick -- begins with newClones.clear(). So a clone queued from outside the tick
         * cycle is thrown away by the next tick before finalise() ever sees it, and the list never
         * grows.
         *
         * That is not a defect. In a real level nothing presses a button except a creature or Chip
         * standing on it, which happens DURING a tick, after initialise() and before finalise().  // aen-exempt: upstream method names
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
        Harness.eq("but the next tick's initialise clears the queue",  // aen-exempt: upstream method names
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

        /* ================================================================
         * 6. A trap that STARTS with Chip or a block on it starts OPEN
         * ================================================================
         * MSLevel's constructor presses every trap that begins holding Chip, a block or an ice
         * block, because in MSCC every trap is open at level start: whatever begins on one can
         * leave it. (A monster on one does not get this -- section 4's tank stays put.)
         *
         * Nothing tested this before the upstream merge, and that is how upstream's trap rework
         * (858dbd0) got past all 1,878 assertions while breaking 13 real solutions: it pressed these
         * traps before its trap index existed. With ONE wire the press silently did nothing and the
         * trap stayed SHUT; with TWO the press threw and the level would not open at all. Both wire
         * counts are here because they fail in those two different ways.
         *
         * REFERENCE: Tile World's mslogic.c, initgame(), springs every wire whose trap holds Chip or
         * a static block (`xy->to == chippos() || ... top.id == Block_Static` -> springtrap()), so
         * that occupant is released for as long as it stays put. A creature ENTERING a trap is
         * released only if istrapopen() finds a button down, which is why Chip, stepping into the
         * trap the block vacated with no button held, is caught. Also confirmed tick by tick
         * against the deployed jc-14 jar, the build before the rework.
         *
         * ⚠ UPSTREAM BASELINE (jc-19): the fork's fix for this was REVERTED at the maintainer's
         * request, so the fork matches upstream v2.0.3 exactly before any fix is layered back on
         * (FORK.md, "The upstream baseline"). Every assertion of the correct behavior below is an
         * upstreamBug() pinned to upstream's own answer, measured on upstream's code: with one wire
         * the trap stays shut, so a block on it never moves, and neither does Chip, on it or behind it;
         * with two, the level throws NullPointerException on `trapIndexMap` and never opens. (So the
         * one check left, "the trap shuts on Chip", holds under upstream only because the trap never
         * opened.) To re-apply the fix: `git revert` the commit "Upstream baseline: revert the four
         * trap fixes". It holds these test changes too, so the revert turns them back into checks. */
        for (int wires = 1; wires <= 2; wires++) {
            final int w = wires;
            String n = w == 1 ? "one wire" : "two wires";

            Level blockStart = openOrUpstreamCrash("a block starts on a trap with " + n, w == 2, "BlockStart" + w, b -> {
                b.tile(5, 5, Tile.CHIP_RIGHT)
                 .tile(6, 5, Tile.BLOCK).under(6, 5, Tile.TRAP)
                 .tile(2, 2, Tile.BUTTON_BROWN).trap(2, 2, 6, 5);
                if (w == 2) b.tile(3, 9, Tile.BUTTON_BROWN).trap(3, 9, 6, 5);
            });
            if (blockStart != null) {
                Harness.upstreamBug("block on a trap, " + n + ": the trap is open before the first tick",
                              blockStart.isTrapOpen(new Position(6, 5)));
                /* "...can exit them at any point in the level": the end-of-tick update must not shut
                 * an OCCUPIED trap just because no button is held, so the push comes after a wait. */
                step(blockStart, 4);
                Harness.upstreamBug("block on a trap, " + n + ": still open four ticks later",
                              blockStart.isTrapOpen(new Position(6, 5)));
                blockStart.tick('r', new Direction[]{ Direction.RIGHT });
                Harness.upstreamBug("block on a trap, " + n + ": Chip pushes it off",
                           blockStart.getLayerFG().get(new Position(7, 5)), Tile.FLOOR, Tile.BLOCK);
                Harness.upstreamBug("block on a trap, " + n + ": and takes its place",
                           blockStart.getChip().getPosition().getX(), 5, 6);
                /* The start-of-level press is not a held button, so it does not last: Chip, stepping
                 * into the trap the block just left, is caught by it, and cannot walk on. (Traced
                 * tick by tick on jc-14: shut from the moment he enters.) A press that stuck would
                 * let him through, and the trap would stay open for the rest of the level. */
                Harness.check("block on a trap, " + n + ": the trap shuts on Chip as he steps in",
                              !blockStart.isTrapOpen(new Position(6, 5)));
                blockStart.tick('r', new Direction[]{ Direction.RIGHT });
                step(blockStart, 1);
                Harness.upstreamBug("block on a trap, " + n + ": and holds him",
                           blockStart.getChip().getPosition().getX(), 5, 6);
            }

            Level chipStart = openOrUpstreamCrash("Chip starts on a trap with " + n, w == 2, "ChipStart" + w, b -> {
                b.tile(6, 5, Tile.CHIP_RIGHT).under(6, 5, Tile.TRAP)
                 .tile(2, 2, Tile.BUTTON_BROWN).trap(2, 2, 6, 5);
                if (w == 2) b.tile(3, 9, Tile.BUTTON_BROWN).trap(3, 9, 6, 5);
            });
            if (chipStart != null) {
                Harness.upstreamBug("Chip on a trap, " + n + ": the trap is open before the first tick",
                              chipStart.isTrapOpen(new Position(6, 5)));
                step(chipStart, 4);
                chipStart.tick('r', new Direction[]{ Direction.RIGHT });
                Harness.upstreamBug("Chip on a trap, " + n + ": he walks straight off it",
                           chipStart.getChip().getPosition().getX(), 6, 7);
            }
        }

        /* ================================================================
         * 7. An EMPTY trap with two buttons, one held: open, whichever button is visited first
         * ================================================================
         * Before 858dbd0 every wire had its own bit and a trap was open if any of its bits was
         * set, so this trap was open. 858dbd0 gave each trap ONE bit but kept the end-of-tick
         * update wire by wire, so the last wire visited decided it -- and the visiting order is a
         * hash map's. Measured on that build: shut in three of these six arrangements. jc-15 to
         * jc-18 decided each trap once, after all its wires (in MSLevel.finaliseTraps); see below.
         *
         * Six arrangements, each pair in both roles, because which order a given pair comes out in
         * is a property of the hash, not of the coordinates: a single fixture could pass by luck.
         * The trap is reached through BrownButton.isOpen, the same question the engine asks. No
         * real replay was seen to depend on it (a creature entering a trap re-checks its buttons),
         * so this pins the rule itself rather than a known desync.
         *
         * REFERENCE: Tile World's mslogic.c, istrapopen(): a trap is passable if ANY wire into it
         * comes from a button that is down (`istrapbuttondown(traps->from)`).
         *
         * ⚠ UPSTREAM BASELINE (jc-19): this fix was REVERTED to match upstream v2.0.3 (FORK.md, "The
         * upstream baseline"). Upstream's answer depends on hash order, so each arrangement is pinned
         * to what upstream gives: the three marked below come out SHUT (upstreamBug), the other three
         * open. Re-applying the fix makes all six open, and the three pins fail as the cue. The order
         * is fixed for a given Position.hashCode, map capacity and JDK (16.0.2, as CI pins); if one of
         * those moves, ANY of the six can fail with no fix and no engine change involved -- the three
         * plain checks too, which fail without saying so. Rule that out before blaming the engine. */
        int[][] pairs = { {2, 2, 3, 9}, {3, 9, 2, 2}, {20, 1, 1, 20}, {1, 20, 20, 1}, {9, 9, 10, 9}, {10, 9, 9, 9} };
        java.util.Set<Integer> upstreamShut = java.util.Set.of(0, 2, 4);   // measured on upstream's code
        for (int i = 0; i < pairs.length; i++) {
            int[] p = pairs[i];
            String n = "held (" + p[0] + "," + p[1] + "), free (" + p[2] + "," + p[3] + ")";
            Level mixed = msLevel("Mixed", b -> b
                    .tile(15, 15, Tile.CHIP_DOWN)
                    .tile(6, 5, Tile.TRAP)
                    .tile(p[0], p[1], Tile.BLOCK).under(p[0], p[1], Tile.BUTTON_BROWN)
                    .tile(p[2], p[3], Tile.BUTTON_BROWN)
                    .trap(p[0], p[1], 6, 5)
                    .trap(p[2], p[3], 6, 5));
            step(mixed, 1);
            BrownButton free = brown(mixed, p[2], p[3]);
            if (upstreamShut.contains(i))
                Harness.upstreamBug("empty trap, " + n + ": open after a tick", free != null && free.isOpen(mixed));
            else
                Harness.check("empty trap, " + n + ": open after a tick", free != null && free.isOpen(mixed));
        }

        /* Neither held: the empty trap is shut. Without this, "always open" passes the six above. */
        Level neither = msLevel("Neither", b -> b
                .tile(15, 15, Tile.CHIP_DOWN)
                .tile(6, 5, Tile.TRAP)
                .tile(2, 2, Tile.BUTTON_BROWN).tile(3, 9, Tile.BUTTON_BROWN)
                .trap(2, 2, 6, 5).trap(3, 9, 6, 5));
        step(neither, 1);
        Harness.check("empty trap, neither button held: shut after a tick",
                      !neither.isTrapOpen(new Position(6, 5)));

        /* A press with nothing ON the button -- what the Cheats menu's "press brown button" does --
         * opens the trap only until the end of the tick: the update sees an empty trap and no held
         * button, and shuts it. That end-of-tick clear is the only thing that shuts it here (a
         * release would, but nothing is released), so this is the assertion that pins it. */
        BrownButton bare = brown(neither, 2, 2);
        if (bare != null) bare.press(neither);
        Harness.check("a bare press opens the empty trap", bare != null && bare.isOpen(neither));
        step(neither, 1);
        Harness.check("but nothing holds it, so the next tick shuts it again",
                      !neither.isTrapOpen(new Position(6, 5)));

        /* The other direction: ONE button wired to TWO traps. The end-of-tick update lets a held
         * button open only the first trap in its own list (record order) -- pressedButtons in
         * MSLevel.finaliseTraps. Moving that loop around for Fix 2 must not lose it.
         * REFERENCE: Tile World's springtrap() goes through trapfrombutton(), which returns the
         * FIRST wire from that button and stops. */
        Level fanout = msLevel("Fanout", b -> b
                .tile(15, 15, Tile.CHIP_DOWN)
                .tile(6, 5, Tile.TRAP).tile(9, 7, Tile.TRAP)
                .tile(2, 2, Tile.BLOCK).under(2, 2, Tile.BUTTON_BROWN)
                .trap(2, 2, 6, 5)
                .trap(2, 2, 9, 7));
        step(fanout, 1);
        Harness.check("one held button, two traps: the FIRST wired is open",
                      fanout.isTrapOpen(new Position(6, 5)));
        Harness.check("one held button, two traps: the second is not",
                      !fanout.isTrapOpen(new Position(9, 7)));

        /* ================================================================
         * 8. Two buttons, one trap: a release shuts it even with the other button held
         * ================================================================
         * Pinned because 858dbd0 changed how it is STORED (one bit per trap, not per wire) and the
         * answer must not change with it. Before, release() cleared the bit of every wire to the
         * trap, so the trap shut even while the other button was held; one bit per trap gives the
         * same. Confirmed against jc-14.
         * REFERENCE: this is SuperCC's model, not Tile World's stock one (whose istrapopen() would
         * read the other button and stay open). The desync work settled it in SuperCC's favor:
         * Jeremy's tworld adds FIX_TRAP_SHUT_BEHIND to mslogic.c to honor exactly this release,
         * measured on PB_Gourami #254, where stock Tile World diverged and the level failed. */
        Level shared = msLevel("Shared", b -> b
                .tile(15, 15, Tile.CHIP_DOWN)
                .tile(6, 5, Tile.TANK_RIGHT).under(6, 5, Tile.TRAP).monster(6, 5)
                .tile(2, 2, Tile.BUTTON_BROWN).tile(3, 9, Tile.BUTTON_BROWN)
                .trap(2, 2, 6, 5).trap(3, 9, 6, 5));
        BrownButton first = brown(shared, 2, 2), second = brown(shared, 3, 9);
        Harness.check("both buttons of the shared trap were wired", first != null && second != null);
        if (first != null && second != null) {
            Harness.check("the shared trap starts shut", !first.isOpen(shared));
            first.press(shared);
            Harness.check("pressing either button opens it, seen through either button",
                          first.isOpen(shared) && second.isOpen(shared));
            second.release(shared);
            Harness.check("releasing the OTHER button shuts it again", !first.isOpen(shared));
        }

        /* ================================================================
         * 9. Upstream's own regression level for 858dbd0, rebuilt
         * ================================================================
         * 858dbd0 added level 25, "Multi Trap", to upstream's unitTest.dat, with the solution
         * ----r-r-r. A .dat may not be committed here, so it is rebuilt cell for cell from the
         * decoded original. Two traps, each wired to two buttons with a block holding ONE of them;
         * a ball drops through each trap onto a red button, whose clone fills the water Chip has to
         * cross. If a trap holds its ball, Chip drowns. Replayed the way SuperCC's emulator does it:
         * a move that ticks twice takes the next tick too. */
        Level multi = msLevel("Multi Trap", b -> b
                .tile(0, 0, Tile.BLOCK).under(0, 0, Tile.BUTTON_BROWN).tile(1, 0, Tile.BUTTON_BROWN)
                .tile(3, 0, Tile.BUTTON_BROWN).tile(4, 0, Tile.BLOCK).under(4, 0, Tile.BUTTON_BROWN)
                .tile(0, 1, Tile.BALL_DOWN).tile(3, 1, Tile.BALL_DOWN)
                .tile(0, 2, Tile.TRAP).tile(3, 2, Tile.TRAP)
                .tile(0, 3, Tile.BUTTON_RED).tile(3, 3, Tile.BUTTON_RED)
                .tile(0, 4, Tile.WATER).tile(1, 4, Tile.WATER).tile(2, 4, Tile.WATER)
                .tile(3, 4, Tile.WATER).tile(4, 4, Tile.WATER)
                .tile(0, 5, Tile.WALL).tile(1, 5, Tile.WALL)
                .tile(2, 5, Tile.BLOCK_DOWN).under(2, 5, Tile.CLONE_MACHINE)
                .tile(3, 5, Tile.BLOCK_DOWN).under(3, 5, Tile.CLONE_MACHINE)
                .tile(4, 5, Tile.WALL).tile(5, 5, Tile.WALL)
                .tile(0, 6, Tile.WALL).tile(1, 6, Tile.CHIP_DOWN).tile(2, 6, Tile.WATER)
                .tile(3, 6, Tile.WATER).tile(4, 6, Tile.EXIT).tile(5, 6, Tile.WALL)
                .tile(0, 7, Tile.WALL).tile(1, 7, Tile.WALL).tile(2, 7, Tile.WALL)
                .tile(3, 7, Tile.WALL).tile(4, 7, Tile.WALL).tile(5, 7, Tile.WALL)
                .trap(0, 0, 0, 2).trap(1, 0, 0, 2).trap(3, 0, 3, 2).trap(4, 0, 3, 2)
                .cloner(0, 3, 2, 5).cloner(3, 3, 3, 5)
                .monster(0, 1).monster(3, 1));
        replay(multi, "----r-r-r");
        Harness.check("Multi Trap: upstream's solution reaches the exit", multi.isCompleted());
        Harness.eq("Multi Trap: on the exit cell", multi.getChip().getPosition().getX(), 4);

        /* ================================================================
         * 10. A trap's savestate bit should be its FIRST wire's record index -- the bit jc-14 used
         * ================================================================
         * "Save all states to disk" writes savestates into a .ser file, trap bits included, so the
         * NUMBER a trap is stored under is a file format. Before 858dbd0 each wire had a bit,
         * numbered by its record in the .dat. Upstream's rework numbered traps by HashMap order;
         * jc-15 to jc-18 numbered each trap by its first wire's record instead (a map built in
         * LevelFactory, removed for the baseline), so a .ser from jc-14 or earlier loaded right
         * wherever a trap has one wire, and what they saved loads right in jc-14.
         *
         * Read straight out of the savestate bytes, at the offset MSSavestate.save() documents --
         * if that layout ever moves, this fails loudly, which is wanted: so would every .ser.
         *
         * ⚠ UPSTREAM BASELINE (jc-19): this fix was REVERTED to match upstream v2.0.3, which numbers
         * traps by HashMap order (FORK.md, "The upstream baseline"). The assertions upstream's
         * numbering fails are pinned with upstreamBug, at upstream's measured answer (the one-wire
         * trap is bit 1, and jc-14's bits 0 and 1 open BOTH traps); the others happen to agree. */
        Level saved = msLevel("Saved", b -> b
                .tile(15, 15, Tile.CHIP_DOWN)
                .tile(21, 21, Tile.TRAP).tile(2, 2, Tile.TRAP)
                .tile(20, 20, Tile.BUTTON_BROWN).tile(1, 1, Tile.BUTTON_BROWN).tile(5, 9, Tile.BUTTON_BROWN)
                .trap(20, 20, 21, 21)       // record 0
                .trap(1, 1, 21, 21)         // record 1: a second wire into the same trap
                .trap(5, 9, 2, 2));         // record 2
        byte[] blank = saved.save();
        Harness.eq("the savestate is the uncompressed V2 layout this test reads",
                   (int) blank[0], (int) game.Savestate.UNCOMPRESSED_V2);
        Harness.eq("nothing is open at the start", trapBits(blank), "{}");

        brown(saved, 5, 9).press(saved);
        Harness.upstreamBug("a trap with one wire, at record 2, is saved as bit 2", trapBits(saved.save()), "{1}", "{2}");
        brown(saved, 5, 9).release(saved);
        brown(saved, 20, 20).press(saved);
        Harness.eq("a trap with two wires is saved under its FIRST wire's record, bit 0",
                   trapBits(saved.save()), "{0}");

        /* And the other direction: what jc-14 wrote. For an open trap with two wires it set BOTH
         * wires' bits; for the one-wire trap, its record's bit. */
        saved.load(withTrapBits(blank, 0, 1));
        Harness.check("a jc-14 state with the shared trap open loads it open",
                      saved.isTrapOpen(new Position(21, 21)));
        Harness.upstreamBug("and leaves the other trap shut", !saved.isTrapOpen(new Position(2, 2)));
        saved.load(withTrapBits(blank, 2));
        Harness.upstreamBug("a jc-14 state with the one-wire trap open loads THAT one open",
                      saved.isTrapOpen(new Position(2, 2)) && !saved.isTrapOpen(new Position(21, 21)));
    }

    /** Offset of the trap-bit length in an MS savestate, per MSSavestate.save()'s layout: version,
     *  ruleset, chip (2), both layers (1024 each), tick (4), chips left (2), keys (8), boots (4),
     *  RNG (4), mouse goal (2). The length is a big-endian short; the BitSet's bytes follow. */
    private static final int TRAPS_AT = 1 + 1 + 2 + 1024 + 1024 + 4 + 2 + 8 + 4 + 4 + 2;

    private static String trapBits(byte[] state) {
        int len = ((state[TRAPS_AT] & 0xff) << 8) | (state[TRAPS_AT + 1] & 0xff);
        return java.util.BitSet.valueOf(java.util.Arrays.copyOfRange(state, TRAPS_AT + 2, TRAPS_AT + 2 + len)).toString();
    }

    /** The same savestate with its trap bits replaced by exactly these. */
    private static byte[] withTrapBits(byte[] state, int... bits) {
        int oldLen = ((state[TRAPS_AT] & 0xff) << 8) | (state[TRAPS_AT + 1] & 0xff);
        java.util.BitSet set = new java.util.BitSet();
        for (int bit : bits) set.set(bit);
        byte[] trap = set.toByteArray();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(state, 0, TRAPS_AT);
        out.write(trap.length >>> 8);
        out.write(trap.length);
        out.write(trap, 0, trap.length);
        int rest = TRAPS_AT + 2 + oldLen;
        out.write(state, rest, state.length - rest);
        return out.toByteArray();
    }

    /** Opens a level, or returns null if opening it throws, so the rest of the file still runs.
     *  Where `upstreamCrash` is true the level is one upstream v2.0.3 cannot open -- a trap with two
     *  or more wires that starts occupied -- and the upstream baseline keeps that (FORK.md): the
     *  crash is pinned with Harness.upstreamBug at upstream's OWN failure, so a level that opens
     *  fails as the cue that the fix is back, and any other throw fails as a regression. Otherwise
     *  the level must open. The name is the same whatever happens; what was thrown is in the detail. */
    private static Level openOrUpstreamCrash(String what, boolean upstreamCrash, String name,
                                             java.util.function.Consumer<DatBuilder.Level> f) {
        Level l = null;
        String outcome;
        try {
            l = msLevel(name, f);
            outcome = "opens";
        } catch (NullPointerException e) {
            outcome = String.valueOf(e.getMessage()).contains("trapIndexMap")
                    ? UPSTREAM_TRAP_CRASH : "throws " + e;
        } catch (Throwable t) {
            outcome = "throws " + t;
        }
        if (upstreamCrash) Harness.upstreamBug(what + ": the level opens", outcome, UPSTREAM_TRAP_CRASH, "opens");
        else Harness.eq(what + ": the level opens", outcome, "opens");
        return l;
    }

    /** Upstream v2.0.3's failure on such a level: its constructor presses the start-occupied trap's
     *  second wire before the trap index exists. */
    private static final String UPSTREAM_TRAP_CRASH = "throws NullPointerException (trapIndexMap is null)";

    /** SuperCC's MS replay, as emulator.SuperCC.tick does it: a move whose tick returns true
     *  (it ticks more than once) is followed by a NONE tick with the capitalized character, and
     *  that tick consumes the next character of the route. */
    private static void replay(Level lv, String moves) {
        for (int i = 0; i < moves.length(); i++) {
            char c = moves.charAt(i);
            Direction d = switch (c) {
                case 'u' -> Direction.UP;
                case 'd' -> Direction.DOWN;
                case 'l' -> Direction.LEFT;
                case 'r' -> Direction.RIGHT;
                default  -> Direction.NONE;
            };
            if ((lv.tick(c, new Direction[]{ d }) & game.Level.MASK_TICK_MULTI) != 0) {
                lv.tick(emulator.SuperCC.capital(c), new Direction[]{ Direction.NONE });
                i++;
            }
            if (lv.getChip().isDead()) break;
        }
    }
}
