import emulator.SuperCC;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;
import game.Direction;
import io.DatParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Engine coverage: .dat parsing and the headless emulator. Fixtures are synthesized by DatBuilder,
 * so this needs no level set and runs anywhere -- see docs\adr\0007-synthesize-dat-fixtures.md.
 *
 * Two headless traps are load-bearing in how these tests are written, and both were found the hard
 * way rather than reasoned about:
 *
 *   * SuperCC.openLevelset() reports a bad file through throwError() and RETURNS -- it does not
 *     throw. (Before jc-11 it fell through to loadLevel() with no level set open and produced a
 *     NullPointerException instead.) Either way it never surfaces the IOException, so every
 *     error-path assertion here goes through `new DatParser(file)` directly, which does throw.
 *   * The GUI-less emulator has a null window, so anything that repaints dereferences null. These
 *     tests parse and inspect levels rather than driving the game loop.
 *
 * Run with -Djava.awt.headless=true (run-tests.ps1 does).
 */
public class EngineTest {

    public static void main(String[] args) {
        System.exit(Harness.run("EngineTest", EngineTest::body));
    }

    /** Scratch space, cleaned up for real on exit -- see Harness.tempDir. */
    private static Path tempDir(String name) throws IOException {
        return Harness.tempDir("scc-engine-" + name + "-");
    }

