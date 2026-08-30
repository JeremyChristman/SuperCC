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
            int opened = 0, failed = 0;
            for (File set : (sets == null ? new File[0] : sets)) {
                try {
                    DatParser parser = new DatParser(set);
                    for (int i = 1; i <= parser.lastLevel(); i++)
                        parser.parseLevel(i, 0, Step.EVEN, Ruleset.CURRENT, Direction.UP);
                    opened++;
                } catch (Exception e) {
                    failed++;
                    System.out.println("        could not fully open " + set.getName() + ": " + e);
                }
            }
            Harness.check("every level of every set in " + collection + " opens"
                          + " (" + opened + " sets ok, " + failed + " failed)", failed == 0);
        }
    }
}
