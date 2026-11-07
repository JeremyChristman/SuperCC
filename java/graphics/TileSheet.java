package graphics;

import emulator.SuperCC;
import io.SuccPaths;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
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
            String[] imgPaths = paths == null ? null : paths.getCustomTilesetImages();
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

    /* MOD (Jeremy, upstream merge): the one place a tileset number from the settings file becomes
     * images, for startup and for the per-ruleset swap. Before, both indexed values() directly and
     * had no fallback: a number out of range stopped startup (README section 6 warned about it), and
     * a tileset whose images failed left the game panel with none. The custom tileset makes the
     * second ordinary -- its images live outside the jar, can be moved or deleted, and the settings
     * file is shared by two machines -- so either case now draws `fallback` and says why. The
     * setting itself is left alone: the images may come back, or exist on the other machine. Only a
     * built-in fallback that cannot load (a broken jar) still throws. */
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
    public static BufferedImage loadTilesetImage(URL url, int tileWidth, int tileHeight) throws IOException {
        Image image = ImageIO.read(Objects.requireNonNull(url));
        if (image == null) throw new IOException("Not an image SuperCC can read:\n" + shown(url));  // MOD (Jeremy, upstream merge): see above
        Image imageResized = image.getScaledInstance(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage imageBuf = new BufferedImage(tileWidth * TILESHEET_WIDTH, tileHeight * TILESHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = imageBuf.createGraphics();
        gr.drawImage(imageResized, 0, 0, null);
        gr.dispose();
        return imageBuf;
    }
}
