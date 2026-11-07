import game.Tile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Writes a tiny synthetic Chip's Challenge folder for corpus.ps1 -SelfTest.
 *
 *   java -cp "SuperCC.jar;<classdir>" CorpusFixture <ccDir> <walkMoves> <stayMoves> <againMoves>
 *
 * No real level set may be committed here (docs\adr\0007), so the self-test builds its own with
 * test\DatBuilder.java, the same way the engine tests do. The layout mirrors a real CC folder:
 *
 *   data\Synth.dat      position 1 "Walk"   Chip at 1,1 with the exit two squares east
 *                       position 2 "Stay"   Chip at 1,1 with the exit far away
 *                       position 3 "Again"  the same as Walk
 *                       position 4  (no title, and a layer whose runs overrun the map, so it does
 *                                   not parse; until jc-17 the missing title alone made SuperCC's
 *                                   SavestateManager throw, but an untitled level loads now)
 *                       position 5 "Renum"  the same as Walk, but its record says it is level 9
 *                       position 6 "Click"  the same as Stay -- home for the save that throws
 *   data\Cclset.ccl     one level, "Walk" -- a set with the OTHER extension SuperCC opens
 *   data\#Hash.dat      one level, "Walk" -- a pack whose NAME starts with the header character
 *   data\Dupes.dat      position 1 "Alpha" and position 2 "Beta", BOTH recorded as level 1
 *   data\Badsig.dat     one level, "Walk", well formed, but under a signature SuperCC refuses
 *   data\Broken.dat     not a level set at all (bad signature)
 *   data\Short.dat      "Walk" and "Cut", cut off after Cut's byte count: Cut's number cannot be read
 *   data\Twins.dat      one level, "Walk" -- and data\Twins.ccl beside it, so the folder is ambiguous
 *
 *   succsave\#Hash\1_Walk-MS.json          a data line that begins with "#"      -> OK won=1
 *   succsave\Badsig\1_Walk-MS.json         its set will not open, but its record reads -> OPENFAIL,
 *                                          fingerprinted by that record, as Cclset's Walk is
 *   succsave\Broken\1_Any-MS.json          its set will not open                 -> OPENFAIL
 *   succsave\Cclset\1_Walk-MS.json         a .ccl set is found and replayed      -> OK won=1
 *   succsave\Dupes\1_Alpha-MS.json         two levels carry 1; the title picks Alpha -> OK won=1
 *   succsave\Dupes\1_Beta-MS.json          ... and here Beta, a different route  -> OK won=1 pos=2
 *   succsave\Dupes\1_Gamma-MS.json         ... and here neither                  -> AMBIGUOUS
 *   succsave\Orphan\1_Lost-MS.json         a folder with no level set            -> NODAT
 *   succsave\Short\1_Other-MS.json         Walk is the only READABLE level 1, but Cut's number is
 *                                          unknown, and the title does not confirm Walk -> AMBIGUOUS
 *   succsave\Short\1_Walk-MS.json          ... the title that does confirm it    -> OK won=1
 *   succsave\Short\2_Cut-MS.json           its record ends past the file         -> NOLEVEL, saying
 *                                          why, NOT a LOADFAIL guessed by taking 2 as a position
 *   succsave\Twins\1_Walk-MS.json          Twins.dat and Twins.ccl both exist     -> OPENFAIL AMBIGUOUS
 *   succsave\Synth\1_Walk-MS.json          <walkMoves>  -- "r-r-" reaches the exit, "r-" stops short
 *   succsave\Synth\2_Latin-MS.json         no "Encode", Latin-1 click bytes C3 BF -> four moves, as
 *                                          SuperCC reads it, not three
 *   succsave\Synth\2_Stay-MS.json          <stayMoves>  -- never reaches the exit, whichever way
 *   succsave\Synth\2_Utf-MS.json           "Encode":"UTF-8", click U+00FF as C3 BF -> three moves
 *   succsave\Synth\1_Walk-LYNX.json        Walk under LYNX                       -> OK, bg=- slip=-
 *   succsave\Synth\3_Again-MS.json         <againMoves> -- or, given "!badjson", an unparseable file
 *   succsave\Synth\4_Untitled-MS.json      its level will not load               -> LOADFAIL
 *   succsave\Synth\5_Ghost-MS.json         position 5 is level 9, and no level is 5 -> NOLEVEL,
 *                                          NOT a silent replay of Renum
 *   succsave\Synth\6_Click-MS.json         ends inside a click record            -> EXC, mid-replay
 *   succsave\Synth\7_Beyond-MS.json        no level carries this number          -> NOLEVEL
 *   succsave\Synth\9_Renum-MS.json         SuperCC names saves by the level's OWN number, so this
 *                                          belongs to position 5                 -> OK won=1 pos=5
 *   succsave\Synth\99999999999_Huge-MS.json  too many digits for any level number -> NOLEVEL
 *   succsave\Synth\readme.json             not <n>_<title>-<RULESET>.json        -> BADNAME
 *
 * The three routes are parameters so the self-test can produce REAL engine-level differences
 * between two runs rather than editing an output file and testing only the text comparison: a
 * different Walk route loses the exit, a different Stay route changes only where Chip ends up -- the
 * kind of difference an outcome-only check would miss -- and a solved Again that stops replaying at
 * all must be reported as a STATUS change, not hidden among routes that merely lost the exit.
 *
 * This class is NOT in test\: run-tests.ps1 runs every class there that has a main(), and this is a
 * generator, not a test.
 */