    /** A PNG that is well-formed in every byte but claims a width x height of its choosing. */
    private static byte[] pngClaiming(int width, int height) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream h = new java.io.DataOutputStream(ihdr);
        h.writeInt(width); h.writeInt(height);
        h.writeByte(8); h.writeByte(6); h.writeByte(0); h.writeByte(0); h.writeByte(0);   // 8-bit RGBA
        pngChunk(out, "IHDR", ihdr.toByteArray());
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(new byte[4096]);
        deflater.finish();
        byte[] z = new byte[8192];
        int n = deflater.deflate(z);
        pngChunk(out, "IDAT", java.util.Arrays.copyOf(z, n));
        pngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void pngChunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        java.io.DataOutputStream d = new java.io.DataOutputStream(out);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }

    interface IoAction { Object run() throws IOException; }

    /** The message of the IOException the action throws; "(no IOException: ...)" if it throws
     *  nothing or something else, so a check on the message fails visibly rather than passing. */
    private static String ioMessage(IoAction a) {
        try { a.run(); return "(no IOException: nothing thrown)"; }
        catch (IOException e) { return String.valueOf(e.getMessage()); }
        catch (Throwable t) { return "(no IOException: " + t + ")"; }
    }

    /** Parses level 1 of a freshly written set, under the ruleset the file declares. */
    private static Level firstLevel(Path dat) throws IOException {
        return new DatParser(dat.toFile()).parseLevel(1, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
    }

    private static void body() throws Exception {

        Harness.section("1. the .dat signature decides the ruleset");
        /* The signature is the first four bytes and is the only thing that says which engine a set
         * was authored for. These constants are asserted against the SPEC, not read back out of
         * DatParser, so a drift in the parser's table shows up here as a failure. */
        Path d1 = tempDir("sig");
        DatBuilder ms = new DatBuilder().signature(DatBuilder.SIG_MS);
        ms.level().title("MS level").end();
        Harness.eq("0x0002AAAC is MS",
                   new DatParser(ms.writeTo(d1, "ms.dat").toFile()).getRuleset(), Ruleset.MS);

        DatBuilder pg = new DatBuilder().signature(DatBuilder.SIG_MS_PG);
        pg.level().title("PG level").end();
        Harness.eq("0x0003AAAC is also MS",
                   new DatParser(pg.writeTo(d1, "pg.dat").toFile()).getRuleset(), Ruleset.MS);

        DatBuilder lynx = new DatBuilder().signature(DatBuilder.SIG_LYNX);
        lynx.level().title("Lynx level").end();
        Harness.eq("0x0102AAAC is Lynx (this is what MO3.dat carries, and why jc-9 exists)",
                   new DatParser(lynx.writeTo(d1, "lynx.dat").toFile()).getRuleset(), Ruleset.LYNX);

        DatBuilder bogus = new DatBuilder().signature(0x00000000);
        bogus.level().title("nope").end();
        Path badPath = bogus.writeTo(d1, "bogus.dat");
        boolean threw = false;
        try { new DatParser(badPath.toFile()); } catch (IOException expected) { threw = true; }
        Harness.check("an unrecognized signature is rejected, not guessed at", threw);

        Harness.section("2. level metadata survives the round trip");
        Path d2 = tempDir("meta");
        DatBuilder b2 = new DatBuilder();
        b2.level().title("Lesson Zero").password("KEYS").hint("Collect the chips.")
                  .author("Jeremy Christman").timeLimit(120).chips(3).end();
        Level l2 = firstLevel(b2.writeTo(d2, "meta.dat"));
        Harness.eq("title (optional field 3)", l2.getTitle(), "Lesson Zero");
        // Field 6 is the ENCODED password: stored XOR 0x99. Reading it back proves the parser
        // un-XORs it rather than handing back the raw bytes.
        Harness.eq("password (optional field 6, XOR 0x99 encoded)", l2.getPassword(), "KEYS");
        Harness.eq("hint (optional field 7)", l2.getHint(), "Collect the chips.");
        Harness.eq("author (optional field 9)", l2.getAuthor(), "Jeremy Christman");
        Harness.eq("chips required", l2.getChipsLeft(), 3);
        Harness.eq("level number", l2.getLevelNumber(), 1);
        /* The timer is NOT stored in seconds. LevelFactory.getTimer() converts the .dat's second
         * count with timeLimit*100 + 90: hundredths, plus the 90 hundredths of the first second
         * that have not elapsed yet. So a 120-second level starts at 12090, and asserting 120 here
         * is wrong -- pinning the conversion is the point, because it is the kind of unit that gets
         * "simplified" by someone who reads getStartTime() and assumes seconds. */
        Harness.eq("the timer is centiseconds + 90, not seconds", l2.getStartTime(), 120 * 100 + 90);

        Path d2b = tempDir("untimed");
        DatBuilder b2b = new DatBuilder();
        b2b.level().title("No clock").timeLimit(0).end();
        Harness.eq("a time limit of 0 means untimed, encoded as -2",
                   firstLevel(b2b.writeTo(d2b, "untimed.dat")).getStartTime(), -2);

        Harness.section("3. both run-length encodings decode to the right cells");
        /* A layer is RLE compressed with a 0xFF/count/code form for runs and literal bytes
         * otherwise. A mostly-empty map exercises the run branch; a hand-placed row that alternates
         * exercises the literal branch. A real level set might contain only one of the two. */
        Path d3 = tempDir("rle");
        DatBuilder b3 = new DatBuilder();
        DatBuilder.Level lv3 = b3.level().title("RLE");
        lv3.tile(1, 1, Tile.CHIP);
        for (int x = 4; x < 12; x++) lv3.tile(x, 5, (x % 2 == 0) ? Tile.WALL : Tile.DIRT);
        for (int x = 0; x < 32; x++) lv3.tile(x, 9, Tile.WATER);   // a long run
        lv3.tile(20, 20, Tile.EXIT);

        /* Assert the fixture actually CONTAINS both token forms before asserting that both decode.
         * Otherwise the claim is unfalsifiable: change the encoder's run threshold to 1 and every
         * decode assertion below still passes while the literal branch goes untested. */
        byte[] encoded = lv3.encodedForegroundLayer();
        boolean sawRun = false, sawLiteral = false;
        for (int i = 0; i < encoded.length; ) {
            if ((encoded[i] & 0xFF) == 0xFF) { sawRun = true; i += 3; }
            else { sawLiteral = true; i += 1; }
        }
        Harness.check("the fixture contains at least one 0xFF run token", sawRun);
        Harness.check("the fixture contains at least one literal token", sawLiteral);

        lv3.end();
        Level l3 = firstLevel(b3.writeTo(d3, "rle.dat"));
        Harness.eq("Chip is where it was placed", l3.getLayerFG().get(new Position(1, 1)), Tile.CHIP);
        Harness.eq("an alternating literal run decodes (wall)", l3.getLayerFG().get(new Position(4, 5)), Tile.WALL);
        Harness.eq("an alternating literal run decodes (dirt)", l3.getLayerFG().get(new Position(5, 5)), Tile.DIRT);
        Harness.eq("a 32-cell compressed run decodes at its start", l3.getLayerFG().get(new Position(0, 9)), Tile.WATER);
        Harness.eq("and at its end", l3.getLayerFG().get(new Position(31, 9)), Tile.WATER);
        Harness.eq("the exit survives", l3.getLayerFG().get(new Position(20, 20)), Tile.EXIT);
        Harness.eq("an untouched cell is floor", l3.getLayerFG().get(new Position(30, 30)), Tile.FLOOR);

        Harness.section("4. a multi-level set is indexed correctly");
        /* DatParser skims the file once, recording where each level starts. An off-by-one there
         * misreads every level after the first, which no single-level fixture would catch. */
        Path d4 = tempDir("multi");
        DatBuilder b4 = new DatBuilder();
        b4.level().title("First").chips(1).end();
        b4.level().title("Second").chips(2).end();
        b4.level().title("Third").chips(3).end();
        DatParser p4 = new DatParser(b4.writeTo(d4, "multi.dat").toFile());
        /* lastLevel() is COUNT + 1, not the count: levelStart is sized levels+1 because index 0 is
         * skipped, and lastLevel() returns its length. loadLevel() relies on that -- it treats
         * lastLevel() as the wrap-around sentinel when you page past the end. Asserted explicitly
         * because the name reads like a count and a "fix" here would break level paging. */
        Harness.eq("lastLevel() is the level count plus one (it is a wrap sentinel)", p4.lastLevel(), 4);
        Harness.eq("level 2 is the second one",
                   p4.parseLevel(2, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP).getTitle(), "Second");
        Harness.eq("level 3 is the third one",
                   p4.parseLevel(3, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP).getTitle(), "Third");
        Harness.eq("the set name comes from the file name", p4.getLevelsetName(), "multi");

        Harness.section("5. jc-7 regression: off-map monster-list entries do not stop a level opening");
        /* Optional field 10 (the MS monster-movement list) contains junk in real level sets, and
         * before jc-7 three levels across the collection would not open AT ALL because of it.
         *
         * Mode (a): x and y both far off the map. 32*160+160 = 5280, past the 1024-cell map, which
         *           threw ArrayIndexOutOfBoundsException out of the counting loop.
         * Mode (b): the nastier one. x >= 32 but 32*y+x still lands inside the map, so the multiply
         *           ALIASES onto an unrelated cell. No exception -- instead the counting and storing
         *           loops disagreed, leaving a trailing null that became an NPE in MSLevel's
         *           constructor. Only reachable when the aliased cell actually holds a monster,
         *           which is why a bug creature is placed at the aliased position here.
         *
         * Both are MS-only: getLynxMonsterList() never reads this field, which is why these levels
         * always opened under Lynx. */
        Path d5 = tempDir("jc7");

        DatBuilder b5a = new DatBuilder().signature(DatBuilder.SIG_MS);
        b5a.level().title("Off-map, out of bounds").monster(160, 160).end();
        Level l5a = firstLevel(b5a.writeTo(d5, "offmap_oob.dat"));
        Harness.eq("(a) an entry at (160,160) opens, and its title parses",
                   l5a.getTitle(), "Off-map, out of bounds");
        Harness.eq("(a) the out-of-bounds entry is discarded, not kept", l5a.getMonsterList().size(), 0);

        DatBuilder b5b = new DatBuilder().signature(DatBuilder.SIG_MS);
        // (40,1) aliases to 32*1+40 = 72, which is (8,2) -- in bounds. The BUG_UP placed there is
        // what makes this fixture bite: the old counting loop used the unchecked get(int), saw a
        // monster at the aliased cell and counted it, while the storing loop built a Position, saw
        // an invalid one, and stored nothing -- over-allocating the array and leaving a trailing
        // null that NPE'd in MSLevel's constructor. Without a real monster at (8,2) both loops
        // would agree on zero and the fixture would prove nothing.
        b5b.level().title("Off-map, aliasing")
                   .tile(8, 2, Tile.BUG_UP)
                   .monster(40, 1)
                   .end();
        Level l5b = firstLevel(b5b.writeTo(d5, "offmap_alias.dat"));
        Harness.eq("(b) an aliasing entry at (40,1) opens, and its title parses",
                   l5b.getTitle(), "Off-map, aliasing");
        Harness.eq("(b) the aliasing entry is DISCARDED -- the two loops agree on zero",
                   l5b.getMonsterList().size(), 0);

        // A well-formed entry must still be honored -- the fix discards invalid entries, it does
        // not discard the list. The MS list is built ONLY from field 10, so this is the assertion
        // that stops "discard everything" from passing the two above.
        DatBuilder b5c = new DatBuilder().signature(DatBuilder.SIG_MS);
        b5c.level().title("Valid monster").tile(8, 2, Tile.BUG_UP).monster(8, 2).end();
        Level l5c = firstLevel(b5c.writeTo(d5, "valid_monster.dat"));
        Harness.eq("a VALID monster entry is still kept", l5c.getMonsterList().size(), 1);

        Harness.section("6. the headless emulator opens a set end to end");
        /* SuperCC(false) is the GUI-less constructor the tools use. It never builds a SuccPaths,
         * which is why the paths == null guards in startingRuleset() and the window title exist --
         * this is the path that would crash without them. */
        Path d6 = tempDir("headless");
        DatBuilder b6 = new DatBuilder().signature(DatBuilder.SIG_MS);
        b6.level().title("Headless").chips(0).timeLimit(60).tile(1, 1, Tile.CHIP).end();
        File set6 = b6.writeTo(d6, "headless.dat").toFile();
        SuperCC emulator = new SuperCC(false);
        emulator.openLevelset(set6);
        Level loaded = emulator.getLevel();
        Harness.check("openLevelset loaded a level with no GUI attached", loaded != null);
        Harness.eq("it is level 1", loaded.getLevelNumber(), 1);
        Harness.eq("with the expected title", loaded.getTitle(), "Headless");
        Harness.eq("under the ruleset the file declares", loaded.getRuleset(), Ruleset.MS);

        Harness.section("6b. a levelset that cannot be read leaves the previous one alone");
        /* jc-11: openLevelset used to report the IOException and then fall THROUGH to loadLevel()
         * with dat still null, producing a NullPointerException out of startingRuleset() right
         * behind a perfectly clear "Could not read file" message. Opening the wrong file is one of
         * the commonest things a stranger does, and now that errors are recorded, a spurious NPE
         * at the top of a fresh log teaches people to ignore the log. */
        Path d6b = tempDir("badopen");
        DatBuilder good = new DatBuilder().signature(DatBuilder.SIG_MS);
        good.level().title("Still Here").chips(0).end();
        File goodSet = good.writeTo(d6b, "good.dat").toFile();

        File notALevelset = d6b.resolve("bogus.dat").toFile();
        Files.write(notALevelset.toPath(), "this is not a level set at all".getBytes());

        /* The case that ACTUALLY threw before the fix: nothing open yet, and the very first set a
         * user opens is invalid. dat stays null, so the old fall-through hit startingRuleset(). */
        SuperCC fresh = new SuperCC(false);
        fresh.openLevelset(notALevelset);
        Harness.check("a failed FIRST open leaves no level loaded, and does not throw",
                      !fresh.isLevelLoaded());
        Harness.check("and getLevel() is null rather than half-built", fresh.getLevel() == null);
        /* loadLevel is guarded too, because ArgumentParser and SeedSearch both call openLevelset
         * and then loadLevel unconditionally -- on the command-line path that NPE used to escape
         * startup entirely and leave the window half-built. */
        fresh.loadLevel(1, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Harness.check("loadLevel with no set open is a no-op, not an NPE", !fresh.isLevelLoaded());

        // And with a set already open, a bad file must not silently reset you to level 1.
        SuperCC emu2 = new SuperCC(false);
        emu2.openLevelset(goodSet);
        Harness.eq("the good set is open", emu2.getLevel().getTitle(), "Still Here");
        emu2.openLevelset(notALevelset);
        Harness.eq("the previously loaded level is still there",
                   emu2.getLevel().getTitle(), "Still Here");

        Harness.section("6c. a level that fails to load leaves the previous level and its savestates together");
        /* jc-17 (MOD in SuperCC.loadLevel): loadLevel used to assign `level` before building its
         * savestates, so a level whose savestates could not be built was left "loaded" with none, or
         * with the PREVIOUS level's. Measured before the fix, with an untitled level (whose
         * SavestateManager threw on getTitle().getBytes()): load level 2, then the untitled level 1,
         * and level 1 ran against level 2's savestates; on a fresh open, isLevelLoaded() was true with
         * getSavestates() null, so every no-level guard in jc-17 was bypassed. Now the load is all or
         * nothing. The fixture here is a level whose record does not parse (DatBuilder.unreadable()),
         * since jc-17 also makes untitled levels load -- see 6d. */
        Path d6c = tempDir("unreadable");
        DatBuilder goodFirst = new DatBuilder().signature(DatBuilder.SIG_MS);
        goodFirst.level().title("Titled").password("ABCD").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();
        goodFirst.level().title("Broken").password("EFGH").chips(0).timeLimit(100).unreadable().end();
        File setA = goodFirst.writeTo(d6c, "goodfirst.dat").toFile();
        DatBuilder badFirst = new DatBuilder().signature(DatBuilder.SIG_MS);
        badFirst.level().title("Broken").password("IJKL").chips(0).timeLimit(100).unreadable().end();
        badFirst.level().title("Second").password("MNOP").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();
        File setB = badFirst.writeTo(d6c, "badfirst.dat").toFile();

        SuperCC emu3 = new SuperCC(false);
        emu3.openLevelset(setA);
        Harness.eq("set A opens on its good level 1", emu3.getLevel().getTitle(), "Titled");
        Object level1States = emu3.getSavestates();
        emu3.loadLevel(2, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);  // unreadable: fails
        Harness.eq("loading the unreadable level 2 keeps level 1", emu3.getLevel().getLevelNumber(), 1);
        Harness.check("with level 1's own savestates, not a mismatch", emu3.getSavestates() == level1States);

        /* And openLevelset, where it is level 1 that fails. The set opens at its first level that
         * loads. Two wrong answers came first, both measured by review: keeping the new file with the
         * old level (set A's level sat under set B's file, and getJSONPath() pointed into B's save
         * folder, so a save would have written A's route there), and restoring set A outright (all of
         * set B unreachable, and every save in such a pack an OPENFAIL in the corpus harness). */
        File setC = new DatBuilder().signature(DatBuilder.SIG_MS)
                .level().title("Only").password("QRST").chips(0).timeLimit(100).unreadable().end()  // its only level
                .writeTo(d6c, "nothingloads.dat").toFile();
        emu3.openLevelset(setB);
        Harness.eq("opening set B, whose level 1 fails, opens it at level 2", emu3.getLevel().getTitle(), "Second");
        Harness.eq("and it is set B's file now", emu3.getLevelsetPath(), setB.getAbsolutePath());
        Harness.check("with level 2's own savestates", emu3.getSavestates() != level1States);

        String pathB = emu3.getLevelsetPath();
        emu3.openLevelset(setC);  // no level in it loads at all
        Harness.eq("a set where nothing loads leaves the open set's level", emu3.getLevel().getTitle(), "Second");
        Harness.eq("and its file", emu3.getLevelsetPath(), pathB);

        SuperCC emu4 = new SuperCC(false);
        emu4.openLevelset(setB);  // a FRESH open whose level 1 fails
        Harness.eq("a fresh open of set B lands on level 2", emu4.getLevel().getTitle(), "Second");
        SuperCC emu5 = new SuperCC(false);
        emu5.openLevelset(setC);  // a fresh open where nothing loads
        Harness.check("a fresh open where nothing loads leaves nothing loaded", !emu5.isLevelLoaded());
        Harness.check("and no half-built savestates", emu5.getSavestates() == null);

        Harness.section("6d. an untitled level loads, with an empty title -- as in Tile World");
        /* jc-17 (MOD in LevelFactory.makeLevel): title is optional field 3, and a level without it
         * used to carry a null title that SavestateManager's constructor dereferenced, so the level
         * could never be opened -- MO3 #12 and #14, the only two of the collection's 23,476 levels
         * that failed to load (2026-11-07). Tile World zero-fills the record, so its name is "".
         * Before the fix every check below failed: the load was refused and level 1 stayed. */
        Path d6d = tempDir("untitled");
        DatBuilder untitled = new DatBuilder().signature(DatBuilder.SIG_MS);
        untitled.level().title("Named").password("ABCD").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();
        untitled.level().password("EFGH").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();  // no field 3
        File setU = untitled.writeTo(d6d, "untitled.dat").toFile();
        SuperCC emu6 = new SuperCC(false);
        emu6.openLevelset(setU);
        Object namedStates = emu6.getSavestates();
        emu6.loadLevel(2, 0, Step.EVEN, false, Ruleset.MS, Direction.UP);
        Harness.eq("the untitled level 2 loads", emu6.getLevel().getLevelNumber(), 2);
        Harness.eq("and its title is empty, not null", emu6.getLevel().getTitle(), "");
        Harness.check("with savestates of its own", emu6.getSavestates() != null && emu6.getSavestates() != namedStates);
        Harness.eq("a titled level keeps its title", new DatParser(setU).parseLevel(1, 0, Step.EVEN, Ruleset.MS, Direction.UP).getTitle(), "Named");
        SuperCC emu7 = new SuperCC(false);
        emu7.openLevelset(setU);
        emu7.loadLevel(2, 0, Step.EVEN, false, Ruleset.LYNX, Direction.UP);
        Harness.eq("and under Lynx too", emu7.getLevel().getTitle(), "");

        /* openLevelset's message names POSITIONS (what Go to... takes), never a record's own number,
         * which the format lets differ. Before the fix it printed the landing level's record number:
         * "Levels 1 to 2 could not be loaded ... Opened at level 7." (review). The headless emulator
         * prints the message to stderr, so it is captured there. */
        DatBuilder renumbered = new DatBuilder().signature(DatBuilder.SIG_MS);
        renumbered.level().title("Bad A").password("IJKL").unreadable().end();
        renumbered.level().title("Bad B").password("MNOP").unreadable().end();
        renumbered.level().number(7).title("Lands").password("QRST").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end();
        File setR = renumbered.writeTo(d6d, "renumbered.dat").toFile();
        java.io.PrintStream realErr = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(captured, true, "UTF-8"));
        try {
            new SuperCC(false).openLevelset(setR);
        } finally {
            System.setErr(realErr);
        }
        String said = captured.toString("UTF-8");
        Harness.check("it reports the two failures by position", said.contains("Levels 1 to 2 could not be loaded"));
        Harness.check("and the landing level by its position, 3", said.contains("Opened at level 3."));
        Harness.check("never by its record number, 7", !said.contains("Opened at level 7"));

        Harness.section("6e. every built-in tileset loads from the built jar");
        /* Resource names in a jar are case-sensitive, unlike the Windows folder they are built from.
         * The jar carried resources/BW-overlay.png while TileSheet asks for bw-overlay.png, so
         * "MSCC (Black and White - Editor)" threw NullPointerException in every release before
         * jc-17 -- measured on the deployed jc-16 jar, the other four loading. Upstream f701c85
         * renamed the file. The suite runs against the built jar, so this sees what ships. */
        for (graphics.TileSheet sheet : graphics.TileSheet.values()) {
            if (sheet == graphics.TileSheet.CUSTOM) continue;   // its images are the user's: 6f
            String problem = null;
            try {
                java.awt.image.BufferedImage[] images = sheet.getTileSheets(null, 16, 16);
                if (images.length != 2 || images[0] == null || images[1] == null) problem = "missing images";
            } catch (Throwable t) {
                problem = t.toString();
            }
            Harness.check("tileset " + sheet + " loads" + (problem == null ? "" : " (" + problem + ")"), problem == null);
        }

        Harness.section("6f. the custom tileset (upstream d78f6f3) fails in words, and startup falls back");
        /* Upstream threw an IOException with no message when no images were chosen (its callers
         * show getMessage(), so the user read "null"), and a file that is not an image as a
         * NullPointerException, which no caller catches. Each failure is now an IOException that
         * says what is wrong. And a tileset number from the settings file that cannot be drawn --
         * custom with its images gone, or a number this version does not have (which used to stop
         * startup) -- draws the fallback and says why. */
        Path d6f = tempDir("custom-tiles");
        File base = d6f.resolve("base.png").toFile(), overlay = d6f.resolve("overlay.png").toFile();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", base);
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", overlay);
        File notImage = d6f.resolve("notes.png").toFile();
        Files.writeString(notImage.toPath(), "this is text, not a picture");
        java.awt.image.BufferedImage[] custom = graphics.TileSheet.loadCustom(base, overlay, 10, 12);
        Harness.check("two real images load, scaled to 7 x 16 tiles of the asked size",
                custom.length == 2 && custom[0].getWidth() == 70 && custom[0].getHeight() == 192
                && custom[1].getWidth() == 70 && custom[1].getHeight() == 192);
        Harness.check("a missing image is an IOException naming the file",
                ioMessage(() -> graphics.TileSheet.loadCustom(base, d6f.resolve("gone.png").toFile(), 10, 10)).contains("gone.png"));
        Harness.check("a file that is not an image is an IOException that says so",
                ioMessage(() -> graphics.TileSheet.loadCustom(notImage, overlay, 10, 10)).startsWith("Not an image"));
        Harness.check("custom with no settings loaded says that, not that no images were chosen",
                ioMessage(() -> graphics.TileSheet.CUSTOM.getTileSheets(null, 10, 10)).contains("not loaded here"));
        SuperCC withSettings = new SuperCC(false);
        java.lang.reflect.Field pathsField = SuperCC.class.getDeclaredField("paths");
        pathsField.setAccessible(true);
        pathsField.set(withSettings, io.SuccPaths.load(d6f.resolve(io.SuccPaths.SETTINGS_FILE_NAME).toFile()));
        Harness.check("custom with settings but no images chosen says so, rather than \"null\"",
                ioMessage(() -> graphics.TileSheet.CUSTOM.getTileSheets(withSettings, 10, 10)).startsWith("No images have been chosen"));
        /* loadForRuleset: what a ruleset's settings say to draw. Custom is a switch; the number beside
         * it is always a built-in tileset, and it is what an older build shows from the same file. */
        io.SuccPaths sp = withSettings.getPaths();
        graphics.TileSheet dflt = graphics.TileSheet.CCEDIT_TW;
        // A fallback DIFFERENT from tileset 0, so "drew the stored number" cannot pass as "fell back" (review).
        graphics.TileSheet other = graphics.TileSheet.TW;
        graphics.TileSheet.Choice fresh6 = graphics.TileSheet.loadForRuleset(withSettings, sp, false, other, 10, 10);
        Harness.check("a fresh settings file draws built-in tileset 0 for MS, with no problem",
                fresh6.sheet() == graphics.TileSheet.values()[0] && fresh6.problem() == null);
        sp.setBuiltInTileset(false, graphics.TileSheet.MSCC.ordinal());
        sp.setCustomTileset(false, base.getAbsolutePath(), overlay.getAbsolutePath());
        graphics.TileSheet.Choice drawn = graphics.TileSheet.loadForRuleset(withSettings, sp, false, dflt, 10, 10);
        Harness.check("with MS's custom switch on, MS draws the chosen images, with no problem", drawn.sheet() == graphics.TileSheet.CUSTOM && drawn.problem() == null);
        graphics.TileSheet.Choice lynx6 = graphics.TileSheet.loadForRuleset(withSettings, sp, true, other, 10, 10);
        Harness.check("and Lynx still draws its own built-in tileset, with no problem",
                lynx6.sheet() == graphics.TileSheet.values()[0] && lynx6.problem() == null);
        Files.move(base.toPath(), d6f.resolve("moved-away.png"));
        graphics.TileSheet.Choice gone6 = graphics.TileSheet.loadForRuleset(withSettings, sp, false, dflt, 10, 10);
        Harness.check("custom whose image is gone falls back to the ruleset's BUILT-IN choice, MSCC -- what an older build shows -- not the default ("
                + gone6.sheet() + ")", gone6.sheet() == graphics.TileSheet.MSCC && gone6.problem() != null && gone6.problem().contains("MSCC"));
        Files.move(d6f.resolve("moved-away.png"), base.toPath());
        File five = Harness.tempDir("scc-engine-five-").resolve(io.SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(five.toPath(), "[Graphics]\nTilesheetNum = 5\nCustomBaseImage = " + base.getAbsolutePath()
                + "\nCustomOverlayImage = " + overlay.getAbsolutePath() + "\n");
        graphics.TileSheet.Choice stored5 = graphics.TileSheet.loadForRuleset(withSettings, io.SuccPaths.load(five), false, dflt, 10, 10);
        Harness.check("a stored TilesheetNum = 5 is NOT taken as custom (the switch is off): it is refused like any number out of range ("
                + stored5.sheet() + ")", stored5.sheet() == dflt && stored5.problem() != null && stored5.problem().contains("not a built-in tileset"));

        java.io.File huge = d6f.resolve("huge.png").toFile();
        // Well-formed PNGs (real CRCs, a deflated IDAT; the review's construction) whose headers claim
        // more pixels than a tileset image may have. 50000 x 50000 made ImageIO throw
        // IllegalArgumentException, past every fallback; 46000 x 46000 stays under ImageIO's own
        // overflow check and asked for about 8 GB -- an OutOfMemoryError. Both are refused from the
        // header now, before anything is decoded.
        // A GIF that ends (trailer 0x3B) right after its screen descriptor, named .png (ImageIO sniffs content, not the name):
        // its reader throws IndexOutOfBoundsException from getWidth(0) -- a RuntimeException that
        // skipped every fallback until the loader turned any of them into an IOException (review).
        java.io.File gif = d6f.resolve("truncated.png").toFile();
        Files.write(gif.toPath(), new byte[] {'G', 'I', 'F', '8', '9', 'a', 10, 0, 10, 0, 0, 0, 0, 0x3B});
        String gifMsg = ioMessage(() -> graphics.TileSheet.loadCustom(gif, overlay, 10, 10));
        Harness.check("a GIF with no image in it is an IOException that says so (" + gifMsg + ")",
                gifMsg.startsWith("Not an image") && gifMsg.contains("truncated.png"));
        for (int side : new int[] {50000, 46000}) {
            Files.write(huge.toPath(), pngClaiming(side, side));
            String hugeMsg = ioMessage(() -> graphics.TileSheet.loadCustom(huge, overlay, 10, 10));
            Harness.check("an image whose header claims " + side + " x " + side + " is refused, unread, in words (" + hugeMsg + ")",
                    hugeMsg.startsWith("Too large") && hugeMsg.contains("huge.png") && hugeMsg.contains(side + " x " + side));
        }
        graphics.TileSheet fallback = graphics.TileSheet.CCEDIT_TW;
        graphics.TileSheet.Choice ok = graphics.TileSheet.load(null, graphics.TileSheet.MSCC.ordinal(), fallback, 10, 10);
        Harness.check("a good number loads that tileset, with no problem", ok.sheet() == graphics.TileSheet.MSCC && ok.problem() == null);
        graphics.TileSheet.Choice gone = graphics.TileSheet.load(null, graphics.TileSheet.CUSTOM.ordinal(), fallback, 10, 10);
        Harness.check("custom that cannot load falls back, and says why",
                gone.sheet() == fallback && gone.images().length == 2 && gone.problem() != null && gone.problem().contains("custom"));
        graphics.TileSheet.Choice range = graphics.TileSheet.load(null, 99, fallback, 10, 10);
        Harness.check("a number this version does not have falls back, and names the number",
                range.sheet() == fallback && range.problem() != null && range.problem().contains("99"));
        graphics.TileSheet.Choice negative = graphics.TileSheet.load(null, -1, fallback, 10, 10);
        Harness.check("so does a negative one", negative.sheet() == fallback && negative.problem() != null);

        Harness.section("7. the full collection fingerprint (local only)");
        /* Synthesized fixtures catch format and edge-case regressions. They cannot speak for the
         * 21,838 levels in the real collection, and creature ORDER drives MS behavior, so any
         * change near the monster list still wants the wide check that jc-7 used. Point this at a
         * folder of .dat files to run it; it SKIPS otherwise, exactly like -Dsupercc.mo3. */
        String collection = System.getProperty("supercc.collection");
        if (collection == null || !new File(collection).isDirectory()) {
            Harness.skip("no -Dsupercc.collection given (level sets are not in this repo)");
        } else {
            File[] sets = new File(collection).listFiles((dir, n) -> n.toLowerCase().endsWith(".dat"));
            int opened = 0, failed = 0, levels = 0;
            for (File set : (sets == null ? new File[0] : sets)) {
                try {
                    DatParser parser = new DatParser(set);
                    /* lastLevel() is the level COUNT PLUS ONE (DatParser skips index 0), so the
                     * last level is lastLevel() - 1 -- the bound openLevelset uses. Until jc-17
                     * this loop ran to <= lastLevel(), read one past the end of every set, and
                     * failed on all 331 when run on the collection (2026-11-04). */
                    for (int i = 1; i < parser.lastLevel(); i++) {
                        parser.parseLevel(i, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
                        levels++;
                    }
                    opened++;
                } catch (Exception e) {
                    failed++;
                    System.out.println("        could not fully open " + set.getName() + ": " + e);
                }
            }
            Harness.check("every level of every set in " + collection + " opens"
                          + " (" + opened + " sets ok, " + failed + " failed, " + levels + " levels)",
                          failed == 0 && opened > 0);
        }
    }
}
