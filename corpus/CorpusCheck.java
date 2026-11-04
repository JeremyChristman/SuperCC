import emulator.SavestateManager;
import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Creature;
import game.CreatureList;
import game.Level;
import game.Position;
import io.DatParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Replays every stored solution in a Chip's Challenge folder and writes one line per solution
 * describing the FINAL state, so two builds can be compared line for line. Run it through
 * corpus.ps1, which compiles it against a jar and compares two runs.
 *
 *   java -cp "SuperCC.jar;<classdir>" CorpusCheck <ccDir> <outFile> [pack] [--crosscheck]
 *
 * <ccDir>\succsave\<pack>\<n>_<title>-<RULESET>.json is replayed against <ccDir>\data\<pack>.dat
 * (or <pack>.ccl -- SuperCC names the folder after the set with ANY extension removed).
 *
 * WHY THIS IS COMMITTED
 * ---------------------
 * It is the widest oracle this fork has: every solution in the collection, through the real engine.
 * The test suite compares SuperCC against TRANSCRIPTIONS of Tile World's rules, one fixture at a
 * time, and can only see the arrangements someone thought to build. On 2026-11-01 upstream's trap
 * rework (858dbd0) passed all 1,878 assertions while changing 13 of 24,204 real solutions: five
 * levels would not open at all (four of them solved), six more solved routes stopped reaching the
 * exit, and two unsolved routes ended differently -- ten solved routes lost in all -- because no
 * fixture starts a block or Chip on a wired trap. This saw it.
 * Its previous version lived in a scratch folder and was lost, as the trace harness's was.
 *
 * WHAT A LINE SAYS
 * ----------------
 * The FIRST line is "#jar <sha256> <path> harness=<sha256> java=<version> pack=<name|*>": the jar that
 * ACTUALLY ran, read from the JVM's own class source, so corpus.ps1 -Compare can refuse a pair that
 * replayed the same jar twice -- and the harness version, JDK and scope that wrote the run. The LAST
 * line is "#end <count>", so a run cut short can never pass for a whole one. Only those two lines
 * are ever anything but data, whatever a pack folder is called.
 *
 * Then pack, file, status, and for OK: ruleset, won, dead, moves replayed, tick, timer, chips left,
 * Chip's cell, facing, sliding, keys and boots held, the RNG value, CRC32s of the foreground layer,
 * the background layer (Lynx has none: "-") and the creature list (type, cell, direction, dead), and
 * the slip list (MS only). Then EVERY line carries its INPUTS: "src=<save>:<level>", CRC32s of what
 * it replayed (see src()), so a compare can tell a changed collection from a changed engine, and
 * last, "pos=N" when the save belongs to a level stored at a different position (see pick()).
 * That is everything the line names -- not literally every bit
 * of engine state: per-creature timers, the idle counter and pending clicks, for example, are not
 * in it. A divergence that fully re-converges by the end is also invisible; this checks where every
 * route ENDS, and trace.ps1 is the tool for where one goes.
 *
 * An exception is recorded as its class, message and the SuperCC method it came from -- never a
 * source line number, which moves whenever anything above it in the file is edited and would make
 * an unchanged failure "differ" between two builds.
 *
 * Statuses other than OK name what went wrong instead of throwing, so one bad file never stops the
 * run: NODAT (no level set for this folder), OPENFAIL (SuperCC will not open the set), BADNAME (not
 * <n>_<title>-<MS|LYNX>.json), BADJSON, NOLEVEL (no level in the set carries that number -- saying so
 * when some levels' records run past the end of the file and their numbers are unknown), AMBIGUOUS
 * (several do, and the title does not single one out), LOADFAIL (the level would not load --
 * reported, never replayed as whatever level was loaded before), EXC (thrown mid-replay, with the
 * move it reached), and LOADEXC / STATEEXC, two defensive catch-alls no input is known to reach.
 *
 * --crosscheck replays every solution a SECOND time through SuperCC's own Solution.load and reports
 * any whose final state differs from the harness's copy of the replay loop (see the note on that
 * loop below). The output file is unchanged by it; mismatches go to stdout, a summary line counts
 * what was checked and skipped, and any mismatch makes the exit code 3. It doubles the run time.
 *
 * Read-only. It writes the output file and nothing else; the headless SuperCC(false) constructor
 * never builds a SuccPaths, so no settings file is read or written. Exit 2 if the output could not
 * be written in full -- a truncated file must never be mistaken for a run -- or if the [pack] given
 * names no folder, which would otherwise be an empty run that looks clean.
 */
public class CorpusCheck {

    private static final Pattern NAME = Pattern.compile("^(\\d+)_(.*)-(MS|LYNX)\\.json$",
                                                        Pattern.CASE_INSENSITIVE);
    private static final Charset CP1252 = Charset.forName("windows-1252");   // as DatParser.readText

    private static boolean crossCheckEnabled = false;
    private static int crossChecked = 0, crossSkipped = 0, crossDiffs = 0, crossRenumbered = 0;
    private static String[] ccPaths = {};          // the CC folder, when absolute, for describe() to mask
    private static String runDir = "";             // the run's temp folder, likewise

