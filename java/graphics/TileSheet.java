package graphics;

import emulator.SuperCC;
import io.SuccPaths;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
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
    
    /* MOD (Jeremy, upstream merge): every way the custom images can be unusable is an IOException
     * with words in it -- the type every caller already catches and reports. Upstream threw an
     * IOException with NO message when none had been chosen (its callers show getMessage(), so the
     * user saw "null"), let a missing file surface as ImageIO's "Can't get input stream from URL!",
     * and let a file that is not an image through as a NullPointerException, which no caller
     * catches (ImageIO.read returns null for a format it cannot read). A built-in sheet missing from
     * the jar stays the NullPointerException it always was: that is a broken build, not a user
     * mistake, and jc-17's Black and White fix is the proof it gets noticed. */
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
    /** A file URL as the path a user would recognize (URL.getPath() keeps %20 for every space). */
    private static String shown(URL url) {
        try { return new File(url.toURI()).getPath(); }
        catch (Exception e) { return url.toString(); }
    }
    /** What to draw for a tileset named by a number in succ_settings.ini: the sheet, its images, and,
     *  when the one asked for could not be used, why (otherwise null). */
    public record Choice(TileSheet sheet, BufferedImage[] images, String problem) {}

    /* MOD (Jeremy, upstream merge): what a ruleset's settings say to draw, as images -- the one place
     * that happens, for startup, the per-ruleset swap and a window-size change.
     *
     * Custom is a switch (SuccPaths.getCustomTileset says why it is never a number), and the number
     * beside it is always a BUILT-IN tileset: the one a build before jc-18 shows from the same file.
     * So:
     *   - switch on: custom, and if its images fail, THAT built-in tileset -- what the older build
     *     would show -- rather than the default (review);
     *   - switch off: the number, which must name a built-in tileset. A stored 5 (written by an
     *     unreleased build that kept custom as a number, or by hand) is refused like any other number
     *     out of range, not taken as custom: it would disagree with the switch, and it still stops a
     *     build before jc-18 at startup (review).
     * Every failure draws a fallback and says why; the settings are left alone, because the images
     * may come back or exist on the other machine. Only a built-in tileset that cannot load (a
     * broken jar) still throws. */
    public static Choice loadForRuleset(SuperCC emulator, SuccPaths paths, boolean lynx, TileSheet fallback,
                                        int tileWidth, int tileHeight) throws IOException {
        int n = lynx ? paths.getLynxTilesetNum() : paths.getMSTilesetNum();
        TileSheet[] all = values();
        boolean builtIn = n >= 0 && n < all.length && all[n] != CUSTOM;
        if (paths.getCustomTileset(lynx)) return load(emulator, CUSTOM.ordinal(), builtIn ? all[n] : fallback, tileWidth, tileHeight);
        if (!builtIn) {
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    "Tileset number " + n + " in succ_settings.ini is not a built-in tileset of this version ("
                    + java.util.Arrays.stream(all).filter(t -> t != CUSTOM).map(t -> String.valueOf(t.ordinal()))
                          .collect(java.util.stream.Collectors.joining(", "))
                    + ").\n\nShowing " + fallback.name + " instead.");
        }
        return load(emulator, n, fallback, tileWidth, tileHeight);
    }

    /** One tileset by its number, falling back to `fallback` -- with the reason -- when that number
     *  is not a tileset or its images cannot be loaded. loadForRuleset is what reads the settings. */
    public static Choice load(SuperCC emulator, int settingsNumber, TileSheet fallback, int tileWidth, int tileHeight) throws IOException {
        TileSheet[] all = values();
        if (settingsNumber < 0 || settingsNumber >= all.length) {
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    "Tileset number " + settingsNumber + " in succ_settings.ini is not one this version has.\n\n"
                    + "Showing " + fallback.name + " instead.");
        }
        TileSheet wanted = all[settingsNumber];
        try {
            return new Choice(wanted, wanted.getTileSheets(emulator, tileWidth, tileHeight), null);
        } catch (IOException e) {
            if (wanted == fallback) throw e;
            return new Choice(fallback, fallback.getTileSheets(emulator, tileWidth, tileHeight),
                    "The " + wanted.name + " tileset could not be loaded:\n" + e.getMessage()
                    + "\n\nShowing " + fallback.name + " instead. Your choice is kept; pick a tileset in View > Tileset to change it.");
        }
    }

    /** MOD (Jeremy, upstream merge): the custom tileset from two given files, so View > Tileset >
     *  custom can load both BEFORE saving their paths -- upstream saved first, and a file that is
     *  not an image was then saved too, failing again at every launch. */
    public static BufferedImage[] loadCustom(File base, File overlay, int tileWidth, int tileHeight) throws IOException {
        return new BufferedImage[] {loadTilesetImage(customImage(base), tileWidth, tileHeight),
                                    loadTilesetImage(customImage(overlay), tileWidth, tileHeight)};
    }
    private static URL customImage(File f) throws IOException {
        if (!f.isFile()) throw new IOException("Custom tileset image not found:\n" + f.getPath());
        return f.toURI().toURL();
    }
    /* MOD (Jeremy, upstream merge): the most pixels a tileset image may have. A sheet of 7 x 16 tiles
     * of 512 pixels -- far past any tile size the menus offer -- is 29 million. 32 million decodes to
     * 128 MB as an ordinary 8-bit image, and to 256 MB at worst, for 16 bits per channel (review); the
     * scaled copies are small. Above it, the image is refused BEFORE it is decoded: a PNG of a few
     * dozen bytes whose header claims 46000 x 46000 stays under ImageIO's own overflow check and makes
     * it allocate about 8 GB, an OutOfMemoryError that no fallback catches (review). A large sheet
     * also costs time, because every tileset is re-read when a set is opened; that is how the
     * built-in ones always worked. */
    private static final long MAX_PIXELS = 32L * 1024 * 1024;

    public static BufferedImage loadTilesetImage(URL url, int tileWidth, int tileHeight) throws IOException {
        Image image;
        ImageReader reader = null;
        // Both in the try: closing an ImageInputStream does NOT close the stream it wraps, and the
        // first version leaked a handle on every image it read -- which on Windows LOCKS the file, so
        // the user could not move or replace their own tileset while SuperCC ran (EngineTest 6f's
        // move of a loaded image is what failed).
        try (InputStream raw = Objects.requireNonNull(url).openStream();
             ImageInputStream in = ImageIO.createImageInputStream(raw)) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IOException("Not an image SuperCC can read:\n" + shown(url));
            reader = readers.next();
            reader.setInput(in, true, true);
            long width = reader.getWidth(0), height = reader.getHeight(0);
            if (width * height > MAX_PIXELS) {
                throw new IOException("Too large for a tileset image:\n" + shown(url) + "\nIt is " + width + " x " + height
                        + " pixels; SuperCC loads up to " + (MAX_PIXELS / (1024 * 1024)) + " million.");
            }
            image = reader.read(0);
        } catch (RuntimeException e) {
            // MOD (Jeremy, upstream merge): ImageIO's readers throw RUNTIME exceptions, not an
            // IOException, for files they cannot use -- IllegalArgumentException for a header that
            // overflows, IndexOutOfBoundsException for a GIF with no image in it, decoder-internal
            // array errors -- and each skipped every caller's fallback, at startup too (reviews).
            // Anything thrown while reading the user's file is that file's problem, said in words.
            throw new IOException("Not an image SuperCC can read:\n" + shown(url) + "\n(" + e + ")", e);
        } finally {
            if (reader != null) reader.dispose();
        }
        if (image == null) throw new IOException("Not an image SuperCC can read:\n" + shown(url));  // MOD (Jeremy, upstream merge): see above
        Image imageResized = image.getScaledInstance(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage imageBuf = new BufferedImage(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = imageBuf.createGraphics();
        gr.drawImage(imageResized, 0, 0, null);
        gr.dispose();
        return imageBuf;
    }
}
