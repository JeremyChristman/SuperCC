import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Position;
import game.Tile;

/**
 * LYNX movement: the per-tick engine. LynxCreature.tick() against lxlogic.c's startmovement(),
 * continuemovement() and endmovement().
 *
 * WHY THIS FILE
 * -------------
 * tick() is 217 lines and was the largest untested method in the repo. It folds THREE Tile World
 * functions into one, and the seam between them is invisible from the outside:
 *
 *     timeTraveled == 0 branch   -> TW startmovement()    begins a move, claims the square
 *     the speed subtraction      -> TW continuemovement() how long the move takes
 *     the switch on arrival      -> TW endmovement()      what the tile does to you
 *
 * THE SPEED RULE (continuemovement, lxlogic.c:1273)
 * -------------------------------------------------
 *     speed = cr->id == Blob ? 1 : 2;
 *     if (isslide(floor) && (cr->id != Chip || !possession(Boots_Slide))) speed *= 2;
 *     else if (isice(floor) && (cr->id != Chip || !possession(Boots_Ice))) speed *= 2;
 *     cr->moving -= speed;
 *
 * A move starts with 8 units to travel, so the tick count falls straight out of the speed: four
 * ticks on floor, two on ice or a force floor, eight for a blob. Section 1 asserts the whole
 * countdown sequence rather than just the endpoint, because only the sequence distinguishes "moves
 * at the right speed" from "arrives eventually".
 *
 * ⚠ SuperCC tests ICE first and force floor second; Tile World tests slide first and ice second.
 * That is a real difference in the source and it is harmless, because `isice` and `isslide` are
 * disjoint -- no tile is both. It is called out here so that nobody "fixes" the order and assumes
 * they have changed behavior, and so that if a tile ever becomes both, this note is where the
 * assumption is written down.
 *
 * THE ARRIVAL SWITCH (endmovement, lxlogic.c:1302)
 * ------------------------------------------------
 * Tile World runs an entity-specific switch (Chip / Block / everything else) and THEN a shared one.
 * SuperCC has a single switch with creature-type tests inside each case, so the same rules are
 * folded differently. Two consequences that are easy to lose in the fold, and are pinned below:
 *
 *   - a BLOCK that ends its move in water turns the water to DIRT and dies; a glider crosses water
 *     unharmed; everything else drowns.
 *   - Key_Blue is ERASED by whoever steps on it, including blocks and monsters, while the other
 *     three keys are only picked up by Chip and are left alone by everyone else. That asymmetry is
 *     in TW's Block and creature switches, and it is the sort of thing a tidy-up would flatten.
 *
 * ⚠ lxlogic.c carries NO `MOD (Jeremy)` comments, unlike mslogic.c's 79 -- it is unmodified upstream
 * Tile World, and the desync project that went 135 to 0 was MS-only. A failure here is a finding.
 */
public class LynxTickTest {

    public static void main(String[] args) {
        System.exit(Harness.run("LynxTickTest", LynxTickTest::body));
    }