    public static void main(String[] argv) throws Exception {
        List<String> args = new ArrayList<>();
        for (String a : argv) {
            if (a.equals("--crosscheck")) crossCheckEnabled = true;
            else args.add(a);
        }
        if (args.size() < 2) {
            System.err.println("usage: CorpusCheck <ccDir> <outFile> [pack] [--crosscheck]");
            System.exit(2);
        }
        File cc = new File(args.get(0));
        /* Only an ABSOLUTE folder is masked: a relative one (".", "cc") is a substring of ordinary
         * text and would mangle every message -- and a message naming a relative path is the same
         * on every PC anyway. corpus.ps1 always passes an absolute one. */
        ccPaths = cc.isAbsolute() ? new String[]{cc.getPath()} : new String[0];
        runDir = System.getProperty("corpus.tempdir", "");      // corpus.ps1's temp folder, if given
        File succsave = new File(cc, "succsave");
        File data = new File(cc, "data");
        String outFile = args.get(1);
        String only = args.size() > 2 ? args.get(2) : null;
        if (!succsave.isDirectory() || !data.isDirectory()) {
            System.err.println("no succsave\\ and data\\ under " + cc);
            System.exit(2);
        }

        File[] packs = succsave.listFiles(File::isDirectory);
        if (packs == null) packs = new File[0];
        /* Sorted, so two runs of the same collection produce the same file in the same order and
         * can be compared byte for byte. Case-insensitive first, then exact, so the order is total
         * even if two folder names differ only in case. */
        Arrays.sort(packs, Comparator.comparing((File f) -> f.getName().toLowerCase(Locale.ROOT))
                                     .thenComparing(File::getName));

        /* A -Pack that names no folder would write an empty run and exit 0 -- a misspelling that
         * reads as a clean result. Refused before anything is written. */
        if (only != null && Arrays.stream(packs).noneMatch(p -> p.getName().equalsIgnoreCase(only))) {
            System.err.println("no pack folder named " + only + " under " + succsave);
            System.exit(2);
        }

        int packCount = 0, solutions = 0;
        boolean writeFailed;
        /* Written to "<out>.partial" and moved into place only once complete: opening <out> itself
         * would truncate it at once, so a run that fails or is killed would destroy a baseline kept
         * at that path -- one that may not be reproducible once its jar is gone. */
        Path finalOut = Paths.get(outFile).toAbsolutePath();
        Path partial = Paths.get(finalOut + ".partial");
        String header = jarHeader() + "\tpack=" + (only == null ? "*" : only);
        try (PrintStream out = new PrintStream(new FileOutputStream(partial.toFile()), false,
                                               StandardCharsets.UTF_8)) {
            out.println(header);
            for (File pack : packs) {
                if (only != null && !pack.getName().equalsIgnoreCase(only)) continue;
                File[] files = pack.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
                if (files == null) files = new File[0];
                Arrays.sort(files, Comparator.comparing(File::getName));

                packCount++;
                System.out.println("[" + packCount + "] " + pack.getName() + "  (" + (solutions + files.length) + " so far)");

                File dat = new File(data, pack.getName() + ".dat");
                File ccl = new File(data, pack.getName() + ".ccl");
                String openFailure = null;
                if (dat.isFile() && ccl.isFile()) {
                    openFailure = "AMBIGUOUS\tboth " + dat.getName() + " and " + ccl.getName()
                                + " exist, and SuperCC files both sets' solutions in this one folder";
                } else if (!dat.isFile() && !ccl.isFile()) {
                    for (File f : files)       // through inputs() like every other line, with no set
                        out.println(pack.getName() + "\t" + f.getName() + "\tNODAT"
                                    + inputs(saveCrc(f), null, "-", locate(null, f)));
                    solutions += files.length;
                    continue;
                }
                File set = dat.isFile() ? dat : ccl;

                /* The INPUTS first, from the file's bytes alone, before the jar under test touches the
                 * set: the Index, and through it every save's fingerprint, must come out the same
                 * whatever that jar then does -- including refusing the set (see levelCrc()). */
                Index index = null;
                String setCrc = "-";
                if (openFailure == null) {
                    try {
                        byte[] bytes = Files.readAllBytes(set.toPath());
                        setCrc = crc(bytes);
                        index = new Index(bytes);
                    } catch (Throwable t) {
                        openFailure = describe(t);     // unreadable as a file: no jar can open it
                    }
                }
                if (openFailure == null) {
                    try {
                        new DatParser(set);            // will SuperCC's parser read it at all?
                    } catch (Throwable t) {
                        openFailure = describe(t);     // the parser's own reason
                    }
                }
                SuperCC emulator = null;
                if (openFailure == null) {
                    try {
                        // Inside the handler too: a constructor that throws (a changed engine jar)
                        // is that pack's OPENFAIL, never the end of the whole run.
                        emulator = new SuperCC(false);
                        emulator.openLevelset(set);
                        /* openLevelset SWALLOWS a read failure -- it reports it and returns, leaving
                         * no level set loaded -- so its return proves nothing, and the next call
                         * would throw outside any handler and abort the whole run. lastLevelNumber()
                         * reads the set itself, so it throws here, inside the handler, instead.
                         * (Deliberately NOT a getLevel() check: a set whose level 1 fails to load is
                         * still open, and its other levels deserve their own lines.) */
                        emulator.lastLevelNumber();
                    } catch (Throwable t) {
                        openFailure = describe(t);
                    }
                }
                if (openFailure != null) {
                    for (File f : files)
                        out.println(pack.getName() + "\t" + f.getName() + "\tOPENFAIL\t" + openFailure
                                    + inputs(saveCrc(f), index, setCrc, locate(index, f)));
                    solutions += files.length;
                    continue;
                }
                for (File f : files) {
                    out.println(pack.getName() + "\t" + f.getName() + "\t" + replayOne(emulator, index, setCrc, f));
                    solutions++;
                }
                /* The fingerprint was taken from ONE read of the set, but the engine re-reads the file
                 * at every load. The collection lives in Dropbox: if the set changed mid-pack, the
                 * lines just written describe bytes that were not all replayed, and a compare would
                 * blame the difference on the engine. Stopped -- with no #end, so it is refused. */
                String setNow;
                try {
                    setNow = crc(Files.readAllBytes(set.toPath()));
                } catch (Exception e) {        // an Error is not "unreadable": it propagates
                    setNow = "unreadable";
                }
                if (!setNow.equals(setCrc)) {
                    out.flush();
                    System.err.println("ERROR: " + set + " changed on disk while its saves were being replayed."
                                       + " Re-run once the collection is still.");
                    System.exit(2);
                }
            }
            /* The LAST line says the run finished, and how many lines it wrote. A run killed part-way
             * (low memory, Ctrl-C, an engine that hangs) leaves a file that looks valid line by line;
             * compared, every save it never reached read as "only in baseline" -- the collection's
             * difference, out of the engine headline -- which is exactly backwards. corpus.ps1 refuses
             * a run without this line, or whose count does not match. */
            out.println("#end\t" + solutions);
            out.flush();
            writeFailed = out.checkError();     // PrintStream swallows IOExceptions; ask
        }
        System.out.println("DONE packs=" + packCount + " solutions=" + solutions);
        if (writeFailed) {
            System.out.println("ERROR: the output file could not be written in full: " + partial
                               + " (left in place; " + finalOut + " is untouched)");
            System.exit(2);
        }
        if (crossCheckEnabled) {
            System.out.println("CROSSCHECK checked=" + crossChecked + " skipped=" + crossSkipped
                               + " differ=" + crossDiffs);
            if (crossRenumbered > 0)
                System.out.println("NOTE: " + crossRenumbered + " save(s) belong to a level whose number is not its"
                                   + " position. SuperCC's own Solution.load reloads by that NUMBER as a position,"
                                   + " so opening these in SuperCC replays them on the WRONG level -- a SuperCC"
                                   + " defect, not a harness one. They are skipped above, not compared.");
            /* A run whose replay loop was just shown NOT to match SuperCC's is not a result: it is
             * left as .partial and never moved into place, so it can never become a baseline that a
             * later compare reads without a word about it. */
            if (crossDiffs > 0) {
                System.out.println("ERROR: the replay does not match SuperCC's own; the output is left at " + partial
                                   + " and " + finalOut + " is untouched");
                System.exit(3);
            }
        }
        // A run that cannot say which jar it replayed could never be compared anyway: it must not
        // replace what is at <out> -- possibly a baseline whose jar is gone -- on its way to a refusal.
        if (header.startsWith("#jar\t-\t")) {
            System.out.println("ERROR: could not identify the jar this run replayed; the output is left at " + partial
                               + " and " + finalOut + " is untouched");
            System.exit(2);
        }
        Files.move(partial, finalOut, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Every level in a set, by the number stored in its record, built once per set so finding the
     * level a save belongs to is a lookup -- never a reload of the whole set per save.
     *
     * SuperCC names a solution file after the level's OWN number -- SuccPaths.getJSONPath is handed
     * level.getLevelNumber(), the word stored at the start of the level's record -- but loads levels
     * by POSITION. The two agree in every set in the collection measured so far, but the format
     * does not force them to: a level can carry any number, and several levels can carry the same
     * one. Replaying a save by position where they differ tests the moves on the wrong level and
     * prints an ordinary-looking result under BOTH jars, so a compare calls a solution that was never
     * tested IDENTICAL.
     *
     * Read straight from the file's BYTES, never through the engine. Which level a save belongs to
     * is a fact about the file, and it must come out the same under every jar: built with
     * DatParser.parseLevel, the index ran the level constructor of the jar under test, in the SET's
     * ruleset -- so a candidate whose MSLevel constructor throws (858dbd0's exact failure) erased
     * those levels' numbers, and every save on them, LYNX ones included, read LOADFAIL without ever
     * being loaded. Now the engine is consulted only by loadPosition, in the save's own ruleset.
     *
     * The layout is the CC1 one DatParser reads: a 4-byte signature, a word level count, then per
     * level a word byte count and the record -- number, time, chips and map-detail words, two RLE
     * layers (read the way DatParser reads them: tiles until 1024, the layer's own byte count
     * ignored), and a word-counted list of optional fields, of which field 3 is the title. Each
     * field is stepped over by EXACTLY the bytes DatParser.parseLevel reads for it (consumed()) --
     * not by its stated length, which DatParser does not always honor: a time or chips field is two
     * bytes whatever it says, an unknown type reads nothing, and connection and monster lists read
     * whole records only. So even in a malformed set the title found here is the one SuperCC finds.
     */
    static final class Index {
        final Map<Integer, List<Integer>> byNumber = new HashMap<>();  // number -> positions
        final Map<Integer, String> titleAt = new HashMap<>();          // position -> filename title
        /* position -> "<signature>/<CRC32 of its record>": the set's signature belongs to every
         * level's fingerprint because it alone decides whether SuperCC opens the set at all (and
         * its default ruleset) -- re-signing a pack, records untouched, is a change in the
         * collection, and must not read as the engine's when the saves stop opening. */
        final Map<Integer, String> crcAt = new HashMap<>();
        int unreadable = 0;                                            // records whose number is past EOF

        Index(byte[] dat) {
            int b0 = u8(dat, 0), b1 = u8(dat, 1), b2 = u8(dat, 2), b3 = u8(dat, 3);
            String signature = b3 < 0 ? "-" : String.format("%08x", b0 | b1 << 8 | b2 << 16 | (long) b3 << 24);
            // The level count as DatParser reads it: a byte past the end reads as 0xFF there, so a set
            // cut off inside its count claims up to 65535 levels -- every one of them unreadable here.
            int lo = u8(dat, 4), hi = u8(dat, 5);
            int levels = (lo < 0 ? 0xFF : lo) | (hi < 0 ? 0xFF : hi) << 8;
            long start = 6;
            for (int p = 1; p <= levels; p++) {
                int size = word(dat, start);
                int number = word(dat, start + 2);
                if (size < 0 || number < 0) { unreadable += levels - p + 1; break; }
                byNumber.computeIfAbsent(number, k -> new ArrayList<>()).add(p);
                long[] parsedTo = {start + 2};
                titleAt.put(p, fileTitle(title(dat, start + 2, parsedTo)));
                /* The fingerprint covers every byte SuperCC's parser reads for this level: its stated
                 * size, or further where a malformed record makes DatParser read on into the next one
                 * (it reads layers and fields until done, whatever the size says) -- otherwise a change
                 * to those borrowed bytes would alter the level but not its fingerprint. */
                int from = (int) (start + 2);
                int to = (int) Math.min(Math.max(start + 2 + size, parsedTo[0]), dat.length);
                CRC32 record = new CRC32();
                record.update(dat, from, to - from);
                crcAt.put(p, signature + "/" + String.format("%08x", record.getValue()));
                start += 2 + size;
            }
        }


        /**
         * The record's title, or null if it has none or it cannot be read. The LAST title field
         * wins, as in DatParser.parseLevel, which overwrites the title at each one it meets.
         * parsedTo[0] is set to where the walk stopped: the end of what DatParser reads for this level.
         */
        static String title(byte[] dat, long record, long[] parsedTo) {
            long i = record + 8;                                    // number, time, chips, map detail
            for (int layer = 0; layer < 2; layer++) {
                i += 2;                                             // its byte count, unused (see above)
                for (int tiles = 0; tiles < 1024; ) {
                    int b = u8(dat, i++);
                    if (b < 0) return stop(parsedTo, i, null);
                    if (b == 0xFF) {
                        int copies = u8(dat, i);
                        if (copies < 0) return stop(parsedTo, i, null);
                        tiles += copies;
                        i += 2;                                     // the count and the tile
                    } else {
                        tiles++;
                    }
                }
            }
            int fieldsLeft = word(dat, i);
            i += 2;
            String title = null;
            while (fieldsLeft > 0) {
                int type = u8(dat, i), length = u8(dat, i + 1);
                // Past the end, DatParser reads 0xFF bytes -- type 255, never a title -- so the title
                // it already has stands. So does this one.
                if (type < 0 || length < 0) return stop(parsedTo, i, title);
                i += 2;
                if (type == 3) {                                    // as DatParser.readText: length-1 bytes, then a NUL
                    if (length == 0) return stop(parsedTo, i, null);    // DatParser throws: the level cannot load
                    // A title running past the end keeps the bytes that are there; DatParser's are the
                    // same plus zeros, which the filename form strips anyway.
                    int n = (int) Math.max(0, Math.min(length - 1, dat.length - i));
                    title = new String(dat, (int) i, n, CP1252);
                }
                else if (length == 0 && type >= 6 && type <= 9) return stop(parsedTo, i, null);  // DatParser throws too
                i += consumed(type, length);
                fieldsLeft -= 2 + length;                           // the count, though, drops by the STATED length
            }
            return stop(parsedTo, i, title);
        }

        private static String stop(long[] parsedTo, long at, String title) {
            parsedTo[0] = at;
            return title;
        }

        /** The bytes DatParser.parseLevel reads for an optional field of this type and stated length. */
        static int consumed(int type, int length) {
            switch (type) {
                case 1: case 2:                    return 2;                    // readWord, whatever the length
                case 3: case 6: case 7: case 8: case 9: return length;          // text: length-1 bytes and a NUL
                case 4:                            return length / 10 * 10;     // trap links, 10 bytes each
                case 5:                            return length / 8 * 8;       // clone links, 8 bytes each
                case 10:                           return length / 2 * 2;       // monster positions, 2 bytes each
                default:                           return 0;                    // DatParser ignores the field
            }
        }

        static int u8(byte[] dat, long i) { return i >= 0 && i < dat.length ? dat[(int) i] & 0xFF : -1; }

        static int word(byte[] dat, long i) {
            int lo = u8(dat, i), hi = u8(dat, i + 1);
            return lo < 0 || hi < 0 ? -1 : lo | hi << 8;
        }
    }

    /** A title as SuperCC writes it into a filename (SuperCC.getJSONPath): only [A-Za-z0-9 ] kept. */
    private static String fileTitle(String title) {
        return title == null ? "" : title.replaceAll("[^a-zA-Z0-9 ]", "");
    }

    /**
     * "#jar <sha256> <path> harness=<sha256>": the jar the JVM actually loaded SuperCC from, taken
     * from the class's own code source rather than from whatever the calling script believes it
     * passed, because the failure this guards against is precisely a script running a different jar
     * than intended -- and the version of THIS harness that wrote the run (corpus.ps1 passes the
     * sha256 of the source it just compiled, line endings normalized, as -Dcorpus.harness). Two runs
     * written by different harness versions differ wherever the harness changed what it writes, and
     * a compare would blame every such line on the engine; corpus.ps1 refuses that pair.
     */
    private static String jarHeader() {
        // ... and the JVM that ran it: exception messages are the JDK's own wording, so two runs on
        // different JDKs may differ in those lines alone (corpus.ps1 warns; end states do not depend
        // on it -- the engine is integer arithmetic).
        String harness = "\tharness=" + System.getProperty("corpus.harness", "-")
                       + "\tjava=" + System.getProperty("java.version", "-");
        try {
            Path jar = Paths.get(SuperCC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar)) return "#jar\t-\t" + jar + harness;
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(jar)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) sha.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) hex.append(String.format("%02X", b));
            // The hash is of what was LOADED -- corpus.ps1's private copy -- but the path shown is
            // the jar the user named (-Dcorpus.jarpath), so a kept baseline still says which release
            // it replayed; the copy's temp folder is gone by the time anyone reads this.
            return "#jar\t" + hex + "\t" + System.getProperty("corpus.jarpath", jar.toString()) + harness;
        } catch (Throwable t) {
            return "#jar\t-\t" + t.getClass().getSimpleName() + harness;
        }
    }

