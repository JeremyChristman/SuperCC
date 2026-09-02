import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Tile;

/**
 * MS arrival effects -- what a tile DOES to whoever steps on it. MSCreature.tryEnter().
 *
 * WHY THIS AND NOT MORE canEnter
 * -------------------------------
 * MS splits the question in two, and the two halves already had very different amounts of testing.
 * `canEnter` answers "is this move legal" and CanEnterTest covers it in 99 assertions, including
 * the rule that fire refuses a bug and a walker. `tryEnter` is the other half -- 365 lines, the
 * largest single method in the repo -- and it answers "what happens now that you are here". None of
 * it was covered. This file is that half, and it deliberately does not re-litigate legality.
 *
 * WHERE MS AND LYNX GENUINELY DIFFER, which is most of the value here
 * -------------------------------------------------------------------
 * LynxTickTest covers the same idea for the other ruleset, and the two are not the same rule with
 * different names. MS MUTATES THE MAP where Lynx mostly does not:
 *
 *     Chip drowns   ->  the tile becomes DROWNED_CHIP        Lynx has no such tile at all
 *     Chip burns    ->  the tile becomes BURNED_CHIP         likewise
 *     block + water ->  DIRT                                 same in both
 *     ice block + water -> ICE                               Lynx's canEnter calls ICE_BLOCK
 *     ice block + fire  -> WATER                             "Doesn't exist in lynx"
 *
 * Those corpse tiles are why `CanEnterTest` and `LynxCanEnterTest` disagree about DROWNED_CHIP and
 * friends: in MS they are real map contents that the engine writes, and in Lynx they are dead
 * entries that admit nobody. A test that only looked at one ruleset would make either behavior look
 * like the obvious one.
 *
 * THE ORACLE
 * ----------
 * mslogic.c splits its arrival handling the same way SuperCC does, into a Chip switch and a
 * creature switch. The creature half is blunt:
 *
 *     case Water:  if (crid != Glider)   dead = TRUE;
 *     case Fire:   if (crid != Fireball) dead = TRUE;
 *     case Bomb:   cell->top.id = Empty; dead = TRUE;
 *
 * SuperCC reaches the same outcomes through a per-creature switch, because it also has to model the
 * block and ice-block cases that Tile World handles before it ever gets here. Where the two are
 * genuinely the same rule the assertions say so; where SuperCC is doing bookkeeping Tile World does
 * elsewhere, the comment says that instead of pretending it is a parity check.
 */
public class MsTryEnterTest {

    public static void main(String[] args) {
        System.exit(Harness.run("MsTryEnterTest", MsTryEnterTest::body));
    }

    private static Level build(String name, java.util.function.Consumer<DatBuilder.Level> f)
            throws Exception {
        java.nio.file.Path d = Harness.tempDir("scc-mstry-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        DatBuilder.Level lv = b.level().title(name).password("ABCD");
        f.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, 0, game.Step.EVEN, game.Ruleset.MS, Direction.UP);
    }

    private static char ch(Direction d) {
        switch (d) {
            case UP: return 'u'; case LEFT: return 'l';
            case DOWN: return 'd'; case RIGHT: return 'r';
            default: return '-';
        }
    }

    private static void step(Level lv, Direction d, int ticks) {
        for (int i = 0; i < ticks; i++) lv.tick(ch(d), new Direction[]{ d });
    }

    private static Tile at(Level lv, int x, int y) {
        return lv.getLayerFG().get(new Position(x, y));
    }