    private static Level build(String name, java.util.function.Consumer<DatBuilder.Level> f)
            throws Exception {
        java.nio.file.Path d = Harness.tempDir("scc-lynxtick-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        DatBuilder.Level lv = b.level().title(name);
        f.accept(lv);
        lv.end();
        return new io.DatParser(b.writeTo(d, name + ".dat").toFile())
                .parseLevel(1, 0, game.Step.EVEN, game.Ruleset.LYNX, Direction.UP);
    }

    private static char ch(Direction d) {
        switch (d) {
            case UP:    return 'u';
            case LEFT:  return 'l';
            case DOWN:  return 'd';
            case RIGHT: return 'r';
            default:    return '-';
        }
    }

    private static void step(Level lv, Direction d, int ticks) {
        for (int i = 0; i < ticks; i++) lv.tick(ch(d), new Direction[]{ d });
    }

    private static Tile at(Level lv, int x, int y) {
        return lv.getLayerFG().get(new Position(x, y));
    }

    /**
     * Chip at 4,5 and the tile under test at 5,5; walks him one square right onto it.
     * `prepare` runs after parsing and before the first tick, for boots and chip counts.
     */
    private static Level chipWalksOnto(Tile target, java.util.function.Consumer<Level> prepare)
            throws Exception {
        Level lv = build("Walk", b -> b.tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, target));
        if (prepare != null) prepare.accept(lv);
        step(lv, Direction.RIGHT, 5);
        return lv;
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. continuemovement: the speed table, asserted as a countdown
         * ================================================================
         * A move begins with 8 units. The first tick both starts the move (position updates
         * immediately, as it does in TW) and applies one subtraction, so a normal creature on floor
         * reads 6, 4, 2, 0 and arrives on the fourth tick. */

        Level floor = build("Floor", b -> b.tile(5, 5, Tile.CHIP_RIGHT));
        Creature chip = floor.getChip();
        int[] onFloor = new int[4];
        for (int t = 0; t < 4; t++) {
            floor.tick('r', new Direction[]{ Direction.RIGHT });
            onFloor[t] = chip.getTimeTraveled();
        }
        Harness.eq("Chip on floor moves at speed 2: the countdown is 6,4,2,0",
                   java.util.Arrays.toString(onFloor), "[6, 4, 2, 0]");
        Harness.eq("and he has advanced exactly one square", chip.getPosition().getIndex() % 32, 6);

        /* Ice doubles the speed, so the same move takes two ticks. */
        Level ice = build("Ice", b -> {
            b.tile(5, 5, Tile.CHIP_RIGHT);
            for (int x = 6; x < 12; x++) b.tile(x, 5, Tile.ICE);
        });
        Creature iceChip = ice.getChip();
        int[] onIce = new int[2];
        for (int t = 0; t < 2; t++) {
            ice.tick('r', new Direction[]{ Direction.RIGHT });
            onIce[t] = iceChip.getTimeTraveled();
        }
        Harness.eq("ice doubles the speed: the countdown is 4,0", java.util.Arrays.toString(onIce), "[4, 0]");

        /* Ice skates take the doubling away again -- the `cr->id != Chip || !possession(Boots_Ice)`
         * half of the condition, which nothing else in the suite reaches. */
        Level skates = build("Skates", b -> {
            b.tile(5, 5, Tile.CHIP_RIGHT);
            for (int x = 6; x < 12; x++) b.tile(x, 5, Tile.ICE);
        });
        skates.getBoots()[2] = 1;
        Creature skateChip = skates.getChip();
        int[] onSkates = new int[4];
        for (int t = 0; t < 4; t++) {
            skates.tick('r', new Direction[]{ Direction.RIGHT });
            onSkates[t] = skateChip.getTimeTraveled();
        }
        Harness.eq("ice skates cancel the doubling: back to 6,4,2,0",
                   java.util.Arrays.toString(onSkates), "[6, 4, 2, 0]");

        /* A blob is the speed-1 case, and the only creature that is. */
        Level blobLv = build("Blob", b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.BLOB_UP));
        Creature blob = null;
        for (int i = 0; i < blobLv.getMonsterList().size(); i++)
            if (blobLv.getMonsterList().get(i).getCreatureType() == CreatureID.BLOB)
                blob = blobLv.getMonsterList().get(i);
        Harness.check("the blob fixture has a blob", blob != null);
        java.util.List<Integer> blobCountdown = new java.util.ArrayList<>();
        for (int t = 0; t < 8; t++) {
            blobLv.tick('-', new Direction[]{ Direction.NONE });
            blobCountdown.add(blob.getTimeTraveled());
        }
        Harness.eq("a blob moves at speed 1, so its countdown steps by one",
                   blobCountdown.toString(), "[7, 6, 5, 4, 3, 2, 1, 0]");

        /* ================================================================
         * 2. endmovement: what a tile does to CHIP
         * ================================================================ */

        Harness.check("Chip drowns in water without flippers",
                      chipWalksOnto(Tile.WATER, null).getChip().isDead());
        Harness.check("flippers carry him across",
                      !chipWalksOnto(Tile.WATER, lv -> lv.getBoots()[0] = 1).getChip().isDead());

        Harness.check("Chip burns in fire without fire boots",
                      chipWalksOnto(Tile.FIRE, null).getChip().isDead());
        Harness.check("fire boots carry him across",
                      !chipWalksOnto(Tile.FIRE, lv -> lv.getBoots()[1] = 1).getChip().isDead());

        Level dirt = chipWalksOnto(Tile.DIRT, null);
        Harness.eq("dirt is cleared to floor once crossed", at(dirt, 5, 5), Tile.FLOOR);
        Harness.check("and Chip survives it", !dirt.getChip().isDead());

        Level fake = chipWalksOnto(Tile.BLUEWALL_FAKE, null);
        Harness.eq("a fake blue wall is cleared to floor", at(fake, 5, 5), Tile.FLOOR);

        Level popup = chipWalksOnto(Tile.POP_UP_WALL, null);
        Harness.eq("a pop-up wall becomes a WALL behind him", at(popup, 5, 5), Tile.WALL);