public class CorpusFixture {
    public static void main(String[] args) throws Exception {
        Path cc = Paths.get(args[0]);
        String walkMoves = args[1];
        String stayMoves = args[2];
        String againMoves = args[3];
        Path data = cc.resolve("data");

        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        b.level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end();
        b.level().title("Stay").tile(1, 1, Tile.CHIP_DOWN).tile(20, 20, Tile.EXIT).end();
        b.level().title("Again").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end();
        b.level().unreadable().end();                     // no title, and a record that does not parse
        b.level().number(9).title("Renum").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end();
        b.level().title("Click").tile(1, 1, Tile.CHIP_DOWN).tile(20, 20, Tile.EXIT).end();
        b.writeTo(data, "Synth.dat");

        new DatBuilder().signature(DatBuilder.SIG_MS)
            .level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .writeTo(data, "Cclset.ccl");
        new DatBuilder().signature(DatBuilder.SIG_MS)
            .level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .writeTo(data, "#Hash.dat");
        // Two records that both claim to be level 1, with exits in different directions, so a save
        // replayed on the wrong one of them cannot reach the exit.
        new DatBuilder().signature(DatBuilder.SIG_MS)
            .level().number(1).title("Alpha").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .level().number(1).title("Beta").tile(1, 1, Tile.CHIP_DOWN).tile(1, 3, Tile.EXIT).end()
            .writeTo(data, "Dupes.dat");

        // Eight bytes that are not a CC1 signature: DatParser refuses it, and SuperCC's
        // openLevelset swallows that refusal -- which is the case this exists to pin.
        Files.write(data.resolve("Broken.dat"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        // A WELL-FORMED set with a signature SuperCC does not accept: it refuses to open it, but its
        // records are perfectly readable, so its save's level fingerprint must be that record's --
        // the same as the identical "Walk" record in Cclset.ccl -- exactly as a jar that DID open it
        // would write. A fingerprint that followed the OPENFAIL would blame a jar that breaks opening
        // on the collection.
        new DatBuilder().signature(0x0004AAAC)
            .level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .writeTo(data, "Badsig.dat");

        // The same name with BOTH extensions: SuperCC files both sets' saves in one folder, so
        // which set a save belongs to cannot be told.
        for (String ext : new String[]{".dat", ".ccl"})
            new DatBuilder().signature(DatBuilder.SIG_MS)
                .level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
                .writeTo(data, "Twins" + ext);

        // Two levels, cut off right after level 2's byte count: DatParser still opens it (it skims
        // only the counts) and level 1 still plays, but level 2's NUMBER lies past the end of the
        // file -- the one way the Index can fail to know a level's number.
        Path shortSet = new DatBuilder().signature(DatBuilder.SIG_MS)
            .level().title("Walk").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .level().title("Cut").tile(1, 1, Tile.CHIP_DOWN).tile(3, 1, Tile.EXIT).end()
            .writeTo(data, "Short.dat");
        byte[] whole = Files.readAllBytes(shortSet);
        int level1Bytes = (whole[6] & 0xFF) | (whole[7] & 0xFF) << 8;
        Files.write(shortSet, Arrays.copyOf(whole, 6 + 2 + level1Bytes + 2));

        Path succsave = cc.resolve("succsave");
        solution(succsave.resolve("#Hash").resolve("1_Walk-MS.json"), "r-r-");
        solution(succsave.resolve("Badsig").resolve("1_Walk-MS.json"), "r-r-");
        solution(succsave.resolve("Broken").resolve("1_Any-MS.json"), "r-");
        solution(succsave.resolve("Cclset").resolve("1_Walk-MS.json"), "r-r-");
        solution(succsave.resolve("Dupes").resolve("1_Alpha-MS.json"), "r-r-");
        solution(succsave.resolve("Dupes").resolve("1_Beta-MS.json"), "d-d-");
        solution(succsave.resolve("Dupes").resolve("1_Gamma-MS.json"), "r-r-");
        solution(succsave.resolve("Orphan").resolve("1_Lost-MS.json"), "r-");
        solution(succsave.resolve("Short").resolve("1_Other-MS.json"), "r-r-");
        solution(succsave.resolve("Short").resolve("1_Walk-MS.json"), "r-r-");
        solution(succsave.resolve("Short").resolve("2_Cut-MS.json"), "r-r-");
        solution(succsave.resolve("Twins").resolve("1_Walk-MS.json"), "r-r-");
        Path synth = succsave.resolve("Synth");
        // The same Walk, played under LYNX: Lynx has no background layer and no slip list, and its
        // line must say so (bg=-, slip=-) rather than read as a crash.
        solution(synth.resolve("1_Walk-LYNX.json"), "r-r-", "LYNX");
        solution(synth.resolve("1_Walk-MS.json"), walkMoves);
        solution(synth.resolve("2_Stay-MS.json"), stayMoves);
        // A MODERN save ("Encode":"UTF-8") whose click move U+00FF is two bytes, C3 BF: read as UTF-8,
        // as SuperCC reads such a file, that is three moves; read as ISO-8859-1 it would be four.
        solution(synth.resolve("2_Utf-MS.json"), "r-ÿ");
        // An OLDER save: no "Encode" field, and two click moves as the single bytes C3 BF. SuperCC
        // reads such a file as ISO-8859-1 -- four moves -- where UTF-8 would read C3 BF as ONE
        // different character, three moves. The move count on its line says which happened.
        Files.write(synth.resolve("2_Latin-MS.json"),
                    "{\"Moves\":\"r-Ã¿\",\"Seed\":\"0\",\"Step\":\"EVEN\",\"Rule\":\"MS\"}"
                        .getBytes(StandardCharsets.ISO_8859_1));
        solution(synth.resolve("3_Again-MS.json"), againMoves);
        solution(synth.resolve("4_Untitled-MS.json"), "r-");
        solution(synth.resolve("5_Ghost-MS.json"), "r-r-");
        // A click record is the marker followed by two offset characters; ending right after the
        // marker makes the replay read past the end of the move list -- in the harness's loop and
        // in Solution.tickBasicMoves alike, which -CrossCheck must therefore call agreement.
        solution(synth.resolve("6_Click-MS.json"), "r-\\u0001");
        solution(synth.resolve("7_Beyond-MS.json"), "r-");
        solution(synth.resolve("9_Renum-MS.json"), "r-r-");
        solution(synth.resolve("99999999999_Huge-MS.json"), "r-");
        solution(synth.resolve("readme.json"), "r-");
    }

    /** The succsave JSON shape SuperCC itself writes (see emulator.Solution.toJSON). */
    private static void solution(Path p, String moves) throws Exception {
        solution(p, moves, "MS");
    }

    /** As Solution.toJSON writes it -- "Encode":"UTF-8" included, so these take SuperCC's UTF-8 path. */
    private static void solution(Path p, String moves, String rule) throws Exception {
        Files.createDirectories(p.getParent());
        String json = moves.equals("!badjson")
                ? "{\"Moves\":"                                   // truncated: not valid JSON
                : "{\"Moves\":\"" + moves + "\",\"Seed\":\"0\",\"Step\":\"EVEN\",\"Rule\":\"" + rule
                  + "\",\"Encode\":\"UTF-8\"}";
        Files.write(p, json.getBytes(StandardCharsets.UTF_8));
    }
}
