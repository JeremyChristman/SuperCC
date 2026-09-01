import game.CreatureID;
import game.Direction;
import game.Position;
import game.Tile;
import game.Lynx.LynxCreature;

import java.lang.reflect.Method;

/**
 * LYNX entry rules -- which tiles admit whom, from which direction. Transcribed from Tile World's
 * lxlogic.c movelaws[] table and the extra checks in canmakemove().
 *
 * THE ORACLE
 * ----------
 * lxlogic.c:524 declares
 *
 *     static struct { unsigned char chip, block, creature; } const movelaws[] = { ... };
 *
 * and canmakemove() consults it as
 *
 *     if (!(movelaws[floor].chip & dir))      return FALSE;     // Chip
 *     if (!(movelaws[floor].block & dir))     return FALSE;     // a block
 *     if (!(movelaws[floor].creature & dir))  return FALSE;     // everything else
 *
 * The masks pack two nibbles (lxlogic.c:509):
 *
 *     DIR_IN(dir)  = (dir)          NORTH 1, WEST 2, SOUTH 4, EAST 8
 *     DIR_OUT(dir) = ((dir) << 4)
 *
 * `dir` in those tests is a bare direction bit, so **only the IN nibble can ever match** -- the OUT
 * bits govern leaving, which lxlogic.c handles in a separate switch on the tile the creature is
 * standing on, and which belongs with canLeave rather than here. Every expectation below is
 * therefore an IN mask, transcribed from the C rather than derived from SuperCC.
 *
 * THREE COLUMNS FOLDED INTO ONE FUNCTION
 * ---------------------------------------
 * Tile World asks a different question per entity class. SuperCC's canEnter takes only the tile and
 * the direction and branches internally on `creatureType.isChip()`, so it has to fold TW's three
 * columns -- plus several checks that live in canmakemove() rather than in movelaws -- into one
 * switch. That folding is exactly where a ruleset can go wrong quietly, so the table below keeps
 * the three columns separate and lets the assertions do the folding.
 *
 * The checks that are NOT in movelaws and are folded into SuperCC's canEnter:
 *
 *     fire      canmakemove: `if (floor == Fire && cr->id != Fireball) return FALSE` -- and it sits
 *               in the CREATURE branch only, so a BLOCK may enter fire and a bug may not.
 *     doors     canmakemove: `if (isdoor(floor) && !possession(floor)) return FALSE`
 *     socket    canmakemove: `if (floor == Socket && chipsneeded() > 0) return FALSE`
 *
 * WHY THIS FILE USES REFLECTION
 * ------------------------------
 * MSCreature.canEnter is public and CanEnterTest calls it directly. LynxCreature.canEnter is
 * private. Widening production visibility to suit a test would be the wrong trade, so this reaches
 * it reflectively. That is a deliberate, contained ugliness: the alternative -- driving every case
 * through canMakeMove on a purpose-built level -- would need a fresh level per case and would test
 * the movement loop's preconditions as much as the entry table.
 *
 * WHAT THIS FILE COVERS THAT THE MS ONE DOES NOT
 * -----------------------------------------------
 * CanEnterTest says of MS: "Doors and sockets are absent entirely: canEnter consults level.getKeys()
 * and getChipsLeft()." A real parsed Lynx level is attached here, so doors and the socket ARE
 * tested, in both the held-key and no-key states.
 *
 * ⚠ Unlike mslogic.c, lxlogic.c carries NO `MOD (Jeremy)` comments -- it is unmodified upstream
 * Tile World, and the desync project that went 135 to 0 was MS-only. A failure here is a finding,
 * not a broken assertion.
 */
public class LynxCanEnterTest {

    public static void main(String[] args) {
        System.exit(Harness.run("LynxCanEnterTest", LynxCanEnterTest::body));
    }

    /* ---- Tile World's direction bits and the IN-nibble masks, from gen.h / lxlogic.c ---- */

