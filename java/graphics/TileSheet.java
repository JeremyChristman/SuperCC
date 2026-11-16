package graphics;

import emulator.SuperCC;
import io.SuccPaths;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

public enum TileSheet {
    
    CCEDIT_TW("/resources/tw-edit-overlay.png", "/resources/tw-edit-tiles.png", "Tile World (CCEdit)"),
    TW("/resources/tw-overlay.png", "/resources/tw-tiles.png", "Tile World"),
    CCEDIT_MSCC("/resources/ms-edit-overlay.png", "/resources/ms-edit-tiles.png", "MSCC (CCEdit)"),
    MSCC("/resources/ms-overlay.png", "/resources/ms-tiles.png", "MSCC"),
//    CCEDIT_CC2("/resources/cc2-edit-overlay.png", "/resources/cc2-edit-tiles.png", "CC2 (CCEdit)"), //removed on account of not having permission to use and trying to avoid issues
//    CC2("/resources/cc2-overlay.png", "/resources/cc2-tiles.png", "CC2"),
    CCEDIT_BLACK_AND_WHITE("/resources/bw-overlay.png", "/resources/bw-tiles.png", "MSCC (Black and White - Editor)"), //should probably do a non-editor version
    CUSTOM(null, null, "custom");

    private static final int TILESHEET_WIDTH = 7, TILESHEET_HEIGHT = 16;
    
    private final String urlOverlay;
    private final String urlTiles;
    private final String name;
    
    private static String[] names;
    
    public static String[] getNames() {
        if (names == null) {
            TileSheet[] allTileSheets = TileSheet.values();
            names = new String[allTileSheets.length];
            for (int i = 0; i < allTileSheets.length; i++) {
                names[i] = allTileSheets[i].name;
            }
        }
        return names;
    }
    
    TileSheet(String urlOverlay, String urlTiles, String name) {
        this.urlOverlay = urlOverlay;
        this.urlTiles = urlTiles;
        this.name = name;
    }
    
    /* MOD (Jeremy, fix layer, UB-19): every way the custom images can be unusable is an IOException
     * with words in it -- the type the callers catch. (View > Tileset shows it, and so does startup
     * since UB-20's fallback, the ruleset swap and a window-size change since UB-21's.) Upstream
     * threw an IOException with NO message when none had been chosen (its callers show getMessage(), so the
     * user saw "null"), let a missing file surface as ImageIO's "Can't get input stream from URL!",
     * and let a file that is not an image through as a NullPointerException, which no caller
     * catches (ImageIO.read returns null for a format it cannot read). A built-in sheet missing from
     * the jar stays the NullPointerException it always was: that is a broken build, not a user
     * mistake, and jc-17's Black and White fix is the proof it gets noticed. jc-18's loader (merged
     * as "upstream merge", reverted for the upstream baseline in jc-22), re-applied alone; its
     * fallbacks came back with UB-20 (Choice, load, below: startup) and UB-21 (the swap, a
     * resize) and UB-16 (loadForRuleset, reading custom's own switch). */
    public BufferedImage[] getTileSheets(SuperCC emulator, int tileWidth, int tileHeight) throws IOException {
        URL localUrlTiles;
        URL localUrlOverlay;
        if (this == TileSheet.CUSTOM) {
            SuccPaths paths = emulator == null ? null : emulator.getPaths();
            // Two messages, so neither is false: with no settings loaded (a headless emulator) the
            // images may well be chosen -- they just cannot be looked up from here (review).
            if (paths == null) throw new IOException("The custom tileset's images are named in succ_settings.ini, which is not loaded here.");
            String[] imgPaths = paths.getCustomTilesetImages();
            if (imgPaths == null) throw new IOException("No images have been chosen for the custom tileset.");
            return loadCustom(new File(imgPaths[0]), new File(imgPaths[1]), tileWidth, tileHeight);
        } else {
            localUrlTiles = TileSheet.class.getResource(urlTiles);
            localUrlOverlay = TileSheet.class.getResource(urlOverlay);
        }
        return new BufferedImage[] {this.loadTilesetImage(localUrlTiles, tileWidth, tileHeight), this.loadTilesetImage(localUrlOverlay, tileWidth, tileHeight)};
    }
    /** MOD (Jeremy, fix layer, UB-19): a file URL as the path a user would recognize, for the
     *  messages (URL.getPath() keeps %20 for every space). */
    private static String shown(URL url) {
        try { return new File(url.toURI()).getPath(); }
        catch (Exception e) { return url.toString(); }
    }