    /** Chip at 4,5; the tile under test at 5,5; walk him onto it. */
    private static Level chipOnto(Tile target, java.util.function.Consumer<Level> prepare)
            throws Exception {
        Level lv = build("Walk", b -> b.tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, target));
        if (prepare != null) prepare.accept(lv);
        step(lv, Direction.RIGHT, 6);
        return lv;
    }

    private static Creature monsterOf(Level lv, CreatureID id) {
        for (int i = 0; i < lv.getMonsterList().size(); i++)
            if (lv.getMonsterList().get(i).getCreatureType() == id) return lv.getMonsterList().get(i);
        return null;
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. Chip, water and fire -- and the corpse tiles MS leaves behind
         * ================================================================ */

        Level drown = chipOnto(Tile.WATER, null);
        Harness.check("Chip drowns in water without flippers", drown.getChip().isDead());
        Harness.eq("and MS writes DROWNED_CHIP onto the tile -- Lynx has no such tile",
                   at(drown, 5, 5), Tile.DROWNED_CHIP);

        Level swim = chipOnto(Tile.WATER, lv -> lv.getBoots()[0] = 1);
        Harness.check("flippers carry him across", !swim.getChip().isDead());
        Harness.eq("and the water is left as water", at(swim, 5, 5), Tile.WATER);

        Level burn = chipOnto(Tile.FIRE, null);
        Harness.check("Chip burns in fire without fire boots", burn.getChip().isDead());
        Harness.eq("and MS writes BURNED_CHIP onto the tile",
                   at(burn, 5, 5), Tile.BURNED_CHIP);

        Level fireproof = chipOnto(Tile.FIRE, lv -> lv.getBoots()[1] = 1);
        Harness.check("fire boots carry him across", !fireproof.getChip().isDead());
        Harness.eq("and the fire is left as fire", at(fireproof, 5, 5), Tile.FIRE);

        /* ================================================================
         * 2. Chip's other arrivals
         * ================================================================ */

        Harness.eq("dirt is cleared to floor", at(chipOnto(Tile.DIRT, null), 5, 5), Tile.FLOOR);
        Harness.eq("a fake blue wall is cleared to floor",
                   at(chipOnto(Tile.BLUEWALL_FAKE, null), 5, 5), Tile.FLOOR);
        Harness.eq("a pop-up wall closes behind him",
                   at(chipOnto(Tile.POP_UP_WALL, null), 5, 5), Tile.WALL);

        Level bomb = chipOnto(Tile.BOMB, null);
        Harness.check("a bomb kills Chip", bomb.getChip().isDead());

        Level thief = chipOnto(Tile.THIEF, lv -> {
            lv.getBoots()[0] = 1; lv.getBoots()[1] = 1; lv.getBoots()[2] = 1; lv.getBoots()[3] = 1;
        });
        Harness.eq("a thief takes every boot",
                   java.util.Arrays.toString(thief.getBoots()), "[0, 0, 0, 0]");

        Tile[] keys = { Tile.KEY_BLUE, Tile.KEY_RED, Tile.KEY_GREEN, Tile.KEY_YELLOW };
        for (int i = 0; i < keys.length; i++) {
            Level k = chipOnto(keys[i], null);
            Harness.eq("Chip picks up " + keys[i], k.getKeys()[i], (short) 1);
            Harness.eq("and the tile is cleared", at(k, 5, 5), Tile.FLOOR);
        }

        Tile[] boots = { Tile.BOOTS_WATER, Tile.BOOTS_FIRE, Tile.BOOTS_ICE, Tile.BOOTS_FF };
        for (int i = 0; i < boots.length; i++) {
            Level bt = chipOnto(boots[i], null);
            Harness.eq("Chip picks up " + boots[i], bt.getBoots()[i], (byte) 1);
            Harness.eq("and the tile is cleared", at(bt, 5, 5), Tile.FLOOR);
        }

        /* Doors consume their key -- except green, which is reusable. Same rule as Lynx, and the
         * same line in mslogic.c: `if (floor != Door_Green) --possession(floor)`. */
        Tile[] doors = { Tile.DOOR_BLUE, Tile.DOOR_RED, Tile.DOOR_GREEN, Tile.DOOR_YELLOW };
        for (int i = 0; i < doors.length; i++) {
            final int slot = i;
            Level dr = chipOnto(doors[i], lv -> lv.getKeys()[slot] = 1);
            Harness.eq("the " + doors[i] + " opens to floor", at(dr, 5, 5), Tile.FLOOR);
            short expected = (doors[i] == Tile.DOOR_GREEN) ? (short) 1 : (short) 0;
            Harness.eq("the " + doors[i] + (expected == 1 ? " does NOT consume" : " consumes") + " its key",
                       dr.getKeys()[slot], expected);
        }

        Level chipTile = chipOnto(Tile.CHIP, lv -> lv.setChipsLeft(3));
        Harness.eq("collecting an IC chip decrements the counter", chipTile.getChipsLeft(), 2);
        Harness.eq("and clears the tile", at(chipTile, 5, 5), Tile.FLOOR);

        Level socket = chipOnto(Tile.SOCKET, lv -> lv.setChipsLeft(0));
        Harness.eq("the socket opens once the count is zero", at(socket, 5, 5), Tile.FLOOR);

        /* ================================================================
         * 3. Blocks fill water, and WHAT they leave depends on the block
         * ================================================================
         * The ordinary block leaves dirt, which is the "push a block in to cross" idiom. An ICE
         * BLOCK leaves ICE instead -- a distinction MS models and Lynx does not have at all. */

        /* ONE tick, and the count matters. The push resolves on the first tick; on the second Chip
         * walks onto the square himself and destroys the evidence -- he clears the dirt, or drowns
         * in the water the ice block just made. The first draft used ten ticks and failed for that
         * reason rather than for anything to do with blocks. */

        Level blockWater = build("BlockWater", b -> b
                .tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, Tile.BLOCK).tile(6, 5, Tile.WATER));
        step(blockWater, Direction.RIGHT, 1);
        Harness.eq("a block pushed into water leaves DIRT", at(blockWater, 6, 5), Tile.DIRT);
        Harness.check("and Chip is alive, standing where the block was", !blockWater.getChip().isDead());
        Harness.eq("which is the square he pushed it off",
                   blockWater.getChip().getPosition().getX() + "," + blockWater.getChip().getPosition().getY(), "5,5");

        Level iceBlockWater = build("IceBlockWater", b -> b
                .tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, Tile.ICE_BLOCK).tile(6, 5, Tile.WATER));
        step(iceBlockWater, Direction.RIGHT, 1);
        Harness.eq("an ICE block pushed into water leaves ICE, not dirt",
                   at(iceBlockWater, 6, 5), Tile.ICE);

        Level iceBlockFire = build("IceBlockFire", b -> b
                .tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, Tile.ICE_BLOCK).tile(6, 5, Tile.FIRE));
        step(iceBlockFire, Direction.RIGHT, 1);
        Harness.eq("an ICE block pushed into fire leaves WATER -- it melts",
                   at(iceBlockFire, 6, 5), Tile.WATER);

        /* And then the consequence, which is a real thing that happens to players: the water the
         * ice block just made is water like any other, so walking in after it drowns you. One more
         * tick is all it takes. */
        step(iceBlockFire, Direction.RIGHT, 1);
        Harness.check("stepping into the water his own ice block made drowns Chip",
                      iceBlockFire.getChip().isDead());
        Harness.eq("leaving DROWNED_CHIP where the fire used to be",
                   at(iceBlockFire, 6, 5), Tile.DROWNED_CHIP);

        /* ================================================================
         * 4. Monsters: the glider and the fireball are the two exemptions
         * ================================================================
         * mslogic.c: `if (crid != Glider) dead` in water, `if (crid != Fireball) dead` in fire.
         * A tank is used for the dying cases because its move list is one direction, so the fixture
         * cannot wander off and pass for the wrong reason. */

        Level gliderWater = build("GliderWater", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.GLIDER_RIGHT).monster(5, 5);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.WATER);
        });
        Creature glider = monsterOf(gliderWater, CreatureID.GLIDER);
        Harness.check("the fixture has a glider", glider != null);
        step(gliderWater, Direction.NONE, 10);
        Harness.check("a glider crosses water alive", !glider.isDead());

        Level tankWater = build("TankWater", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.TANK_RIGHT).monster(5, 5);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.WATER);
        });
        Creature tank = monsterOf(tankWater, CreatureID.TANK_MOVING);
        Harness.check("the fixture has a tank", tank != null);
        step(tankWater, Direction.NONE, 10);
        Harness.check("a tank drowns -- only the glider is exempt", tank.isDead());

        Level fireballFire = build("FireballFire", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.FIREBALL_RIGHT).monster(5, 5);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.FIRE);
        });
        Creature fireball = monsterOf(fireballFire, CreatureID.FIREBALL);
        Harness.check("the fixture has a fireball", fireball != null);
        step(fireballFire, Direction.NONE, 10);
        Harness.check("a fireball crosses fire alive", !fireball.isDead());

        Level tankFire = build("TankFire", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.TANK_RIGHT).monster(5, 5);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.FIRE);
        });
        Creature burning = monsterOf(tankFire, CreatureID.TANK_MOVING);
        Harness.check("the fixture has a tank", burning != null);
        step(tankFire, Direction.NONE, 10);
        Harness.check("a tank dies in fire -- only the fireball is exempt", burning.isDead());

        /* A bug is the other half of the fire rule, and it is a REFUSAL rather than a death:
         * canEnter turns it away before tryEnter is ever reached (CanEnterTest section 8). Stated
         * here because "the bug is still alive" and "the bug never entered" look identical from
         * outside, and only one of them is right. */
        Level bugFire = build("BugFire", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.BUG_RIGHT).monster(5, 5);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.FIRE);
        });
        Creature bug = monsterOf(bugFire, CreatureID.BUG);
        Harness.check("the fixture has a bug", bug != null);
        step(bugFire, Direction.NONE, 10);
        Harness.check("a bug beside fire is still alive", !bug.isDead());
        Harness.check("because it never entered -- it is still off the fire",
                      bug.getPosition().getX() <= 5);
        Harness.eq("and the fire it refused is untouched", at(bugFire, 6, 5), Tile.FIRE);
    }
}