    /**
     * Loads one position; null on success, else the status line. loadLevel swallows its own
     * exceptions, so failure has to be detected by identity:
     *  - the level object unchanged: parsing failed and the PREVIOUS level is still loaded, so
     *    anything reported now would describe the wrong level;
     *  - the savestate manager unchanged: the level parsed but SavestateManager's constructor threw
     *    (a level with no title does this -- MO3 #12 and #14). Before jc-17 that left the new level
     *    paired with the old level's savestates; since jc-17 loadLevel assigns both or neither, so
     *    on a jc-17+ jar this shows up as the level unchanged instead. Both tests stay, because the
     *    harness also replays older jars. The GUI reports either as "Could not load level".
     */
    private static String loadPosition(SuperCC emulator, int position, Solution solution) {
        Level levelBefore = emulator.getLevel();
        SavestateManager savestatesBefore = emulator.getSavestates();
        try {
            emulator.loadLevel(position, solution.rngSeed, solution.step, false,
                               solution.ruleset, solution.initialSlide);
        } catch (Throwable t) {
            return "LOADEXC\t" + solution.ruleset + "\t" + describe(t);
        }
        Level level = emulator.getLevel();
        if (level == null || level == levelBefore || emulator.getSavestates() == savestatesBefore)
            return "LOADFAIL\t" + solution.ruleset;
        return null;
    }