    /** MOD (Jeremy, fix layer, UB-20): what to draw for a tileset named by a number in
     *  succ_settings.ini: the sheet, its images, and, when the one asked for could not be used, why
     *  (otherwise null). jc-18's, re-applied alone. */
    public record Choice(TileSheet sheet, BufferedImage[] images, String problem) {}

    /** MOD (Jeremy, fix layer, UB-20): one tileset by its number, falling back to `fallback` -- with
     *  the reason -- when that number is not a tileset or its images cannot be loaded, so a saved
     *  tileset that cannot be drawn no longer stops startup partway. The settings are left
     *  alone: the pictures may come back, or exist on the other machine. Only the fallback itself
     *  failing (a broken jar) still throws. jc-18's, re-applied alone. Since jc-37 (UB-16) the
     *  settings are read through loadForRuleset, below, which calls this with a number it has
     *  checked (or custom's); a resize calls it with the tileset drawn. Both of its messages end in KEPT (jc-34 review: jc-18 told
     *  only the custom case, though a number out of range is kept just the same, and said again at
     *  every start until it is changed). */
    public static Choice load(SuperCC emulator, int settingsNumber, TileSheet fallback, int tileWidth, int tileHeight) throws IOException {
        TileSheet[] all = values();
        if (settingsNumber < 0 || settingsNumber >= all.length) {
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    "Tileset number " + settingsNumber + " in succ_settings.ini is not one this version has.\n\n"
                    + "Showing " + fallback.name + " instead." + KEPT);
        }
        TileSheet wanted = all[settingsNumber];
        try {
            return new Choice(wanted, wanted.getTileSheets(emulator, tileWidth, tileHeight), null);
        } catch (IOException e) {
            if (wanted == fallback) throw e;
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    "The " + wanted.name + " tileset could not be loaded:\n" + e.getMessage()
                    + "\n\nShowing " + fallback.name + " instead." + KEPT);
        }
    }

    /* MOD (Jeremy, fix layer, UB-16): what a ruleset's settings say to draw, as images -- the one
     * place the SETTINGS are read as a tileset: startup, the per-ruleset swap and a window-size
     * change of a custom tileset. (View > Tile size reloads the tileset drawn through getTileSheets
     * directly, with no fallback: upstream's, and jc-18's alike -- BASELINE-ISSUES.md, "NOT
     * introduced".)
     *
     * Custom is a switch (SuccPaths.getCustomTileset says why it is never a number), and the number
     * beside it is always a BUILT-IN tileset: the one a build before jc-18 shows from the same file.
     * So:
     *   - switch on: custom, and if its pictures fail, THAT built-in tileset -- what the older build
     *     would show -- rather than the default;
     *   - switch off: the number, which must name a built-in tileset. A 5 here is refused like any
     *     other number out of range, not taken as custom: it would disagree with the switch. (A 5
     *     stored by jc-22 to jc-36 never gets here: SuccPaths reads it as the switch, at load.)
     * Every failure draws a fallback and says why; the settings are left alone, because the pictures
     * may come back or exist on the other machine. Only a built-in tileset that cannot load (a
     * broken jar) still throws. jc-18's, re-applied alone, but its message for a number that is
     * not a built-in tileset names the setting it came from (jc-34 and jc-36 reviews: "Tileset
     * number 99" alone did not say whether to fix TilesheetNum or LynxTilesheetNum). */
    public static Choice loadForRuleset(SuperCC emulator, SuccPaths paths, boolean lynx, TileSheet fallback,
                                        int tileWidth, int tileHeight) throws IOException {
        int n = lynx ? paths.getLynxTilesetNum() : paths.getMSTilesetNum();
        TileSheet[] all = values();
        boolean builtIn = n >= 0 && n < all.length && all[n] != CUSTOM;
        if (paths.getCustomTileset(lynx)) return load(emulator, CUSTOM.ordinal(), builtIn ? all[n] : fallback, tileWidth, tileHeight);
        if (!builtIn) {
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    (lynx ? "LynxTilesheetNum" : "TilesheetNum") + " = " + n + " in succ_settings.ini is not a built-in tileset of this version ("
                    + java.util.Arrays.stream(all).filter(t -> t != CUSTOM).map(t -> String.valueOf(t.ordinal()))
                          .collect(java.util.stream.Collectors.joining(", "))
                    + ").\n\nShowing " + fallback.name + " instead." + KEPT);
        }
        return load(emulator, n, fallback, tileWidth, tileHeight);
    }

    /** MOD (Jeremy, fix layer, UB-20): how load's messages end -- the saved choice is left as it is. */
    private static final String KEPT = " Your choice is kept; pick a tileset in View > Tileset to change it.";

    /** MOD (Jeremy, fix layer, UB-19): the custom tileset from two given files, so View > Tileset >
     *  custom can load both BEFORE saving their paths -- upstream saved first, and a file that is
     *  not an image was then saved too, failing again at every launch. */
    public static BufferedImage[] loadCustom(File base, File overlay, int tileWidth, int tileHeight) throws IOException {
        return new BufferedImage[] {loadTilesetImage(customImage(base), tileWidth, tileHeight),
                                    loadTilesetImage(customImage(overlay), tileWidth, tileHeight)};
    }
    /** MOD (Jeremy, fix layer, UB-19): a chosen picture as the URL loadTilesetImage reads, or the
     *  reason it cannot be one, naming it by its absolute path, as every other message does. */
    private static URL customImage(File f) throws IOException {
        String named = f.getAbsolutePath();
        BasicFileAttributes found = null;   // stays null when the file cannot be looked at
        try {
            found = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
        } catch (InvalidPathException e) {
            // From a hand-edited settings file, say: never looked for, so not "not found" (jc-33 review).
            throw new IOException("Custom tileset image is not a valid path:\n" + named, e);
        } catch (NoSuchFileException e) {
            throw new IOException("Custom tileset image not found:\n" + named, e);
        } catch (IOException e) {
            // There or not, it cannot be looked at -- in a folder this user may not open: opening it
            // says why, naming it (jc-33 review: File.exists() answered "not found").
        }
        // There, but not a file -- a folder named in a hand-edited settings file (jc-33 review).
        if (found != null && !found.isRegularFile()) throw new IOException("Custom tileset image is not a file:\n" + named);
        return f.toURI().toURL();
    }
    /* MOD (Jeremy, fix layer, UB-19): the most pixels a tileset image may have, 32 Mi (33,554,432). A
     * sheet of 7 x 16 tiles of 512 pixels -- far past any tile size the menus offer -- is 29 million.
     * 32 Mi decodes to 128 MB as an ordinary 8-bit image, and to 256 MB at worst, for 16 bits per
     * channel (review); the scaled copies are small. Above it, the image is refused BEFORE it is
     * decoded: a PNG of a few dozen bytes whose header claims 46000 x 46000 stays under ImageIO's own
     * overflow check and makes it allocate about 8 GB, an OutOfMemoryError that no fallback catches
     * (review). The cap bounds a picture's dimensions only. A large sheet also costs time, because
     * every tileset is re-read when a set is opened; that is how the built-in ones always worked. */
    private static final long MAX_PIXELS = 32L * 1024 * 1024;

    public static BufferedImage loadTilesetImage(URL url, int tileWidth, int tileHeight) throws IOException {
        // A built-in sheet missing from the jar is a broken build, not a user mistake, and stays the
        // NullPointerException it always was -- thrown here, outside the try below, which would
        // otherwise catch it (jc-33 review: it then escaped only by accident, from shown()).
        URL source = Objects.requireNonNull(url);
        File file = fileOf(source);
        Image image;
        ImageReader reader = null;
        /* MOD (Jeremy, fix layer, UB-19): how the picture is read. A file -- every custom picture -- is
         * read in place, with no cache and no copy: ImageIO's default cache, a temp file, failed on its
         * own when the temp folder could not be used ("Can't create cache file!"), reported as the
         * picture's fault, and a cache in memory held a large file whole on the heap (jc-33 reviews).
         * A built-in sheet, inside the jar, is cached in memory; those are small. Every stream is a
         * resource of this try, the jar entry too (null for a file): closing ImageIO's stream does not
         * close the one it wraps, and jc-18's first loader leaked a handle on every image it read --
         * which on Windows LOCKS the file, so the user could not move or replace their own tileset
         * while SuperCC ran (EngineTest 6f's move of a loaded image is what failed). */
        try (InputStream raw = file == null ? source.openStream() : null;
             ImageInputStream in = file != null ? new FileImageInputStream(file) : new MemoryCacheImageInputStream(raw)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw notAnImage(source, null);
            reader = readers.next();
            reader.setInput(in, true, true);
            long width = reader.getWidth(0), height = reader.getHeight(0);
            if (width * height > MAX_PIXELS) {
                throw new Refused("Too large for a tileset image:\n" + shown(source) + "\nIt is " + width + " x " + height
                        + " pixels; SuperCC loads up to " + String.format(Locale.ROOT, "%,d", MAX_PIXELS) + " pixels.", null);
            }
            image = reader.read(0);
        } catch (Refused r) {
            throw r;   // the loader's own words, already naming the file
        } catch (FileNotFoundException e) {
            // MOD (Jeremy, fix layer, UB-19): a picture that cannot be opened -- this user may not
            // read it, or it went away or was locked after customImage looked -- in words, naming it,
            // not as the system's bare "<path> (Access is denied)" (jc-33 reviews, reproduced).
            throw new IOException("Could not open the picture:\n" + shown(source) + "\n(" + reason(e) + ")", e);
        } catch (RuntimeException | IIOException e) {
            // MOD (Jeremy, fix layer, UB-19): ImageIO's readers throw RUNTIME exceptions, not an
            // IOException, for files they cannot use -- IllegalArgumentException for a header that
            // overflows, IndexOutOfBoundsException for a GIF with no image in it, decoder-internal
            // array errors -- and each skipped every caller's fallback, at startup too (reviews).
            // Their own IIOException, for a file cut short or damaged ("Error reading PNG image
            // data"), named no file (jc-33 review; jc-18 let it through). Anything the reader throws
            // about the user's file is that file's problem, said in words, naming it, with the
            // reader's own words after them.
            throw notAnImage(source, e);
        } catch (IOException e) {
            // MOD (Jeremy, fix layer, UB-19): any other failure to read -- a drive that drops part
            // way, say -- names the file too, but is not blamed on the picture: it may well be a good
            // one (jc-33 reviews). Not checked by a test: it needs a device that fails mid-read.
            throw new IOException("Could not read the picture:\n" + shown(source) + "\n(" + e + ")", e);
        } finally {
            if (reader != null) reader.dispose();
        }
        if (image == null) throw notAnImage(source, null);  // MOD (Jeremy, fix layer, UB-19): see above
        Image imageResized = image.getScaledInstance(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage imageBuf = new BufferedImage(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = imageBuf.createGraphics();
        gr.drawImage(imageResized, 0, 0, null);
        gr.dispose();
        return imageBuf;
    }

    /** MOD (Jeremy, fix layer, UB-19): the file a URL names, or null for anything else -- a built-in
     *  sheet, inside the jar. */
    private static File fileOf(URL source) {
        if (!"file".equals(source.getProtocol())) return null;
        try { return new File(source.toURI()); }
        catch (URISyntaxException | IllegalArgumentException notAPlainFile) { return null; }
    }

    /** MOD (Jeremy, fix layer, UB-19): why a file could not be opened, without the path the system puts
     *  in front of it ("C:\...\base.png (Access is denied)" gives "Access is denied"; jc-33 review: the
     *  message printed the path twice). */
    private static String reason(FileNotFoundException e) {
        String message = String.valueOf(e.getMessage());
        int open = message.lastIndexOf(" (");
        return open >= 0 && message.endsWith(")") ? message.substring(open + 2, message.length() - 1) : message;
    }

    /** MOD (Jeremy, fix layer, UB-19): a picture the loader itself refuses, in its own words -- so the
     *  catch for what the image reader throws does not wrap it a second time. */
    private static final class Refused extends IOException {
        Refused(String message, Throwable cause) { super(message, cause); }
    }

    /** MOD (Jeremy, fix layer, UB-19): the one message for a file the image reader cannot use, naming
     *  it, with the reader's own words when it gave any (jc-33 review: it was written out three times). */
    private static IOException notAnImage(URL url, Exception cause) {
        return new Refused("Not an image SuperCC can read:\n" + shown(url) + (cause == null ? "" : "\n(" + cause + ")"), cause);
    }
}