    private static final int N = 1, W = 2, S = 4, E = 8;
    private static final int ALL = N | W | S | E;
    private static final int NONE_IN = 0;

    private static int twBit(Direction d) {
        switch (d) {
            case UP:    return N;
            case LEFT:  return W;
            case DOWN:  return S;
            case RIGHT: return E;
            default:    return 0;
        }
    }

    /** One row of movelaws[]: the IN mask for each of Tile World's three entity columns. */
    private static final class Law {
        final Tile tile; final int chip, block, creature;
        Law(Tile tile, int chip, int block, int creature) {
            this.tile = tile; this.chip = chip; this.block = block; this.creature = creature;
        }
    }

    private static Law law(Tile t, int c, int b, int m) { return new Law(t, c, b, m); }

    /**
     * movelaws[], transcribed row by row from lxlogic.c:524.
     *
     * Only the IN nibble of each entry is written out, because that is the only half canmakemove
     * can match against a bare direction bit. `ALL_OUT` rows therefore appear here as 0.
     */
    private static final Law[] MOVELAWS = {
        /* Empty */                 law(Tile.FLOOR,      ALL, ALL, ALL),
        /* Slide_South */           law(Tile.FF_DOWN,    ALL, ALL, ALL),
        /* Slide_North */           law(Tile.FF_UP,      ALL, ALL, ALL),
        /* Slide_East */            law(Tile.FF_RIGHT,   ALL, ALL, ALL),
        /* Slide_West */            law(Tile.FF_LEFT,    ALL, ALL, ALL),
        /* Slide_Random */          law(Tile.FF_RANDOM,  ALL, ALL, ALL),
        /* Ice */                   law(Tile.ICE,        ALL, ALL, ALL),

        /* IceWall_Northwest */     law(Tile.ICE_SLIDE_NORTHWEST, S | E, S | E, S | E),
        /* IceWall_Northeast */     law(Tile.ICE_SLIDE_NORTHEAST, S | W, S | W, S | W),
        /* IceWall_Southwest */     law(Tile.ICE_SLIDE_SOUTHWEST, N | E, N | E, N | E),
        /* IceWall_Southeast */     law(Tile.ICE_SLIDE_SOUTHEAST, N | W, N | W, N | W),

        /* Gravel  -- the ONE row where block and creature disagree */
        /*                       */ law(Tile.GRAVEL,     ALL, ALL, NONE_IN),
        /* Dirt */                  law(Tile.DIRT,       ALL, NONE_IN, NONE_IN),
        /* Water */                 law(Tile.WATER,      ALL, ALL, ALL),
        /* Fire */                  law(Tile.FIRE,       ALL, ALL, ALL),
        /* Bomb */                  law(Tile.BOMB,       ALL, ALL, ALL),
        /* Beartrap */              law(Tile.TRAP,       ALL, ALL, ALL),
        /* Burglar */               law(Tile.THIEF,      ALL, NONE_IN, NONE_IN),
        /* HintButton */            law(Tile.HINT,       ALL, NONE_IN, NONE_IN),

        /* Button_Blue */           law(Tile.BUTTON_BLUE,  ALL, ALL, ALL),
        /* Button_Green */          law(Tile.BUTTON_GREEN, ALL, ALL, ALL),
        /* Button_Red */            law(Tile.BUTTON_RED,   ALL, ALL, ALL),
        /* Button_Brown */          law(Tile.BUTTON_BROWN, ALL, ALL, ALL),
        /* Teleport */              law(Tile.TELEPORT,     ALL, ALL, ALL),

        /* Wall */                  law(Tile.WALL,       NONE_IN, NONE_IN, NONE_IN),
        /* Wall_North */            law(Tile.THIN_WALL_UP,    N | W | E, N | W | E, N | W | E),
        /* Wall_West */             law(Tile.THIN_WALL_LEFT,  N | W | S, N | W | S, N | W | S),
        /* Wall_South */            law(Tile.THIN_WALL_DOWN,  W | S | E, W | S | E, W | S | E),
        /* Wall_East */             law(Tile.THIN_WALL_RIGHT, N | S | E, N | S | E, N | S | E),
        /* Wall_Southeast */        law(Tile.THIN_WALL_DOWN_RIGHT, S | E, S | E, S | E),

        /* HiddenWall_Perm */       law(Tile.INVISIBLE_WALL,  NONE_IN, NONE_IN, NONE_IN),
        /* HiddenWall_Temp */       law(Tile.HIDDENWALL_TEMP, ALL, NONE_IN, NONE_IN),
        /* BlueWall_Real */         law(Tile.BLUEWALL_REAL,   ALL, NONE_IN, NONE_IN),
        /* BlueWall_Fake */         law(Tile.BLUEWALL_FAKE,   ALL, NONE_IN, NONE_IN),
        /* SwitchWall_Open */       law(Tile.TOGGLE_OPEN,     ALL, ALL, ALL),
        /* SwitchWall_Closed */     law(Tile.TOGGLE_CLOSED,   NONE_IN, NONE_IN, NONE_IN),
        /* PopupWall */             law(Tile.POP_UP_WALL,     ALL, NONE_IN, NONE_IN),
        /* CloneMachine */          law(Tile.CLONE_MACHINE,   NONE_IN, NONE_IN, NONE_IN),

        /* Exit */                  law(Tile.EXIT,       ALL, NONE_IN, NONE_IN),
        /* ICChip */                law(Tile.CHIP,       ALL, NONE_IN, NONE_IN),

        /* Key_Red */               law(Tile.KEY_RED,    ALL, ALL, ALL),
        /* Key_Blue */              law(Tile.KEY_BLUE,   ALL, ALL, ALL),
        /* Key_Yellow */            law(Tile.KEY_YELLOW, ALL, NONE_IN, NONE_IN),
        /* Key_Green */             law(Tile.KEY_GREEN,  ALL, NONE_IN, NONE_IN),
        /* Boots_Slide */           law(Tile.BOOTS_FF,    ALL, NONE_IN, NONE_IN),
        /* Boots_Ice */             law(Tile.BOOTS_ICE,   ALL, NONE_IN, NONE_IN),
        /* Boots_Water */           law(Tile.BOOTS_WATER, ALL, NONE_IN, NONE_IN),
        /* Boots_Fire */            law(Tile.BOOTS_FIRE,  ALL, NONE_IN, NONE_IN),

        /* Overlay_Buffer and the corpse tiles are all { 0, 0, 0 } */
        /*                       */ law(Tile.OVERLAY_BUFFER, NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.DROWNED_CHIP,   NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.BURNED_CHIP,    NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.BOMBED_CHIP,    NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.EXITED_CHIP,    NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.EXIT_EXTRA_1,   NONE_IN, NONE_IN, NONE_IN),
        /*                       */ law(Tile.EXIT_EXTRA_2,   NONE_IN, NONE_IN, NONE_IN),
    };