    /** Where a save's level is in its set: a position, or the problem (NOLEVEL / AMBIGUOUS) and why. */
    private static final class Pick {
        final int position;
        final String problem, detail;

        Pick(int position) { this.position = position; this.problem = null; this.detail = null; }

        Pick(String problem, String detail) { this.position = -1; this.problem = problem; this.detail = detail; }

        String status(Solution s) { return problem + "\t" + s.ruleset + (detail == null ? "" : "\t" + detail); }
    }

    /**
     * Finds the POSITION of the level a save belongs to, from its filename's number and title alone.
     * Reads only the Index; nothing is loaded, and no jar is consulted.
     *
     * The level is the one whose OWN number is the save's (see Index). When several levels carry
     * that number, the title SuperCC also wrote into the filename picks between them; if it does not
     * pick exactly one, the save is AMBIGUOUS rather than guessed at. The title is ONLY a tie-break:
     * saves are routinely copied between packs under their source's name, so requiring the title to
     * match everywhere would flag hundreds of correct saves. A set with UNREADABLE records counts as
     * a tie too, even with one readable candidate: a record whose number cannot be read might carry
     * this same number, so there the title must confirm the one that can.
     */
    private static Pick pick(Index index, int number, String fileTitle) {
        List<Integer> candidates = index.byNumber.get(number);
        if (candidates == null || candidates.isEmpty()) {
            /* Where some records end past the end of the file, their numbers are unknown, and the
             * save may well belong to one of them -- said so, rather than guessed at by taking the
             * save's number as a position, which is the very assumption the Index exists to avoid.
             * (SuperCC's own reader returns 0xFF for every byte past the end, so it "sees" a number
             * like 65535 there -- but the title lies past the end too, and a level without one can
             * neither load nor be saved in SuperCC. So any save for such a record was made before
             * the set was cut short, under the number it had then, which no one can now read.) */
            if (index.unreadable > 0)
                return new Pick("NOLEVEL", index.unreadable
                                + " record(s) run past the end of the set, so their numbers are unknown");
            return new Pick("NOLEVEL", null);
        }
        if (candidates.size() == 1 && index.unreadable == 0)
            return new Pick(candidates.get(0));
        List<Integer> titled = new ArrayList<>();
        for (int p : candidates)
            if (index.titleAt.get(p).equalsIgnoreCase(fileTitle)) titled.add(p);
        if (titled.size() == 1)
            return new Pick(titled.get(0));
        return new Pick("AMBIGUOUS", candidates.size() + " level(s) carry number " + number
                        + (index.unreadable > 0 ? " and " + index.unreadable + " record(s) cannot be read" : "")
                        + ", and the title picks " + titled.size());
    }

