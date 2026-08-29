import game.Tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a valid CC1 .dat file in memory, so engine tests have a fixture without shipping one.
 *
 * WHY THIS EXISTS
 * ---------------
 * CC1 level sets are third-party content and cannot be committed here (see
 * docs\adr\0007-synthesize-dat-fixtures.md). A test that needs a real set either fails for
 * everyone or skips for everyone, and a suite of permanent skips is not coverage.
 *
 * WHY IT IS NOT JUST A MIRROR OF DatParser
 * ----------------------------------------
 * The obvious objection to a synthesized fixture is that it encodes the same format knowledge the
 * parser has, so a round trip proves only that the two agree with each other. Two things keep that
 * from being true here, and BOTH must be preserved by anyone editing this file:
 *
 *   1. This is written from the FORMAT SPECIFICATION (http://www.seasip.info/ccfile.html), not from
 *      DatParser. If someone "simplifies" it by copying the parser's logic, the tests immediately
 *      stop being able to find a parser bug. Cite the spec in any change here, not the parser.
 *   2. Tile codes are taken from the jar under test -- Tile's ordinal IS the CC1 byte code, so
 *      tile(Tile.BUG_UP) needs no hardcoded table that could drift.
 *
 * Its real value is in fixtures that encode a KNOWN-BAD input, which a well-formed level set would
 * never contain. offMapMonster() is the one that matters: it reproduces the jc-7 bug class exactly.
 */
public final class DatBuilder {

    /** The three signatures DatParser accepts. A .dat declares its ruleset in its first four bytes. */
    public static final int SIG_MS      = 0x0002AAAC;
    public static final int SIG_MS_PG   = 0x0003AAAC;
    public static final int SIG_LYNX    = 0x0102AAAC;

    /** CC1 text fields are Windows-1252, and the parser decodes them that way. */
    private static final Charset CP1252 = Charset.forName("Windows-1252");

    /** Field 6 (the encoded password) is XORed with this. Field 8 is the same text in the clear. */
    private static final int PASSWORD_XOR = 0x99;

    private static final int MAP_CELLS = 32 * 32;

    private int signature = SIG_MS;
    private final List<Level> levels = new ArrayList<>();

    public DatBuilder signature(int sig) { this.signature = sig; return this; }

    public Level level() { Level l = new Level(levels.size() + 1); levels.add(l); return l; }

    /** One level record. Fluent, and every setter is optional except what CC1 itself requires. */
    public final class Level {
        private final int number;
        private int timeLimit = 0, chips = 0;
        private final byte[] fg = new byte[MAP_CELLS];
        private final byte[] bg = new byte[MAP_CELLS];
        private String title, password, hint, author;
        private final List<int[]> monsters = new ArrayList<>();

        private Level(int number) { this.number = number; }

        public Level timeLimit(int t) { this.timeLimit = t; return this; }
        public Level chips(int c)     { this.chips = c;     return this; }
        public Level title(String s)    { this.title = s;    return this; }
        public Level password(String s) { this.password = s; return this; }
        public Level hint(String s)     { this.hint = s;     return this; }
        public Level author(String s)   { this.author = s;   return this; }

        /** Places a tile on the upper (foreground) layer. */
        public Level tile(int x, int y, Tile t) { fg[32 * y + x] = (byte) t.ordinal(); return this; }

        /** Places a tile on the lower (background) layer -- what sits under a creature or block. */
        public Level under(int x, int y, Tile t) { bg[32 * y + x] = (byte) t.ordinal(); return this; }

        /**
         * Adds an entry to optional field 10, the MS monster-movement list. Coordinates are written
         * as raw bytes with NO validation, which is the entire point: field 10 in real level sets
         * contains entries off the 32x32 map, and how an engine handles those is the jc-7 bug.
         */
        public Level monster(int x, int y) { monsters.add(new int[]{x, y}); return this; }

        public DatBuilder end() { return DatBuilder.this; }

        /**
         * The run-length-encoded bytes this level's foreground layer will be written as.
         *
         * Exposed so a test can assert which ENCODING BRANCHES a fixture actually exercises. Without
         * it, "both encodings decode correctly" is unfalsifiable: if the run threshold changed so
         * that everything became a run, every decode assertion would still pass and the literal
         * branch would silently go untested.
         */
        public byte[] encodedForegroundLayer() { return encodeLayer(fg); }

        private byte[] bytes() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            word(out, number);
            word(out, timeLimit);
            word(out, chips);
            word(out, 1);                       // map detail: always 1 for CC1
            layer(out, fg);
            layer(out, bg);
            out.write(optionalFields());
            return out.toByteArray();
        }

        private byte[] optionalFields() throws IOException {
            ByteArrayOutputStream fields = new ByteArrayOutputStream();
            if (title != null)    field(fields, 3, nullTerminated(title));
            if (password != null) field(fields, 6, xor(nullTerminated(password)));
            if (hint != null)     field(fields, 7, nullTerminated(hint));
            if (author != null)   field(fields, 9, nullTerminated(author));
            if (!monsters.isEmpty()) {
                ByteArrayOutputStream m = new ByteArrayOutputStream();
                for (int[] p : monsters) { m.write(p[0] & 0xFF); m.write(p[1] & 0xFF); }
                field(fields, 10, m.toByteArray());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] body = fields.toByteArray();
            word(out, body.length);
            out.write(body);
            return out.toByteArray();
        }
    }

    /** Writes the .dat and returns its path. The set name is the file name without its extension. */
    public Path writeTo(Path dir, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int32(out, signature);
        word(out, levels.size());
        for (Level l : levels) {
            byte[] body = l.bytes();
            word(out, body.length);
            out.write(body);
        }
        Files.createDirectories(dir);
        Path p = dir.resolve(fileName);
        Files.write(p, out.toByteArray());
        return p;
    }

    /* ---------- the format primitives, straight from the spec ---------- */

    private static void word(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void int32(ByteArrayOutputStream out, int v) {
        word(out, v & 0xFFFF);
        word(out, (v >>> 16) & 0xFFFF);
    }

    /**
     * Writes one TLV optional field: type byte, length byte, body.
     *
     * The length really is a single byte, so 255 is the spec's hard ceiling. Without this guard an
     * over-long title or a monster list past 127 entries writes a length of 0, the parser then
     * reads nothing for the field, its running optional-fields counter never advances past it, and
     * every subsequent field misparses -- a corrupt fixture that fails somewhere unrelated to the
     * mistake. Fail where the mistake is.
     */
    private static void field(ByteArrayOutputStream out, int type, byte[] body) throws IOException {
        if (body.length > 255) {
            throw new IllegalArgumentException(
                "optional field " + type + " is " + body.length + " bytes; the .dat format stores its "
                + "length in one byte, so 255 is the maximum");
        }
        out.write(type);
        out.write(body.length);
        out.write(body);
    }

    private static byte[] nullTerminated(String s) {
        byte[] raw = s.getBytes(CP1252);
        byte[] withNull = new byte[raw.length + 1];
        System.arraycopy(raw, 0, withNull, 0, raw.length);
        return withNull;                                    // the trailing 0 is already there
    }

    /**
     * Field 6 encoding: the text bytes are XORed, the terminating NUL is NOT.
     *
     * That asymmetry mirrors the reader exactly. DatParser.readEncodedText() reads length-1 bytes,
     * XORs those length-1, and then reads the terminator separately and discards it -- so a NUL
     * encoded here would never be decoded, and the loop bound below is load-bearing rather than an
     * off-by-one waiting to be tidied up.
     */
    private static byte[] xor(byte[] b) {
        byte[] copy = b.clone();
        for (int i = 0; i < copy.length - 1; i++) copy[i] = (byte) (copy[i] ^ PASSWORD_XOR);
        return copy;
    }

    /**
     * Writes one 32x32 layer, run-length encoded, preceded by its byte count.
     *
     * Runs of four or more become the 0xFF/count/code form and everything else is written
     * literally, so a single fixture exercises BOTH branches of the parser's decoder -- a large
     * floor field compresses, a hand-placed row of tiles does not. Four is the threshold because
     * three bytes of overhead only pays off above it; runs are also capped at 255, which is the
     * widest count the single count byte can hold.
     *
     * NOTE for a future tile addition: a cell whose value is literally 0xFF cannot be written in
     * the literal branch, because the reader treats 0xFF as the run marker. Unreachable today --
     * the highest Tile ordinal is 0x6F -- so this is a warning, not a defect. If Tile ever grows
     * past 0xFE, that cell must be emitted as a one-long run instead.
     */
    private static void layer(ByteArrayOutputStream out, byte[] cells) throws IOException {
        byte[] body = encodeLayer(cells);
        word(out, body.length);
        out.write(body);
    }

    /** The encoder itself, separated so a test can inspect its output (encodedForegroundLayer). */
    private static byte[] encodeLayer(byte[] cells) {
        ByteArrayOutputStream rle = new ByteArrayOutputStream();
        int i = 0;
        while (i < MAP_CELLS) {
            int run = 1;
            while (i + run < MAP_CELLS && cells[i + run] == cells[i] && run < 255) run++;
            if (run >= 4) {
                rle.write(0xFF);
                rle.write(run);
                rle.write(cells[i] & 0xFF);
                i += run;
            } else {
                rle.write(cells[i] & 0xFF);
                i++;
            }
        }
        return rle.toByteArray();
    }
}