        Level bomb = chipWalksOnto(Tile.BOMB, null);
        Harness.check("a bomb kills Chip", bomb.getChip().isDead());
        Harness.eq("and the bomb tile is consumed", at(bomb, 5, 5), Tile.FLOOR);

        Level thief = chipWalksOnto(Tile.THIEF, lv -> {
            lv.getBoots()[0] = 1; lv.getBoots()[1] = 1; lv.getBoots()[2] = 1; lv.getBoots()[3] = 1;
        });
        Harness.eq("a thief takes every boot",
                   java.util.Arrays.toString(thief.getBoots()), "[0, 0, 0, 0]");
        Harness.eq("but the thief tile stays where it is", at(thief, 5, 5), Tile.THIEF);

        /* Keys: collected, counted, and the tile cleared. */
        Tile[] keyTiles = { Tile.KEY_BLUE, Tile.KEY_RED, Tile.KEY_GREEN, Tile.KEY_YELLOW };
        for (int i = 0; i < keyTiles.length; i++) {
            Level k = chipWalksOnto(keyTiles[i], null);
            Harness.eq("Chip picks up " + keyTiles[i], k.getKeys()[i], (short) 1);
            Harness.eq("and the " + keyTiles[i] + " tile is cleared", at(k, 5, 5), Tile.FLOOR);
        }

        /* Boots: collected, and the tile cleared. */
        Tile[] bootTiles = { Tile.BOOTS_WATER, Tile.BOOTS_FIRE, Tile.BOOTS_ICE, Tile.BOOTS_FF };
        for (int i = 0; i < bootTiles.length; i++) {
            Level bt = chipWalksOnto(bootTiles[i], null);
            Harness.eq("Chip picks up " + bootTiles[i], bt.getBoots()[i], (byte) 1);
            Harness.eq("and the " + bootTiles[i] + " tile is cleared", at(bt, 5, 5), Tile.FLOOR);
        }

        /* Doors consume the key -- except green, which is reusable. TW:
         *     if (floor != Door_Green) --possession(floor); */
        Tile[] doorTiles = { Tile.DOOR_BLUE, Tile.DOOR_RED, Tile.DOOR_GREEN, Tile.DOOR_YELLOW };
        for (int i = 0; i < doorTiles.length; i++) {
            final int slot = i;
            Level dr = chipWalksOnto(doorTiles[i], lv -> lv.getKeys()[slot] = 1);
            Harness.eq("the " + doorTiles[i] + " opens to floor", at(dr, 5, 5), Tile.FLOOR);
            short expected = (doorTiles[i] == Tile.DOOR_GREEN) ? (short) 1 : (short) 0;
            Harness.eq("the " + doorTiles[i] + " " + (expected == 1 ? "does NOT consume" : "consumes")
                       + " its key", dr.getKeys()[slot], expected);
        }

        /* The IC chip and the socket. */
        Level icChip = chipWalksOnto(Tile.CHIP, lv -> lv.setChipsLeft(3));
        Harness.eq("collecting an IC chip decrements the counter", icChip.getChipsLeft(), 2);
        Harness.eq("and clears the tile", at(icChip, 5, 5), Tile.FLOOR);

        Level socket = chipWalksOnto(Tile.SOCKET, lv -> lv.setChipsLeft(0));
        Harness.eq("the socket opens to floor once the count is zero", at(socket, 5, 5), Tile.FLOOR);

        /* ================================================================
         * 3. endmovement: BLOCKS, which have their own switch in Tile World
         * ================================================================
         * TW's Block branch: Water turns to Dirt and the block is removed. That is the rule the
         * whole "push a block into the water to cross" idiom rests on. */

        Level blockWater = build("BlockWater", b -> b
                .tile(4, 5, Tile.CHIP_RIGHT)
                .tile(5, 5, Tile.BLOCK)
                .tile(6, 5, Tile.WATER));
        step(blockWater, Direction.RIGHT, 12);
        Harness.eq("a block pushed into water turns it to DIRT", at(blockWater, 6, 5), Tile.DIRT);
        Harness.check("and Chip is still alive on the near side", !blockWater.getChip().isDead());

        /* ================================================================
         * 4. endmovement: monsters, and the blue-key asymmetry
         * ================================================================
         * A glider crosses water; everything else drowns. And Key_Blue is erased by whoever walks
         * over it, while the other three are untouched by anything but Chip. */