    /**
     * "<TAB>src=<save>:<level>": CRC32s of the solution file's bytes and of the level its FILENAME
     * points at (levelCrc()), so a compare can tell a change in the COLLECTION -- a re-recorded save,
     * an updated pack -- from a change in the ENGINE. Without it, both read as the engine.
     *
     * It is the LAST thing on the line but for "pos=N", which is input identity too (where in the
     * set that level sits) and so comes AFTER it: corpus.ps1 treats everything from "src=" on as the
     * inputs, and a level that moved because another was inserted before it is the collection's
     * doing, not a different end state.
     */
    private static String src(String saveCrc, String levelCrc) {
        return "\tsrc=" + saveCrc + ":" + (levelCrc == null ? "-" : levelCrc);
    }

    /** What a save's FILENAME says: whether it parses, its level number, and where that level is. */
    private static final class Located {
        final boolean named, numbered;
        final int number;
        final Pick where;       // null unless numbered (and the set's Index was read)

        Located(boolean named, boolean numbered, int number, Pick where) {
            this.named = named; this.numbered = numbered; this.number = number; this.where = where;
        }
    }

    /**
     * Finds a save's level ONCE, from its filename and the Index alone. Every line's inputs (src=,
     * pos=) and the level replayed both come from this one result -- on every path, OPENFAIL
     * included -- so they cannot disagree, and no jar can move them.
     */
    private static Located locate(Index index, File save) {
        Matcher m = NAME.matcher(save.getName());
        if (!m.matches()) return new Located(false, false, -1, null);
        int number;
        try {
            number = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return new Located(true, false, -1, null);      // more digits than any level number can hold
        }
        return new Located(true, true, number, index == null ? null : pick(index, number, m.group(2)));
    }

