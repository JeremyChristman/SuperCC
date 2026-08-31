import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Creature;
import game.CreatureID;
import game.Direction;
import game.Level;
import game.Tile;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Replays one solution and emits a per-tick trace, for diffing against Tile World.
 *
 * WHAT THIS IS FOR
 * ----------------
 * Every other test in this repo compares SuperCC against a TRANSCRIPTION of Tile World's rules.
 * This one compares the two ENGINES, running the same solution on the same level, tick by tick,
 * and pins the first tick where they disagree. It is the instrument that took the desync count
 * from 135 to 0, and the reason it is committed here is that the previous version lived in a
 * scratch folder and was lost -- the Tile World half survived only because it sits inside
 * mslogic.c behind -DTRACE_DESYNC.
 *
 * THE FORMAT IS NOT MINE TO CHOOSE. mslogic.c emits these exact lines and its comment says "in the
 * same format SuperCC's TraceLevel.java produces". This file is the other half of that contract:
 *
 *   T <lvl> <tick> <rng> chip=<x>,<y>,<slip>  C:<letter>,<x>,<y>,<dir> ...  B:<x>,<y> ...
 *   Q <lvl> <tick> Q:<letter>,<x>,<y>,<dir> ...
 *
 * Fields are tab separated; the lists inside C:, B: and Q: are space separated.
 *
 * Everything is stated in engine-independent terms. Positions are grid x,y rather than tile
 * indices, and directions are the letters N/W/S/E, because the two engines encode both differently
 * -- Tile World uses compass bit flags, SuperCC uses enum ordinals. Without that normalization the
 * diff is blind to a creature standing in the right place facing the wrong way, which is exactly
 * how one real divergence hid.
 *
 * The Q line exists because the T line shows only positions, and block-against-block divergences
 * on random force floors turn on WHICH block the slip pass reaches first. Position alone cannot
 * tell "different direction" from "different order".
 *
 *   java -cp "SuperCC.jar;<classdir>" TraceLevel <levelset.dat> <levelNumber> <solution.json>
 *
 * Blocks are scanned off the map rather than read from a list, on both sides, because SuperCC
 * keeps MS blocks out of its monster list and Tile World's blocks[] only holds ones that have
 * been touched.
 */
public class TraceLevel {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: TraceLevel <levelset.dat> <levelNumber> <solution.json>");
            System.exit(2);
        }
        File set = new File(args[0]);
        int levelNumber = Integer.parseInt(args[1]);
        File solutionFile = new File(args[2]);

        Solution solution = Solution.fromJSON(
                Files.readString(solutionFile.toPath(), StandardCharsets.UTF_8));

        SuperCC emulator = new SuperCC(false);
        emulator.openLevelset(set);
        /* Loaded directly under the solution's own ruleset rather than through Solution.load,
         * which would ask a question the headless emulator answers "no". */
        emulator.loadLevel(levelNumber, solution.rngSeed, solution.step, false,
                           solution.ruleset, solution.initialSlide);

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Level level = emulator.getLevel();

        emit(out, level, levelNumber);          // tick 0, before any move
        replay(out, emulator, level, levelNumber, solution);

        out.flush();
    }

    /**
     * Ticks the solution one move at a time, emitting after each.
     *
     * This mirrors Solution.tickBasicMoves rather than calling it, because that method runs the
     * whole solution in one go and there is no way to observe a tick from outside it. The
     * multi-tick and click handling has to match it exactly or the trace drifts from what a normal
     * replay would do.
     */
    private static void replay(PrintStream out, SuperCC emulator, Level level,
                               int levelNumber, Solution solution) {
        char[] moves = solution.basicMoves;
        for (int move = 0; move < moves.length; move++) {
            char c = moves[move];
            if (c == SuperCC.CHIP_RELATIVE_CLICK) {
                int x = moves[++move] - 9;
                int y = moves[++move] - 9;
                if (x == 0 && y == 0) {
                    c = SuperCC.WAIT;
                } else {
                    game.Position chipPosition = level.getChip().getPosition();
                    game.Position clickPosition = chipPosition.add(x, y);
                    level.setClick(clickPosition.getIndex());
                    c = clickPosition.clickChar(chipPosition);
                }
            }
            boolean tickedMulti = emulator.tick(c, TickFlags.PRELOADING);
            if (tickedMulti) move += level.ticksPerMove() - 1;

            emit(out, level, levelNumber);

            if (level.getChip().isDead()) break;
        }
    }

    /** One tick's worth of state: the T line, then the Q line. */
    private static void emit(PrintStream out, Level level, int levelNumber) {
        Creature chip = level.getChip();
        StringBuilder t = new StringBuilder();
        t.append("T\t").append(levelNumber)
         .append('\t').append(level.getTickNumber())
         .append('\t').append(level.getRNG().getCurrentValue())
         .append("\tchip=").append(x(chip)).append(',').append(y(chip)).append(',')
         .append(chip.isSliding() ? 1 : 0)
         .append("\tC:");
        for (int i = 0; i < level.getMonsterList().size(); i++) {
            Creature c = level.getMonsterList().get(i);
            if (c == null || c.isDead() || c.getCreatureType().isChip()) continue;
            t.append(letter(c.getCreatureType())).append(',')
             .append(x(c)).append(',').append(y(c)).append(',')
             .append(dir(c.getDirection())).append(' ');
        }
        /* Blocks come off the MAP, not the monster list -- MS blocks are not creatures, and Tile
         * World's blocks[] holds only the ones that have been touched. Both sides scan. */
        t.append("\tB:");
        for (int i = 0; i < 32 * 32; i++) {
            Tile tile = level.getLayerFG().get(i);
            if (tile == Tile.BLOCK || tile.isBlock()) {
                t.append(i % 32).append(',').append(i / 32).append(' ');
            }
        }
        out.println(t);

        StringBuilder q = new StringBuilder();
        q.append("Q\t").append(levelNumber).append('\t').append(level.getTickNumber()).append("\tQ:");
        try {
            for (Creature c : level.getSlipList()) {
                if (c == null) continue;
                q.append(letter(c.getCreatureType())).append(',')
                 .append(x(c)).append(',').append(y(c)).append(',')
                 .append(dir(c.getDirection())).append(' ');
            }
        } catch (Throwable ignored) {
            // Lynx has no slip list; an empty Q line keeps the two files aligned line for line.
        }
        out.println(q);
    }

    private static int x(Creature c) { return c.getPosition().getIndex() % 32; }
    private static int y(Creature c) { return c.getPosition().getIndex() / 32; }

    /** Tile World's creature letters, from the _crletter[] table in mslogic.c. */
    private static char letter(CreatureID id) {
        if (id.isChip()) return '@';
        switch (id) {
            case BLOCK: case ICE_BLOCK:            return '#';
            case TANK_MOVING: case TANK_STATIONARY: return 'K';
            case PINK_BALL:                        return 'b';
            case GLIDER:                           return 'G';
            case FIREBALL:                         return 'F';
            case WALKER:                           return 'W';
            case BLOB:                             return 'B';
            case TEETH:                            return 'T';
            case BUG:                              return 'U';
            case PARAMECIUM:                       return 'P';
            default:                               return '?';
        }
    }

    /** N/W/S/E, the letters both engines can agree on. */
    private static char dir(Direction d) {
        if (d == Direction.UP)    return 'N';
        if (d == Direction.LEFT)  return 'W';
        if (d == Direction.DOWN)  return 'S';
        if (d == Direction.RIGHT) return 'E';
        return '-';
    }
}