        Level gliderWater = build("GliderWater", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.GLIDER_RIGHT);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.WATER);
        });
        Creature glider = null;
        for (int i = 0; i < gliderWater.getMonsterList().size(); i++)
            if (gliderWater.getMonsterList().get(i).getCreatureType() == CreatureID.GLIDER)
                glider = gliderWater.getMonsterList().get(i);
        Harness.check("the glider fixture has a glider", glider != null);
        step(gliderWater, Direction.NONE, 12);
        Harness.check("a glider crosses water alive", !glider.isDead());
        Harness.check("and it actually got onto the water", glider.getPosition().getIndex() % 32 > 5);

        /* A TANK, not a bug. A bug's preference is left-first, so a BUG_RIGHT beside water turns UP
         * onto the floor and never enters it -- the first draft of this fixture did exactly that and
         * the assertion failed for a reason that had nothing to do with drowning. A tank's list is
         * one direction, so it has no way to avoid the water. */
        Level tankWater = build("TankWater", b -> {
            b.tile(1, 1, Tile.CHIP_DOWN).tile(5, 5, Tile.TANK_RIGHT);
            for (int x = 6; x < 10; x++) b.tile(x, 5, Tile.WATER);
        });
        Creature tank = null;
        for (int i = 0; i < tankWater.getMonsterList().size(); i++)
            if (tankWater.getMonsterList().get(i).getCreatureType() == CreatureID.TANK_MOVING)
                tank = tankWater.getMonsterList().get(i);
        Harness.check("the drowning fixture has a tank", tank != null);
        step(tankWater, Direction.NONE, 12);
        Harness.check("a tank drowns -- only the glider is exempt", tank.isDead());

        /* The blue-key asymmetry. A tank is used because its move is exactly one direction, so the
         * fixture cannot wander. */
        Level blueKey = build("BlueKey", b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT)
                .tile(6, 5, Tile.KEY_BLUE));
        step(blueKey, Direction.NONE, 8);
        Harness.eq("a monster walking over a BLUE key destroys it", at(blueKey, 6, 5), Tile.FLOOR);

        Level redKey = build("RedKey", b -> b
                .tile(1, 1, Tile.CHIP_DOWN)
                .tile(5, 5, Tile.TANK_RIGHT)
                .tile(6, 5, Tile.KEY_RED));
        step(redKey, Direction.NONE, 8);
        Harness.eq("but a RED key is left alone -- only Chip picks it up", at(redKey, 6, 5), Tile.KEY_RED);

        /* ================================================================
         * 5. Two places SuperCC and Tile World are written differently
         * ================================================================
         * Neither is known to change a replay, and neither is being "fixed" here -- they are pinned
         * so that the difference is recorded rather than rediscovered, and so a future change to
         * either one is visible.
         *
         * (a) TW guards the IC counter: `if (chipsneeded()) --chipsneeded();`. SuperCC decrements
         *     unconditionally, so a level with more IC chips on the map than it requires can drive
         *     the count NEGATIVE where Tile World would stop at zero. The socket test is
         *     `chipsLeft <= 0` on both sides, so the door still opens either way. */
        Level extraChips = chipWalksOnto(Tile.CHIP, lv -> lv.setChipsLeft(0));
        Harness.eq("SuperCC lets the IC count go negative; TW clamps at zero (documented, not fixed)",
                   extraChips.getChipsLeft(), -1);
        Harness.eq("the tile is still cleared", at(extraChips, 5, 5), Tile.FLOOR);
        Level stillOpens = build("StillOpens", b -> b
                .tile(4, 5, Tile.CHIP_RIGHT).tile(5, 5, Tile.SOCKET));
        stillOpens.setChipsLeft(-1);
        step(stillOpens, Direction.RIGHT, 5);
        Harness.eq("and a negative count still opens the socket, so the difference is cosmetic",
                   at(stillOpens, 5, 5), Tile.FLOOR);

        /* (b) TW INCREMENTS boot possession; SuperCC assigns 1. Collecting the same boot twice
         *     therefore reads 2 in Tile World and 1 here. Every use is a nonzero test, so this
         *     cannot change behavior -- but it means the boot count is not a count. */
        Level twoBoots = build("TwoBoots", b -> b
                .tile(3, 5, Tile.CHIP_RIGHT)
                .tile(4, 5, Tile.BOOTS_FIRE)
                .tile(5, 5, Tile.BOOTS_FIRE));
        step(twoBoots, Direction.RIGHT, 10);
        Harness.eq("collecting two fire boots still reads 1, not 2 (TW would say 2)",
                   twoBoots.getBoots()[1], (byte) 1);
    }
}