    /**
     * The line's input identity: src=, then "pos=N" when the save's level sits at a position other
     * than its number.
     */
    private static String inputs(String saveCrc, Index index, String setCrc, Located at) {
        Pick where = at.where;
        String pos = where != null && where.problem == null && where.position != at.number
                   ? "\tpos=" + where.position : "";
        return src(saveCrc, levelCrc(index, setCrc, where)) + pos;
    }

    private static String saveCrc(File save) {
        try {
            return crc(Files.readAllBytes(save.toPath()));
        } catch (Exception e) {      // NOT Throwable: a JVM in trouble must end the run (see describe())
            return "-";
        }
    }

    /**
     * The CRC32 of the level a save's FILENAME points at: its record when the Index picks exactly
     * one, else the whole set ("-" with no readable set). Worked out from the inputs ALONE, never
     * from what the jar did with the save: a jar that stops parsing it (BADJSON), loading it
     * (LOADFAIL) or opening its set (OPENFAIL) must leave its fingerprint exactly as it was, or the
     * compare would file that engine regression as INPUT CHANGED -- the collection's fault, out of
     * "solved routes lost". That was a real defect here, caught in review, and the self-test now
     * checks that the level half of every save's src= is the same in two runs over one set.
     */
    private static String levelCrc(Index index, String setCrc, Pick where) {
        return where != null && where.problem == null ? index.crcAt.get(where.position) : setCrc;
    }


    private static String crc(byte[] bytes) {
        CRC32 c = new CRC32();
        c.update(bytes);
        return String.format("%08x", c.getValue());     // fixed width: a fingerprint field
    }

