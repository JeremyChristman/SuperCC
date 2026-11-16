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

    /** The loaded level's title, or "(no level)" -- upstream's open (jc-21 to jc-28) can leave none
     *  (UB-14), and a check on a build like that must fail rather than throw out of its section. */
    private static String titleOf(SuperCC emu) {
        return emu.getLevel() == null ? "(no level)" : emu.getLevel().getTitle();
    }

    /** Settings whose "always open in MS?" question -- asked for the starting ruleset before every
     *  level an open tries -- throws an Error on its Nth asking, standing in for one out of
     *  parseLevel itself. Before that it answers no. */
    private static final class ErrorOnRulesetQuestion extends io.SuccPaths {
        private final int throwOnCall;
        private int calls;
        ErrorOnRulesetQuestion(File settings, int throwOnCall) throws IOException {
            super(settings);
            this.throwOnCall = throwOnCall;
        }
        @Override public boolean getAlwaysOpenInMS() {
            if (++calls >= throwOnCall) throw new LinkageError("planted by EngineTest 6c");
            return false;
        }
    }

    /** A parser for `set` whose level at `position` parses as usual but cannot have its savestates
     *  built -- the one way a load fails after parseLevel succeeds (UB-15). As an Exception, the
     *  level has no title, which SavestateManager's constructor dereferences (MO3 #12 and #14 were
     *  such levels until jc-17 titled them "", 6d); as an Error, saving its state throws one, standing
     *  in for a class that will not load (the jc-29 failure-mode review's replaced jar). Every other
     *  level, and everything else about that one, is the file's own. */
    private static final class NoSavestatesAt extends DatParser {
        private final int position;
        private final boolean anError;
        NoSavestatesAt(File set, int position, boolean anError) throws IOException {
            super(set);
            this.position = position;
            this.anError = anError;
        }
        @Override public Level parseLevel(int n, int rngSeed, Step step, Ruleset rules, Direction slide) throws IOException {
            Level real = super.parseLevel(n, rngSeed, step, rules, slide);
            if (n != position) return real;
            return (Level) java.lang.reflect.Proxy.newProxyInstance(Level.class.getClassLoader(),
                    new Class<?>[]{ Level.class }, (proxy, method, args) -> {
                        if (!anError && method.getName().equals("getTitle")) return null;
                        if (anError && method.getName().equals("save")) throw new LinkageError("planted by EngineTest 6c");
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    /** One of the emulator's private fields, made accessible -- its settings (`paths`), which a
     *  headless emulator never builds, or the open set's parser (`dat`). The one place these tests
     *  reach into SuperCC by name. */
    private static java.lang.reflect.Field field(String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = SuperCC.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Puts a parser into a headless emulator, as if its set had been opened through it. */
    private static void installParser(SuperCC emu, DatParser parser) throws ReflectiveOperationException {
        field("dat").set(emu, parser);
    }

    /** Settings that, at their first "always open in MS?" asking, swap the set an open has just read
     *  for a NoSavestatesAt parser of the same file. An open makes its own parser, and that asking --
     *  for the starting ruleset, after the file is read and before each level is loaded from it -- is
     *  the one way in. Always answers no. Records which file it swapped, so a check can confirm it
     *  was the one being opened (were the asking ever moved before the file is read, it would be the
     *  previous set's). */
    private static final class NoSavestatesOnOpen extends io.SuccPaths {
        private final SuperCC emu;
        private final int position;
        private final boolean anError;
        String swapped;
        NoSavestatesOnOpen(File settings, SuperCC emu, int position, boolean anError) throws IOException {
            super(settings);
            this.emu = emu;
            this.position = position;
            this.anError = anError;
        }
        @Override public boolean getAlwaysOpenInMS() {
            if (swapped == null) {
                swapped = emu.getLevelsetPath();
                try {
                    installParser(emu, new NoSavestatesAt(new File(swapped), position, anError));
                } catch (ReflectiveOperationException | IOException e) {
                    throw new IllegalStateException("EngineTest 6c could not swap the parser", e);
                }
            }
            return false;
        }
    }

    /** The title the loaded savestates were built for, or "(no savestates)" -- which level they
     *  belong to, since SavestateManager keeps its level's title. */
    private static String savestatesTitle(SuperCC emu) throws IOException {
        return emu.getSavestates() == null ? "(no savestates)" : new String(emu.getSavestates().getLevelTitle(), "Windows-1252");
    }

    /** What r printed to stderr -- where the headless emulator reports its errors. */
    private static String captureStderr(Runnable r) throws IOException {
        java.io.PrintStream before = System.err;
        java.io.ByteArrayOutputStream said = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(said, true, "UTF-8"));
        try {
            r.run();
        } finally {
            System.setErr(before);
        }
        return said.toString("UTF-8");
    }

    /** Puts settings into a headless emulator, which never builds any of its own. */
    private static void installSettings(SuperCC emu, io.SuccPaths settings) throws ReflectiveOperationException {
        field("paths").set(emu, settings);
    }

    /** Runs r: true if it threw an Error (an Exception counts as false, as does no throw). */
    private static boolean throwsAnError(Runnable r) {
        try { r.run(); return false; }
        catch (Error expected) { return true; }
        catch (RuntimeException e) { return false; }
    }

    /** Whether a level set's file is open, loaded level or not (SuperCC.dat; getLevelsetPath()
     *  throws when none is). */
    private static boolean aSetIsOpen(SuperCC emu) throws ReflectiveOperationException {
        return field("dat").get(emu) != null;
    }

    /** A headless emulator whose settings file names these two custom tileset images (null: none
     *  chosen). Written as a FILE, which every build reads the same way (6f). */
    private static SuperCC withCustom(File base, File overlay) throws Exception {
        Path dir = Harness.tempDir("scc-engine-custom-");
        File ini = dir.resolve(io.SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(ini.toPath(), "[Graphics]\nCustomBaseImage = " + (base == null ? "" : base.getAbsolutePath())
                + "\nCustomOverlayImage = " + (overlay == null ? "" : overlay.getAbsolutePath()) + "\n");
        SuperCC emu = new SuperCC(false);
        installSettings(emu, io.SuccPaths.load(ini));
        return emu;
    }

    /** What loading the custom tileset did, comparably: "loads", "IOException: " and the FIRST line
     *  of its message (the lines after it hold temp paths), or "throws " and the class (UB-19). */
    private static String customOutcome(SuperCC emu) {
        try {
            graphics.TileSheet.CUSTOM.getTileSheets(emu, 10, 10);
            return "loads";
        } catch (IOException e) {
            return "IOException: " + (e.getMessage() == null ? "(no message)" : e.getMessage().split("\n")[0]);
        } catch (Throwable t) {
            return "throws " + t.getClass().getSimpleName();
        }
    }

    /** The whole message of loading the custom tileset's failure, to check what it names; "loads", or
     *  "throws " and the class, when there is none. */
    private static String customMessage(SuperCC emu) {
        try {
            graphics.TileSheet.CUSTOM.getTileSheets(emu, 10, 10);
            return "loads";
        } catch (IOException e) {
            return String.valueOf(e.getMessage());
        } catch (Throwable t) {
            return "throws " + t.getClass().getSimpleName();
        }
    }

    /** TileSheet.load(emu, number, fallback, 10, 10), reached by reflection (6g): the Choice it
     *  returned, or what happened instead as a String -- "throws <class>: <first line>", or "no
     *  TileSheet.load" on a jar from before UB-20's fix. */
    private static Object tilesetLoad(SuperCC emu, int number, graphics.TileSheet fallback) {
        try {
            return graphics.TileSheet.class.getMethod("load", SuperCC.class, int.class, graphics.TileSheet.class, int.class, int.class)
                    .invoke(null, emu, number, fallback, 10, 10);
        } catch (NoSuchMethodException e) {
            return "no TileSheet.load";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause();
            return "throws " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).split("\n")[0];
        } catch (ReflectiveOperationException e) {
            return "cannot call TileSheet.load: " + e;
        }
    }

    /** A record component of the Choice tilesetLoad returned. */
    private static Object part(Object choice, String name) throws ReflectiveOperationException {
        return choice.getClass().getMethod(name).invoke(choice);
    }

    /** tilesetLoad, comparably: "<sheet> | <the problem's first line, or (no problem)> | images
     *  <count>", or what happened instead. */
    private static String tilesetChoice(SuperCC emu, int number, graphics.TileSheet fallback) {
        Object choice = tilesetLoad(emu, number, fallback);
        if (choice instanceof String) return (String) choice;
        try {
            Object problem = part(choice, "problem");
            java.awt.image.BufferedImage[] images = (java.awt.image.BufferedImage[]) part(choice, "images");
            long real = images == null ? -1 : java.util.Arrays.stream(images).filter(java.util.Objects::nonNull).count();
            return ((graphics.TileSheet) part(choice, "sheet")).name() + " | " + (problem == null ? "(no problem)" : ((String) problem).split("\n")[0])
                    + " | images " + (images == null ? "null" : real == images.length ? String.valueOf(real) : real + " of " + images.length);
        } catch (ReflectiveOperationException e) {
            return "cannot read TileSheet.Choice: " + e;
        }
    }

    /** The whole problem tilesetLoad reports, or what happened instead. */
    private static String tilesetProblem(SuperCC emu, int number, graphics.TileSheet fallback) {
        Object choice = tilesetLoad(emu, number, fallback);
        if (choice instanceof String) return (String) choice;
        try {
            return String.valueOf(part(choice, "problem"));
        } catch (ReflectiveOperationException e) {
            return "cannot read TileSheet.Choice: " + e;
        }
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
         * getSavestates() null, so every no-level guard in jc-17 was bypassed. jc-17 made the load all
         * or nothing; the upstream baseline reverted that (UB-15, jc-21 to jc-29), and the fix layer
         * put it back in jc-30 (MOD (Jeremy, fix layer, UB-15)). No real level fails that way now
         * that untitled levels load (6d), so the first fixture here is a level whose record does not
         * parse (DatBuilder.unreadable()), which fails before anything is assigned -- those checks
         * hold on either code -- and the last checks of this section break a level AFTER it parses. */
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
        /* loadLevel still REPORTS its failure (the headless emulator prints it to stderr): since
         * UB-14's fix its body is tryLoadLevel, which only returns the failure, and loadLevel reports
         * it -- a loadLevel that dropped it would leave Go to..., Next and the rest silent on a level
         * that will not load (planted: it passed every other check). */
        String loadSaid = captureStderr(() -> emu3.loadLevel(2, 0, Step.EVEN, false, Ruleset.MS, Direction.UP));  // unreadable: fails
        Harness.check("loading the unreadable level 2 reports it (\"Could not load level: ...\")",
                      loadSaid.contains("Could not load level: "));
        Harness.eq("loading the unreadable level 2 keeps level 1", emu3.getLevel().getLevelNumber(), 1);
        Harness.check("with level 1's own savestates, not a mismatch", emu3.getSavestates() == level1States);

        /* And openLevelset, where it is level 1 that fails. The right answer -- jc-17 to jc-20's,
         * and from jc-29 (MOD (Jeremy, fix layer, UB-14)) -- is to open the set at its first level
         * that loads. Two wrong answers came first, both measured by review: keeping the new file
         * with the old level (set A's level sat under set B's file, and getJSONPath() pointed into
         * B's save folder, so a save would have written A's route there), and restoring set A
         * outright (all of set B unreachable, and every save in such a pack an OPENFAIL in the corpus
         * harness). The first of those is upstream's behavior, which jc-21 to jc-28 had (these
         * checks were pinned at it). */
        File setC = new DatBuilder().signature(DatBuilder.SIG_MS)
                .level().title("Only").password("QRST").chips(0).timeLimit(100).unreadable().end()  // its only level
                .writeTo(d6c, "nothingloads.dat").toFile();
        emu3.openLevelset(setB);
        Harness.eq("opening set B, whose level 1 fails, opens it at level 2", titleOf(emu3), "Second");
        Harness.eq("and it is set B's file now", emu3.getLevelsetPath(), setB.getAbsolutePath());
        Harness.check("with level 2's own savestates", emu3.getSavestates() != level1States);

        String pathB = emu3.getLevelsetPath();
        emu3.openLevelset(setC);  // no level in it loads at all
        Harness.eq("a set where nothing loads leaves the open set's level", titleOf(emu3), "Second");
        Harness.eq("and its file", emu3.getLevelsetPath(), pathB);

        SuperCC emu4 = new SuperCC(false);
        emu4.openLevelset(setB);  // a FRESH open whose level 1 fails
        Harness.eq("a fresh open of set B lands on level 2", titleOf(emu4), "Second");
        SuperCC emu5 = new SuperCC(false);
        emu5.openLevelset(setC);  // a fresh open where nothing loads
        Harness.check("a fresh open where nothing loads leaves nothing loaded", !emu5.isLevelLoaded());
        Harness.check("and no half-built savestates", emu5.getSavestates() == null);
        /* UB-30's state, which jc-28's menu guard made a dead end: a set's FILE open with no level
         * loaded, so Go to... (guarded) cannot reach its levels. Upstream's open (jc-21 to jc-28)
         * left it after both fresh opens above; from jc-29 an open either loads a level or puts the
         * previous set -- here none -- back. */
        Harness.check("and no set is left open without a level (UB-30)", !aSetIsOpen(emu5));

        /* An ERROR escaping the open -- not an Exception, which tryLoadLevel catches -- still puts
         * the previous set back. The jc-29 failure-mode review measured one: a jar replaced under a
         * running instance threw NoClassDefFoundError out of parseLevel, and that left set B's file
         * under set A's level (getJSONPath() pointing into B's save folder), or on a fresh open B's
         * file with no level at all (UB-30). Here the Error comes from the settings, which the loop
         * asks for the starting ruleset before every level it tries: a SuccPaths that throws. */
        Path d6cErr = tempDir("error");
        File errSettings = d6cErr.resolve("succ_settings.ini").toFile();
        Files.write(errSettings.toPath(), new byte[0]);
        SuperCC emu8 = new SuperCC(false);
        emu8.openLevelset(setA);
        String pathA = emu8.getLevelsetPath();
        Object levelA = emu8.getLevel();
        installSettings(emu8, new ErrorOnRulesetQuestion(errSettings, 1));
        Harness.check("an Error thrown during an open escapes it (it is not swallowed)", throwsAnError(() -> emu8.openLevelset(setB)));
        Harness.eq("and the open set's file is put back", emu8.getLevelsetPath(), pathA);
        Harness.check("with the open set's level, still loaded", emu8.getLevel() == levelA);
        SuperCC emu9 = new SuperCC(false);
        installSettings(emu9, new ErrorOnRulesetQuestion(errSettings, 1));
        Harness.check("and on a fresh open, the Error escapes too", throwsAnError(() -> emu9.openLevelset(setB)));
        Harness.check("leaving no set open without a level (UB-30)", !emu9.isLevelLoaded() && !aSetIsOpen(emu9));
        //  And an Error that comes AFTER an ordinary failure: set B's level 1 fails to load (an
        //  Exception, counted) and the Error comes as the loop moves on to level 2 (review).
        SuperCC emu10 = new SuperCC(false);
        emu10.openLevelset(setA);
        Object levelA10 = emu10.getLevel();
        installSettings(emu10, new ErrorOnRulesetQuestion(errSettings, 2));
        Harness.check("an Error after level 1 failed normally escapes too", throwsAnError(() -> emu10.openLevelset(setB)));
        Harness.check("and still puts the open set back, its level loaded",
                      emu10.getLevelsetPath().equals(pathA) && emu10.getLevel() == levelA10);

        /* A level that PARSES but whose savestates cannot be built (UB-15). jc-21 to jc-29 assigned
         * the level first, so such a level was left "loaded" with no savestates, or the previous
         * level's: past the menu guard, which asks only isLevelLoaded() (UB-11), and counted by the
         * open as a level that loaded, since it tells one by identity (UB-14). From jc-30 nothing is
         * assigned. The parser that breaks the level (NoSavestatesAt) goes in by reflection for a
         * load, and through the settings for an open, which makes its own. */
        File setT = new DatBuilder().signature(DatBuilder.SIG_MS)
                .level().title("One").password("UVWX").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end()
                .level().title("Two").password("YZAB").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end()
                .writeTo(d6c, "twogood.dat").toFile();
        File setL = new DatBuilder().signature(DatBuilder.SIG_MS)
                .level().title("Lone").password("CDEF").chips(0).timeLimit(100).tile(3, 5, Tile.CHIP_RIGHT).end()
                .writeTo(d6c, "lone.dat").toFile();
        //  A load (what Go to..., Next, Restart and a solution's level all do).
        SuperCC emu11 = new SuperCC(false);
        emu11.openLevelset(setT);
        Object one = emu11.getLevel();
        Object oneStates = emu11.getSavestates();
        installParser(emu11, new NoSavestatesAt(setT, 2, false));
        String said11 = captureStderr(() -> emu11.loadLevel(2, 0, Step.EVEN, false, Ruleset.MS, Direction.UP));
        //  (One check, not two: jc-29 kept level 1's savestates too, under the broken level 2 -- review.)
        Harness.check("loading a level 2 whose savestates cannot be built keeps level 1, with its own savestates",
                      emu11.getLevel() == one && emu11.getSavestates() == oneStates);
        Harness.check("and reports it (\"Could not load level: ...\")", said11.contains("Could not load level: "));
        installParser(emu11, new NoSavestatesAt(setT, 2, true));
        Harness.check("an Error building them escapes the load",
                      throwsAnError(() -> emu11.loadLevel(2, 0, Step.EVEN, false, Ruleset.MS, Direction.UP)));
        Harness.check("and keeps level 1 and its savestates too", emu11.getLevel() == one && emu11.getSavestates() == oneStates);
        //  An open: a fresh one, a set whose only level fails that way, and an Error.
        SuperCC emu12 = new SuperCC(false);
        NoSavestatesOnOpen hook12 = new NoSavestatesOnOpen(errSettings, emu12, 1, false);
        installSettings(emu12, hook12);
        String said12 = captureStderr(() -> emu12.openLevelset(setT));
        Harness.eq("a fresh open whose level 1 cannot have savestates lands on level 2", titleOf(emu12), "Two");
        Harness.eq("with level 2's own savestates", savestatesTitle(emu12), "Two");
        Harness.check("and reports level 1 by position",
                      said12.contains("Level 1 could not be loaded") && said12.contains("Opened at level 2."));
        SuperCC emu13 = new SuperCC(false);
        emu13.openLevelset(setT);
        Object levelT = emu13.getLevel();
        String pathT = emu13.getLevelsetPath();
        NoSavestatesOnOpen hook13 = new NoSavestatesOnOpen(errSettings, emu13, 1, false);
        installSettings(emu13, hook13);
        String said13 = captureStderr(() -> emu13.openLevelset(setL));
        Harness.check("a set whose only level cannot have savestates leaves the open set and its level",
                      emu13.getLevel() == levelT && emu13.getLevelsetPath().equals(pathT));
        Harness.check("and says no level in it could be loaded", said13.contains("No level in lone.dat could be loaded"));
        SuperCC emu14 = new SuperCC(false);
        emu14.openLevelset(setT);
        Object levelT14 = emu14.getLevel();
        NoSavestatesOnOpen hook14 = new NoSavestatesOnOpen(errSettings, emu14, 1, true);
        installSettings(emu14, hook14);
        Harness.check("an Error building level 1's savestates escapes the open", throwsAnError(() -> emu14.openLevelset(setL)));
        Harness.check("and puts the open set back, its level loaded",
                      emu14.getLevelsetPath().equals(pathT) && emu14.getLevel() == levelT14);
        Harness.check("(each open above broke a level of the set it was opening, not the previous set's)",
                      setT.getPath().equals(hook12.swapped) && setL.getPath().equals(hook13.swapped)
                      && setL.getPath().equals(hook14.swapped));

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
        String said = captureStderr(() -> new SuperCC(false).openLevelset(setR));
        /* (jc-21 to jc-28 tried level 1 only and reported it as any load, "Could not load level: ...",
         * so the first two were pinned there; the third holds either way. MOD (Jeremy, fix layer,
         * UB-14).) */
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

        Harness.section("6f. the custom tileset (upstream d78f6f3): what each failure says");
        /* jc-18 to jc-21 made every failure an IOException that says what is wrong, refused an image
         * whose header claims more pixels than a tileset may have before decoding it, and fell back
         * to a tileset that can be drawn -- at startup, at a level set's ruleset swap and in the
         * window -- naming the reason once a session.
         *
         * ⚠ UPSTREAM BASELINE (from jc-22): all of that was REVERTED (FORK.md, "The upstream
         * baseline"; BASELINE-ISSUES.md UB-16 to UB-21), and comes back one fix at a time (FORK.md,
         * "The fix layer"). MOD (Jeremy, fix layer, UB-19): the loader is back from jc-33, so every
         * failure below is an IOException again, checked by the first line of its message (the
         * lines after it hold temp paths) and, where there is one, that it names the file. (On
         * jc-22 to jc-32 each was pinned at what upstream's ImageIO.read did.) The fallbacks live in
         * methods upstream does not have (UB-20, UB-21); what upstream does instead at startup and at
         * a swap was measured by launching the program. The settings are written as a FILE here,
         * which both codes read, so this section runs on either. */
        Path d6f = tempDir("custom-tiles");
        File base = d6f.resolve("base.png").toFile(), overlay = d6f.resolve("overlay.png").toFile();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", base);
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", overlay);
        File notImage = d6f.resolve("notes.png").toFile();
        Files.writeString(notImage.toPath(), "this is text, not a picture");

        java.awt.image.BufferedImage[] custom = graphics.TileSheet.CUSTOM.getTileSheets(withCustom(base, overlay), 10, 12);
        Harness.check("two real images load, scaled to 7 x 16 tiles of the asked size",
                custom.length == 2 && custom[0].getWidth() == 70 && custom[0].getHeight() == 192
                && custom[1].getWidth() == 70 && custom[1].getHeight() == 192);
        // The picture must be closed once read: jc-18's first loader leaked a handle on every image it
        // read (closing ImageIO's stream did not close the one it wrapped), which on Windows LOCKS the
        // file, so the user could not move or replace their own tileset while SuperCC ran. A move of
        // a picture just loaded is what caught it then, and checks the file read in place now.
        Path movedAway = d6f.resolve("moved-away.png");
        boolean moved;
        try { Files.move(base.toPath(), movedAway); Files.move(movedAway, base.toPath()); moved = true; }
        catch (IOException e) { moved = false; }
        Harness.check("a picture just loaded is not left locked: it can be moved, and back", moved && base.isFile());
        // ImageIO caches a stream in a temp file by default, and a cache folder it cannot use failed
        // the load -- reported as the picture's fault. The loader reads a picture file in place, with
        // no cache (jc-33 reviews). ImageIO's cache folder is set to one that is then deleted, and the
        // one found here is put back afterward (it is the whole JVM's).
        Path cacheDir = Files.createDirectories(d6f.resolve("imageio-cache"));
        File cacheBefore = javax.imageio.ImageIO.getCacheDirectory();
        String withCacheGone, builtInWithCacheGone;
        try {
            javax.imageio.ImageIO.setCacheDirectory(cacheDir.toFile());
            Files.delete(cacheDir);
            withCacheGone = customOutcome(withCustom(base, overlay));
            // And a built-in sheet, read from the jar, which goes the other way (jc-33 review).
            try { graphics.TileSheet.TW.getTileSheets(null, 10, 10); builtInWithCacheGone = "loads"; }
            catch (Throwable t) { builtInWithCacheGone = "throws " + t; }
        } finally {
            javax.imageio.ImageIO.setCacheDirectory(cacheBefore);
        }
        Harness.eq("a picture loads even when ImageIO's temp-file cache folder is gone", withCacheGone, "loads");
        Harness.eq("and so does a built-in tileset", builtInWithCacheGone, "loads");
        SuperCC missing = withCustom(base, d6f.resolve("gone.png").toFile());
        Harness.eq("a missing image is an IOException that says so", customOutcome(missing),
                "IOException: Custom tileset image not found:");
        Harness.check("and its message names the missing file", customMessage(missing).contains("gone.png"));
        // A folder named where a picture should be (a hand-edited settings file): there, so not
        // "not found" -- just not a picture file (jc-33 review).
        Harness.eq("a folder where a picture should be is an IOException that says it is not a file",
                customOutcome(withCustom(d6f.toFile(), overlay)), "IOException: Custom tileset image is not a file:");
        // A name Windows cannot have (a hand-edited settings file): never looked for, so not "not
        // found" either (jc-33 review). Elsewhere < and > are legal in a name, so it runs on Windows,
        // this program's platform, only.
        if (System.getProperty("os.name", "").startsWith("Windows"))
            Harness.eq("a path that cannot be a path is an IOException that says so",
                    customOutcome(withCustom(new File(d6f.toFile(), "bad<1>.png"), overlay)), "IOException: Custom tileset image is not a valid path:");
        else
            Harness.skip("a path that cannot be a path: < and > are legal in a name off Windows");
        // A picture the loader cannot open -- unreadable, or gone after customImage looked -- is said in
        // words, naming it, not as the system's bare message (jc-33 review). Asked of the loader itself,
        // past customImage's own check, as a race would reach it.
        String unopened;
        try { graphics.TileSheet.loadTilesetImage(d6f.resolve("never-there.png").toUri().toURL(), 10, 10); unopened = "loads"; }
        catch (IOException e) { unopened = String.valueOf(e.getMessage()); }
        catch (Throwable t) { unopened = "throws " + t.getClass().getSimpleName(); }
        Harness.eq("a picture the loader cannot open is an IOException that says so", unopened.split("\n")[0], "Could not open the picture:");
        Harness.check("and that message names the picture, once (the system's reason follows without it)",
                unopened.indexOf("never-there.png") >= 0 && unopened.indexOf("never-there.png") == unopened.lastIndexOf("never-there.png"));
        SuperCC text = withCustom(notImage, overlay);
        Harness.eq("a file that is not an image is an IOException that says so", customOutcome(text),
                "IOException: Not an image SuperCC can read:");
        Harness.check("and that message names the file", customMessage(text).contains("notes.png"));
        Harness.eq("custom with no settings loaded says that, not that no images were chosen", customOutcome(null),
                "IOException: The custom tileset's images are named in succ_settings.ini, which is not loaded here.");
        // The fork's settings seed both keys "": an empty path is "none chosen" (SuccPaths), where
        // upstream's getter returned the pair and the empty paths reached ImageIO as the working folder.
        Harness.eq("custom with settings but no images chosen says so, rather than \"null\"", customOutcome(withCustom(null, null)),
                "IOException: No images have been chosen for the custom tileset.");

        // A GIF that ends (trailer 0x3B) right after its screen descriptor, named .png (ImageIO sniffs
        // content, not the name): jc-18's review found its reader throwing IndexOutOfBoundsException,
        // a RuntimeException that skipped every fallback until the loader turned them all into words.
        java.io.File gif = d6f.resolve("truncated.png").toFile();
        Files.write(gif.toPath(), new byte[] {'G', 'I', 'F', '8', '9', 'a', 10, 0, 10, 0, 0, 0, 0, 0x3B});
        SuperCC gifEmu = withCustom(gif, overlay);
        Harness.eq("a GIF with no image in it is an IOException that says so", customOutcome(gifEmu),
                "IOException: Not an image SuperCC can read:");
        Harness.check("and the GIF's message names the file", customMessage(gifEmu).contains("truncated.png"));
        // A real PNG cut short, as a half-downloaded file is: its header reads, then the image data
        // ends. ImageIO's own IIOException for it ("Error reading PNG image data") named no file --
        // jc-18 let it through, and the jc-33 review found it; it is the file's problem in words now.
        java.awt.image.BufferedImage noise = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random noiseRandom = new java.util.Random(33);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) noise.setRGB(x, y, noiseRandom.nextInt());
        java.io.ByteArrayOutputStream pngBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(noise, "png", pngBytes);
        byte[] wholePng = pngBytes.toByteArray();
        File cutShort = d6f.resolve("cut-short.png").toFile();
        Files.write(cutShort.toPath(), java.util.Arrays.copyOf(wholePng, wholePng.length * 3 / 5));
        SuperCC cutEmu = withCustom(cutShort, overlay);
        Harness.eq("a picture cut short is an IOException that says so", customOutcome(cutEmu),
                "IOException: Not an image SuperCC can read:");
        Harness.check("and the cut-short picture's message names the file", customMessage(cutEmu).contains("cut-short.png"));
        // Well-formed PNGs (real CRCs, a deflated IDAT; the review's construction) whose headers claim
        // more pixels than a tileset image may have. 50000 x 50000 makes ImageIO throw
        // IllegalArgumentException; 46000 x 46000 stays under ImageIO's own overflow check and asks for
        // about 8 GB. Both are refused from the header, before anything is decoded -- and the 46000
        // case is run only once the 50000 one has been refused that way, since a loader that decodes
        // first (upstream's, on jc-22 to jc-32) would try to allocate the 8 GB.
        java.io.File huge = d6f.resolve("huge.png").toFile();
        boolean refusesFromHeader = true;
        for (int side : new int[] {50000, 46000}) {
            if (!refusesFromHeader) {
                Harness.skip("an image whose header claims " + side + " x " + side + ": the loader did not refuse 50000 x 50000"
                             + " from its header, so it might decode this one (about 8 GB); not run");
                continue;
            }
            Files.write(huge.toPath(), pngClaiming(side, side));
            SuperCC hugeEmu = withCustom(huge, overlay);
            String hugeOutcome = customOutcome(hugeEmu);
            Harness.eq("an image whose header claims " + side + " x " + side + " is refused, unread, in words", hugeOutcome,
                    "IOException: Too large for a tileset image:");
            String hugeMsg = customMessage(hugeEmu);
            Harness.check("and the " + side + " message names the file and its size", hugeMsg.contains("huge.png") && hugeMsg.contains(side + " x " + side));
            refusesFromHeader = hugeOutcome.equals("IOException: Too large for a tileset image:");
        }

        Harness.section("6g. a saved tileset number that cannot be drawn: TileSheet.load falls back, and says why (UB-20)");
        /* MOD (Jeremy, fix layer, UB-20): what startup draws for the MS number in succ_settings.ini.
         * Upstream indexed TileSheet.values() with it, so a number out of range threw, and a tileset
         * that failed passed null images on; StartupTilesetTest measures that through a real start.
         * This checks the loader itself, at its edges: every number this version has, the first one
         * past the end and one below zero, custom with nothing chosen, and the fallback itself
         * failing. Reached by reflection, so that this file still compiles against a jar from before
         * the fix (jc-22 to jc-33), where these checks then fail as "no TileSheet.load". */
        graphics.TileSheet[] sheets = graphics.TileSheet.values();
        SuperCC noneChosen = withCustom(null, null);
        for (int n = 0; n < sheets.length; n++) {
            boolean isCustom = sheets[n] == graphics.TileSheet.CUSTOM;
            Harness.eq("tileset number " + n + (isCustom ? " (custom, none chosen)" : "") + " draws what it should",
                    tilesetChoice(noneChosen, n, graphics.TileSheet.CCEDIT_TW),
                    isCustom ? "CCEDIT_TW | The custom tileset could not be loaded: | images 2"
                           : sheets[n].name() + " | (no problem) | images 2");
        }
        Harness.eq("custom with pictures that load is drawn, with nothing to say",
                tilesetChoice(withCustom(base, overlay), graphics.TileSheet.CUSTOM.ordinal(), graphics.TileSheet.CCEDIT_TW),
                "CUSTOM | (no problem) | images 2");
        Harness.eq("tileset number " + sheets.length + ", the first past the last, falls back and says so",
                tilesetChoice(noneChosen, sheets.length, graphics.TileSheet.CCEDIT_TW),
                "CCEDIT_TW | Tileset number " + sheets.length + " in succ_settings.ini is not one this version has. | images 2");
        Harness.eq("tileset number -1 falls back and says so", tilesetChoice(noneChosen, -1, graphics.TileSheet.MSCC),
                "MSCC | Tileset number -1 in succ_settings.ini is not one this version has. | images 2");
        Harness.eq("a number out of range: the whole message, which says the choice is kept too (jc-34 review)",
                tilesetProblem(noneChosen, 99, graphics.TileSheet.CCEDIT_TW),
                "Tileset number 99 in succ_settings.ini is not one this version has.\n\nShowing "
                + graphics.TileSheet.getNames()[graphics.TileSheet.CCEDIT_TW.ordinal()]
                + " instead. Your choice is kept; pick a tileset in View > Tileset to change it.");
        String whole = tilesetProblem(withCustom(base, d6f.resolve("gone.png").toFile()), graphics.TileSheet.CUSTOM.ordinal(), graphics.TileSheet.MSCC);
        Harness.check("custom that fails: the reason names the file, the tileset shown, and that the choice is kept",
                whole.contains("gone.png") && whole.contains("Showing " + graphics.TileSheet.getNames()[graphics.TileSheet.MSCC.ordinal()] + " instead.") && whole.endsWith(
                "Your choice is kept; pick a tileset in View > Tileset to change it."));
        Harness.eq("custom asked for with custom as the fallback: the failure is thrown, not hidden",
                tilesetChoice(noneChosen, graphics.TileSheet.CUSTOM.ordinal(), graphics.TileSheet.CUSTOM),
                "throws IOException: No images have been chosen for the custom tileset.");

        // throwErrorOnce: the same words once a session; headless, throwError prints to stderr.
        java.lang.reflect.Method once;
        try { once = SuperCC.class.getMethod("throwErrorOnce", String.class); } catch (NoSuchMethodException e) { once = null; }
        String onceOutcome = "no SuperCC.throwErrorOnce";
        if (once != null) {
            java.lang.reflect.Method say = once;
            onceOutcome = captureStderr(() -> {
                try {
                    SuperCC quiet = new SuperCC(false);
                    for (String s : new String[] {"first", "second", "first", "second", "third"}) say.invoke(quiet, s);
                    say.invoke(new SuperCC(false), "first");   // once per SESSION: another emulator says it again
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }).replace("\r", "").replace("[SuperCC Error] ", "").replace("\n", " ").trim();
        }
        Harness.eq("throwErrorOnce says each message once, in order", onceOutcome, "first second third first");

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