    private static final Direction[] ALL_DIRS =
        { Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT };

    private static Method CAN_ENTER;
    private static game.Level LEVEL;

    /** LynxCreature.canEnter is private; MSCreature's is public. See the class comment. */
    private static boolean canEnter(CreatureID type, Direction traveling, Tile tile) throws Exception {
        LynxCreature c = new LynxCreature(Direction.UP, type, new Position(10, 10));
        c.setLevel(LEVEL);
        return (Boolean) CAN_ENTER.invoke(c, traveling, tile);
    }

    private static void body() throws Exception {
        CAN_ENTER = LynxCreature.class.getDeclaredMethod("canEnter", Direction.class, Tile.class);
        CAN_ENTER.setAccessible(true);

        /* A real parsed Lynx level, so the door and socket cases have keys and a chip count to
         * consult. Everything else in the table is level-independent. */
        java.nio.file.Path dir = Harness.tempDir("scc-lynx-enter-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        b.level().title("Entry").tile(5, 5, Tile.CHIP_UP).end();
        LEVEL = new io.DatParser(b.writeTo(dir, "enter.dat").toFile())
                .parseLevel(1, 0, game.Step.EVEN, game.Ruleset.LYNX, Direction.UP);

        /* No keys, and chips still required -- the restrictive state. Doors and the socket are
         * asserted separately below in both states. */
        short[] keys = LEVEL.getKeys();
        for (int i = 0; i < keys.length; i++) keys[i] = 0;
        LEVEL.setChipsLeft(1);

        /* ================================================================
         * 1. The whole movelaws table, every row, every direction
         * ================================================================
         * CHIP is compared against the .chip column, BLOCK against .block, and BUG against
         * .creature. Doors and the socket are skipped here only because their canmakemove-side
         * gates are state-dependent; section 4 covers them. */

        for (Law l : MOVELAWS) {
            for (Direction d : ALL_DIRS) {
                int bit = twBit(d);

                Harness.eq("Lynx CHIP entering " + l.tile + " traveling " + d,
                           canEnter(CreatureID.CHIP, d, l.tile), (l.chip & bit) != 0);

                Harness.eq("Lynx BLOCK entering " + l.tile + " traveling " + d,
                           canEnter(CreatureID.BLOCK, d, l.tile), (l.block & bit) != 0);

                /* A bug stands in for "everything that is not Chip and not a block". Fire is the
                 * one tile where that is not the whole story -- see section 2. */
                boolean creatureExpected = (l.creature & bit) != 0 && l.tile != Tile.FIRE;
                Harness.eq("Lynx BUG entering " + l.tile + " traveling " + d,
                           canEnter(CreatureID.BUG, d, l.tile), creatureExpected);
            }
        }

        /* ================================================================
         * 2. Fire -- the rule that is NOT in movelaws
         * ================================================================
         * movelaws[Fire] is { ALL, ALL, ALL }: by the table alone, everything may enter fire. The
         * real restriction is one line further down canmakemove(), inside the CREATURE branch:
         *
         *     if (floor == Fire && cr->id != Fireball) return FALSE;
         *
         * Because it is in the creature branch and not the block branch, a BLOCK may be pushed into
         * fire while a bug beside it may not walk in. Reading movelaws alone would get this wrong,
         * and a "simplification" of SuperCC's canEnter toward a pure per-tile table would lose it. */

        for (Direction d : ALL_DIRS) {
            Harness.check("a fireball may enter fire, traveling " + d,
                          canEnter(CreatureID.FIREBALL, d, Tile.FIRE));
            Harness.check("Chip may enter fire, traveling " + d,
                          canEnter(CreatureID.CHIP, d, Tile.FIRE));
            Harness.check("a BLOCK may enter fire -- the check is in TW's creature branch only",
                          canEnter(CreatureID.BLOCK, d, Tile.FIRE));
            for (CreatureID burns : new CreatureID[]{ CreatureID.BUG, CreatureID.GLIDER,
                                                      CreatureID.PINK_BALL, CreatureID.TEETH,
                                                      CreatureID.WALKER, CreatureID.BLOB,
                                                      CreatureID.PARAMECIUM, CreatureID.TANK_MOVING }) {
                Harness.check("a " + burns + " may NOT enter fire, traveling " + d,
                              !canEnter(burns, d, Tile.FIRE));
            }
        }

        /* ================================================================
         * 3. Gravel -- the one row where block and creature disagree
         * ================================================================
         * movelaws[Gravel] = { ALL_IN_OUT, ALL_IN_OUT, ALL_OUT }. The creature column has OUT bits
         * only, so a creature may leave gravel but never enter it, while a block may do both. If
         * SuperCC's canEnter answered by tile alone this distinction would vanish. */

        for (Direction d : ALL_DIRS) {
            Harness.check("Chip may enter gravel, traveling " + d,
                          canEnter(CreatureID.CHIP, d, Tile.GRAVEL));
            Harness.check("a block may enter gravel, traveling " + d,
                          canEnter(CreatureID.BLOCK, d, Tile.GRAVEL));
            Harness.check("a bug may NOT enter gravel, traveling " + d,
                          !canEnter(CreatureID.BUG, d, Tile.GRAVEL));
            Harness.check("a fireball may NOT enter gravel either -- gravel is not a fire rule",
                          !canEnter(CreatureID.FIREBALL, d, Tile.GRAVEL));
        }

        /* Dirt differs from gravel by exactly one column: a block may enter gravel but not dirt. */
        for (Direction d : ALL_DIRS) {
            Harness.check("a block may NOT enter dirt, traveling " + d,
                          !canEnter(CreatureID.BLOCK, d, Tile.DIRT));
            Harness.check("Chip may enter dirt, traveling " + d,
                          canEnter(CreatureID.CHIP, d, Tile.DIRT));
        }

        /* ================================================================
         * 4. Doors and the socket -- state-dependent, and absent from the MS test
         * ================================================================
         * movelaws lets Chip into all four doors and the socket; canmakemove then refuses on
         * `!possession(floor)` and `chipsneeded() > 0`. SuperCC folds both into canEnter, so both
         * states are observable here. */

        Tile[] doors = { Tile.DOOR_BLUE, Tile.DOOR_RED, Tile.DOOR_GREEN, Tile.DOOR_YELLOW };
        for (int i = 0; i < doors.length; i++) {
            for (int k = 0; k < keys.length; k++) keys[k] = 0;
            for (Direction d : ALL_DIRS) {
                Harness.check("Chip may NOT enter " + doors[i] + " with no key, traveling " + d,
                              !canEnter(CreatureID.CHIP, d, doors[i]));
            }
            keys[i] = 1;
            for (Direction d : ALL_DIRS) {
                Harness.check("Chip MAY enter " + doors[i] + " holding its key, traveling " + d,
                              canEnter(CreatureID.CHIP, d, doors[i]));
            }
            /* Holding the key does not admit anyone else: movelaws gives doors ALL_OUT for block
             * and creature, so the key is irrelevant to them. */
            Harness.check("a block may not enter " + doors[i] + " even with the key held",
                          !canEnter(CreatureID.BLOCK, Direction.UP, doors[i]));
            Harness.check("a bug may not enter " + doors[i] + " even with the key held",
                          !canEnter(CreatureID.BUG, Direction.UP, doors[i]));

            /* One key opens ONE color. With only door i's key held, every other door stays shut --
             * this is what catches an off-by-one in the keys[] indexing, which a single-door test
             * would not. */
            for (int j = 0; j < doors.length; j++) {
                if (j == i) continue;
                Harness.check("holding only the " + doors[i] + " key does not open " + doors[j],
                              !canEnter(CreatureID.CHIP, Direction.UP, doors[j]));
            }
        }
        for (int k = 0; k < keys.length; k++) keys[k] = 0;

        LEVEL.setChipsLeft(1);
        for (Direction d : ALL_DIRS) {
            Harness.check("Chip may NOT enter the socket while chips remain, traveling " + d,
                          !canEnter(CreatureID.CHIP, d, Tile.SOCKET));
        }
        LEVEL.setChipsLeft(0);
        for (Direction d : ALL_DIRS) {
            Harness.check("Chip MAY enter the socket once the count reaches zero, traveling " + d,
                          canEnter(CreatureID.CHIP, d, Tile.SOCKET));
        }
        Harness.check("a block may not enter the socket even at zero chips",
                      !canEnter(CreatureID.BLOCK, Direction.UP, Tile.SOCKET));
        Harness.check("a bug may not enter the socket even at zero chips",
                      !canEnter(CreatureID.BUG, Direction.UP, Tile.SOCKET));

        /* ================================================================
         * 5. The direction-dependent tiles, stated the other way round
         * ================================================================
         * Section 1 already covers these through the table. Stating the blocked direction directly
         * is what makes a transposed pair legible: if THIN_WALL_UP and THIN_WALL_DOWN were swapped,
         * section 1 fails with two tile names and no hint of the relationship, while these say it. */

        Object[][] blocked = {
            { Tile.THIN_WALL_UP,    Direction.DOWN  },   // Wall_North blocks SOUTH_IN
            { Tile.THIN_WALL_LEFT,  Direction.RIGHT },   // Wall_West  blocks EAST_IN
            { Tile.THIN_WALL_DOWN,  Direction.UP    },   // Wall_South blocks NORTH_IN
            { Tile.THIN_WALL_RIGHT, Direction.LEFT  },   // Wall_East  blocks WEST_IN
        };
        for (Object[] row : blocked) {
            Tile t = (Tile) row[0];
            Direction blockedDir = (Direction) row[1];
            for (Direction d : ALL_DIRS) {
                boolean expected = d != blockedDir;
                Harness.eq("a thin wall on " + t + " admits travel " + d,
                           canEnter(CreatureID.CHIP, d, t), expected);
            }
            Harness.check("a thin wall blocks the SAME direction for a bug as for Chip: " + t,
                          canEnter(CreatureID.BUG, blockedDir, t) == canEnter(CreatureID.CHIP, blockedDir, t));
        }

        /* Ice corners admit exactly the two directions they do not deflect. The pairing is the easy
         * thing to invert -- IceWall_Northwest blocks entry traveling NORTH or WEST, which reads
         * backwards until you picture the wall rather than the slide. */
        Object[][] iceCorners = {
            { Tile.ICE_SLIDE_NORTHWEST, Direction.UP,   Direction.LEFT  },
            { Tile.ICE_SLIDE_NORTHEAST, Direction.UP,   Direction.RIGHT },
            { Tile.ICE_SLIDE_SOUTHWEST, Direction.DOWN, Direction.LEFT  },
            { Tile.ICE_SLIDE_SOUTHEAST, Direction.DOWN, Direction.RIGHT },
        };
        for (Object[] row : iceCorners) {
            Tile t = (Tile) row[0];
            Direction b1 = (Direction) row[1], b2 = (Direction) row[2];
            for (Direction d : ALL_DIRS) {
                boolean expected = d != b1 && d != b2;
                Harness.eq("ice corner " + t + " admits travel " + d,
                           canEnter(CreatureID.CHIP, d, t), expected);
            }
            Harness.check("ice corner " + t + " blocks exactly two of the four directions",
                          !canEnter(CreatureID.CHIP, b1, t) && !canEnter(CreatureID.CHIP, b2, t));
        }

        /* THIN_WALL_DOWN_RIGHT is the two-sided one: Wall_Southeast admits only SOUTH_IN | EAST_IN,
         * so it blocks entry traveling UP or LEFT. */
        for (Direction d : ALL_DIRS) {
            boolean expected = d != Direction.UP && d != Direction.LEFT;
            Harness.eq("the SE thin wall admits travel " + d,
                       canEnter(CreatureID.CHIP, d, Tile.THIN_WALL_DOWN_RIGHT), expected);
        }

        /* ================================================================
         * 6. Entity-awareness is real, not incidental
         * ================================================================
         * If canEnter ever collapsed into a per-tile lookup that ignored the creature, most of this
         * file would still pass -- the majority of rows have identical columns. These are the rows
         * that would not, collected in one place so the property is stated rather than implied. */

        Tile[] chipOnly = { Tile.DIRT, Tile.THIEF, Tile.HINT, Tile.POP_UP_WALL, Tile.EXIT,
                            Tile.CHIP, Tile.BLUEWALL_FAKE, Tile.BLUEWALL_REAL,
                            Tile.HIDDENWALL_TEMP, Tile.KEY_GREEN, Tile.KEY_YELLOW,
                            Tile.BOOTS_WATER, Tile.BOOTS_FIRE, Tile.BOOTS_ICE, Tile.BOOTS_FF };
        for (Tile t : chipOnly) {
            Harness.check("only Chip may enter " + t,
                          canEnter(CreatureID.CHIP, Direction.UP, t)
                          && !canEnter(CreatureID.BLOCK, Direction.UP, t)
                          && !canEnter(CreatureID.BUG, Direction.UP, t));
        }

        /* Red and blue keys admit everyone; green and yellow admit only Chip. That asymmetry looks
         * like a bug until you find it in movelaws, where Key_Red and Key_Blue are ALL_IN_OUT on
         * all three columns and Key_Green and Key_Yellow are chip-only. */
        for (Tile k : new Tile[]{ Tile.KEY_RED, Tile.KEY_BLUE }) {
            Harness.check("a monster may walk over " + k + " -- movelaws says ALL on all three columns",
                          canEnter(CreatureID.BUG, Direction.UP, k)
                          && canEnter(CreatureID.BLOCK, Direction.UP, k)
                          && canEnter(CreatureID.CHIP, Direction.UP, k));
        }

        /* And the inverse property: the tiles nobody may enter, which would also survive a collapse
         * to a per-tile table but are worth naming. */
        Tile[] noOne = { Tile.WALL, Tile.INVISIBLE_WALL, Tile.TOGGLE_CLOSED, Tile.CLONE_MACHINE,
                         Tile.OVERLAY_BUFFER, Tile.DROWNED_CHIP, Tile.BURNED_CHIP,
                         Tile.BOMBED_CHIP, Tile.EXITED_CHIP, Tile.EXIT_EXTRA_1, Tile.EXIT_EXTRA_2 };
        for (Tile t : noOne) {
            for (Direction d : ALL_DIRS) {
                Harness.check("nobody may enter " + t + " traveling " + d,
                              !canEnter(CreatureID.CHIP, d, t)
                              && !canEnter(CreatureID.BLOCK, d, t)
                              && !canEnter(CreatureID.BUG, d, t));
            }
        }

        /* ================================================================
         * 7. Tiles SuperCC answers for that Tile World's Lynx has no floor entry for
         * ================================================================
         * ICE_BLOCK and the CC2-era corpse tiles have no Lynx counterpart; SuperCC's canEnter marks
         * them unenterable with the comment "Doesn't exist in lynx". A creature tile sitting in the
         * floor layer is likewise not a thing lxlogic.c models. These pin SuperCC's answers so a
         * later edit cannot quietly make one of them passable. */

        for (Direction d : ALL_DIRS) {
            Harness.check("ICE_BLOCK does not exist in Lynx and admits nobody, traveling " + d,
                          !canEnter(CreatureID.CHIP, d, Tile.ICE_BLOCK)
                          && !canEnter(CreatureID.BLOCK, d, Tile.ICE_BLOCK)
                          && !canEnter(CreatureID.BUG, d, Tile.ICE_BLOCK));
            Harness.check("UNUSED_36 admits nobody, traveling " + d,
                          !canEnter(CreatureID.CHIP, d, Tile.UNUSED_36));
            Harness.check("UNUSED_37 admits nobody, traveling " + d,
                          !canEnter(CreatureID.CHIP, d, Tile.UNUSED_37));
        }

        /* A Chip-shaped tile in the floor layer: SuperCC answers !isChip, so a monster may step on
         * it and Chip may not. Stated rather than justified -- lxlogic.c has no such row, so this
         * is SuperCC's own contract. */
        for (Tile t : new Tile[]{ Tile.CHIP_UP, Tile.CHIP_LEFT, Tile.CHIP_DOWN, Tile.CHIP_RIGHT }) {
            Harness.check("a monster may enter the Chip-shaped tile " + t,
                          canEnter(CreatureID.BUG, Direction.UP, t));
            Harness.check("Chip may NOT enter the Chip-shaped tile " + t,
                          !canEnter(CreatureID.CHIP, Direction.UP, t));
        }
    }
}