    private static String replayOne(SuperCC emulator, Index index, String setCrc, File f) {
        byte[] saveBytes = null;
        Throwable readFailure = null;
        try {
            saveBytes = Files.readAllBytes(f.toPath());   // read ONCE: fingerprinted, then parsed
        } catch (Throwable t) {
            readFailure = t;
        }
        Located at = locate(index, f);
        String tail = inputs(saveBytes == null ? "-" : crc(saveBytes), index, setCrc, at);

        if (!at.named) return "BADNAME" + tail;
        Solution solution;
        try {
            if (readFailure != null) throw readFailure;
            /* Decoded EXACTLY as SuperCC's own Open does (MenuBar): read as UTF-8 to find the
             * "Encode" field, and only a file that says "UTF-8" is taken as UTF-8 -- any other is
             * ISO-8859-1, one byte per character. That is how older saves were written, and their
             * click moves are single bytes 0xAF-0xFF: read as UTF-8, most would not decode at all,
             * and a pair like C3 BF would decode as ONE different move, replayed without a word. */
            String text = new String(saveBytes, StandardCharsets.UTF_8);
            // Cast as MenuBar casts it: an "Encode" that is not a string throws there, so it does here.
            String encoding = (String) ((JSONObject) new JSONParser().parse(text)).get(Solution.ENCODE);
            solution = Solution.fromJSON("UTF-8".equals(encoding) ? text
                                         : new String(saveBytes, StandardCharsets.ISO_8859_1));
        } catch (Throwable t) {
            return "BADJSON\t" + describe(t) + tail;
        }
        if (!at.numbered) return "NOLEVEL\t" + solution.ruleset + tail;
        if (at.where.problem != null) return at.where.status(solution) + tail;
        int position = at.where.position;
        String failure = loadPosition(emulator, position, solution);
        if (failure != null) return failure + tail;
        Level level = emulator.getLevel();

        /* This loop mirrors emulator.Solution.tickBasicMoves exactly (as trace/TraceLevel.java does),
         * because that method runs a whole solution in one call and cannot be observed from outside.
         * The click and multi-tick handling must stay in step with it, or this stops measuring what a
         * real replay does -- and --crosscheck is what proves it still does. */
        int move = 0;
        char[] moves = solution.basicMoves;
        String line;
        try {
            for (move = 0; move < moves.length; move++) {
                char c = moves[move];
                if (c == SuperCC.CHIP_RELATIVE_CLICK) {
                    int x = moves[++move] - 9;
                    int y = moves[++move] - 9;
                    if (x == 0 && y == 0) {
                        c = SuperCC.WAIT;
                    } else {
                        Position chipPosition = level.getChip().getPosition();
                        Position clickPosition = chipPosition.add(x, y);
                        level.setClick(clickPosition.getIndex());
                        c = clickPosition.clickChar(chipPosition);
                    }
                }
                boolean tickedMulti = emulator.tick(c, TickFlags.PRELOADING);
                if (tickedMulti) move += level.ticksPerMove() - 1;
                /* The fatal move WAS replayed, so it is counted before leaving -- otherwise every
                 * route that ends on its last move reads one short (move=880/881), and MS marks Chip
                 * dead on reaching the exit, so that was nearly every solved route. */
                if (level.getChip().isDead()) { move++; break; }
            }
            line = finalLine(level, solution, Math.min(move, moves.length));
        } catch (Throwable t) {
            line = "EXC\t" + solution.ruleset + "\tmove=" + Math.min(move, moves.length) + "/" + moves.length
                 + "\t" + describe(t);
        }
        if (crossCheckEnabled) crossCheck(emulator, level, position, solution, line, f);
        return line + tail;
    }

    /** OK plus the state, or STATEEXC if describing the state itself throws. */
    private static String finalLine(Level level, Solution solution, int move) {
        try {
            return "OK\t" + state(level, solution, move);
        } catch (Throwable t) {
            return "STATEEXC\t" + solution.ruleset + "\t" + describe(t);
        }
    }

    /**
     * Replays the same solution through Solution.load -- the path SuperCC itself uses -- and compares
     * its final state with the harness's. The loop above is a COPY of Solution.tickBasicMoves, kept
     * in step by hand; this is what turns "kept in step" from a comment into a measurement. Run it
     * after anything touches Solution.java (an upstream merge, above all).
     *
     * Every replayed save is counted as checked or skipped -- including ones whose replay THREW,
     * since a copy that throws where the original does not is exactly the drift this exists for.
     */
    private static void crossCheck(SuperCC emulator, Level mirrored, int position, Solution solution,
                                   String mirroredLine, File f) {
        /* Solution.load reloads by the CURRENT level's own number, which it treats as a position --
         * a SuperCC defect: where that number is not the level's position (a save resolved through
         * pos=) it loads a different level, in the GUI too. Those cannot be compared, so they are
         * skipped, and counted apart so the run SAYS so (the NOTE in main). (No ruleset check is
         * needed: loadPosition just loaded this level IN the solution's ruleset, so Solution.load
         * never has a ruleset question to ask; were it ever to decline, the identity check below
         * counts that as skipped.) */
        if (mirrored.getLevelNumber() != position) {
            crossSkipped++;
            crossRenumbered++;
            return;
        }
        String real;
        SavestateManager savestatesBefore = emulator.getSavestates();
        try {
            solution.load(emulator);
            Level reloaded = emulator.getLevel();
            // Load declined or failed: by the same TWO identities loadPosition uses -- a level that
            // parsed but whose SavestateManager threw is a failed load too, not a replay to compare.
            if (reloaded == mirrored || emulator.getSavestates() == savestatesBefore) { crossSkipped++; return; }
            real = finalLine(reloaded, solution, solution.basicMoves.length);
        } catch (Throwable t) {
            real = "EXC\t" + solution.ruleset + "\tmove=?\t" + describe(t);
        }
        String harness = comparable(mirroredLine), superCC = comparable(real);
        if (harness.equals(superCC)) {
            crossChecked++;
        } else {
            crossDiffs++;
            System.out.println("MIRRORDIFF\t" + f.getParentFile().getName() + "\t" + f.getName());
            System.out.println("   harness  " + harness);
            System.out.println("   SuperCC  " + superCC);
        }
    }

    /**
     * A line reduced to what both replay paths can report identically. The move counter is the
     * harness's own bookkeeping (Solution.load exposes none), and for an exception the reporting
     * method differs by construction -- the same exception is raised in this class on one path and
     * in Solution.tickBasicMoves on the other -- so an exception compares by class and message.
     */
    private static String comparable(String line) {
        String s = line.replaceFirst("\tmove=[^\t]*", "");
        if (s.startsWith("EXC\t") || s.startsWith("STATEEXC\t")) s = s.substring(0, s.lastIndexOf('\t'));
        return s;
    }

    private static String state(Level level, Solution solution, int move) {
        Creature chip = level.getChip();
        CRC32 fg = new CRC32();
        fg.update(level.getLayerFG().getBytes());
        String bgHex;
        try {                       // Lynx has no background layer and throws rather than return one
            CRC32 bg = new CRC32();
            bg.update(level.getLayerBG().getBytes());
            bgHex = Long.toHexString(bg.getValue());
        } catch (UnsupportedOperationException e) {
            bgHex = "-";
        }
        CreatureList list = level.getMonsterList();
        CRC32 mon = new CRC32();
        for (int i = 0; i < list.size(); i++) {
            Creature c = list.get(i);
            if (c == null) { mon.update(0xFF); continue; }
            mon.update(creature(c).getBytes(StandardCharsets.UTF_8));
        }
        String slip;
        try {                       // MS only: Lynx has no slip list and throws rather than return one
            CRC32 s = new CRC32();
            int n = 0;
            for (Creature c : level.getSlipList()) {
                if (c == null) continue;
                s.update(creature(c).getBytes(StandardCharsets.UTF_8));
                n++;
            }
            slip = n + ":" + Long.toHexString(s.getValue());
        } catch (UnsupportedOperationException e) {
            // ONLY that: anything else an engine throws here is a crash, and must surface as
            // STATEEXC with its class and method, not pass for a harmless "slip=-".
            slip = "-";
        }
        return solution.ruleset
             + "\twon=" + (level.isCompleted() ? 1 : 0)
             + "\tdead=" + (chip.isDead() ? 1 : 0)
             + "\tmove=" + move + "/" + solution.basicMoves.length
             + "\ttick=" + level.getTickNumber()
             + "\ttimer=" + level.getTimer()
             + "\tchips=" + level.getChipsLeft()
             + "\tchip=" + chip.getPosition().getIndex()
             + "\tface=" + chip.getDirection()
             + "\tslide=" + (chip.isSliding() ? 1 : 0)
             + "\tkeys=" + join(level.getKeys())
             + "\tboots=" + join(level.getBoots())
             + "\trng=" + level.getRNG().getCurrentValue()
             + "\tfg=" + Long.toHexString(fg.getValue())
             + "\tbg=" + bgHex
             + "\tmon=" + list.size() + ":" + Long.toHexString(mon.getValue())
             + "\tslip=" + slip;
    }

    private static String creature(Creature c) {
        return c.getCreatureType() + "@" + c.getPosition().getIndex() + ":" + c.getDirection()
             + (c.isDead() ? "x" : "") + ";";
    }

    private static String join(short[] a) {
        if (a == null) return "-";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) s.append(i == 0 ? "" : ",").append(a[i]);
        return s.toString();
    }

    private static String join(byte[] a) {
        if (a == null) return "-";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) s.append(i == 0 ? "" : ",").append(a[i]);
        return s.toString();
    }

    /**
     * Exception class, message, and the SuperCC method it came from, on one tab-safe line. NO line
     * number: line numbers move whenever anything above them is edited, which would make an unchanged
     * failure "differ" between two builds -- precisely in the releases that touch the engine.
     *
     * For the same reason a helpful-NPE message's local-variable SLOT is dropped: the spliced classes
     * are compiled without -g (build-config.ps1), so a null local is named "<local4>", and that slot
     * number moves when the method is edited above it. "<local>" keeps the message, minus the noise.
     * So are the two things a message can carry that differ between runs with no engine change at
     * all: the Chip's Challenge folder's own path (an IOException names its file, and the folder is
     * C:\Users\Jeremy\... on one PC and C:\Users\jerem\... on the other), shown as "<CC>", the run's
     * own temp folder (a fresh GUID every run, holding the jar copy), shown as "<RUN>", and an
     * identity hash (Object.toString's "@1b6d3586"), shown as "@<hash>".
     *
     * A JVM in trouble is NOT a result, so it is never described: running out of memory, or the VM's
     * own internal failure, depends on the heap and the machine rather than on the input, and written
     * as an EXC line it would compare as the engine's. It is rethrown, so the run dies with no #end
     * and is refused. (A StackOverflowError IS kept as a result -- an engine that recurses without
     * end on one save does so on every run -- but by its class alone; see below.)
     */
    private static String describe(Throwable t) {
        if (t instanceof OutOfMemoryError || t instanceof InternalError || t instanceof UnknownError)
            throw (Error) t;
        /* A stack overflow is recorded by its class ALONE: the frame it strikes in depends on the
         * thread's stack size and on which frames the JIT has compiled (their sizes differ), so its
         * method -- and its empty message -- would make two runs of one jar disagree. */
        if (t instanceof StackOverflowError) return "StackOverflowError\t-\t-";
        String where = "";
        for (StackTraceElement e : t.getStackTrace()) {
            String cls = e.getClassName();
            if (cls.startsWith("game.") || cls.startsWith("io.") || cls.startsWith("emulator.")) {
                where = cls + "." + e.getMethodName();
                break;
            }
        }
        String msg = String.valueOf(t.getMessage());
        for (String cc : ccPaths) if (!cc.isEmpty()) msg = msg.replace(cc, "<CC>");
        if (!runDir.isEmpty()) msg = msg.replace(runDir, "<RUN>");
        msg = msg.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
                 .replaceAll("<local\\d+>", "<local>")
                 .replaceAll("(?<=[\\w$])@[0-9a-f]{1,8}\\b", "@<hash>");
        if (msg.length() > 160) msg = msg.substring(0, 160);
        return t.getClass().getSimpleName() + "\t" + msg + "\t" + where;
    }
}
