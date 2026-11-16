import emulator.SuperCC;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * SuperCC STARTING with a saved tileset it cannot draw -- the start a double-click makes, in a real
 * window, with a settings file of its own.
 *
 * Upstream's SuperCC.initialiseTilesheet indexed TileSheet.values() with the saved number and passed  // aen-exempt: upstream method name
 * whatever images it got on to the game panel, so a number this version does not have, or custom
 * whose pictures are missing or are not pictures, threw there. The window was already up -- Gui
 * builds it, drawn in the default tileset, before this runs -- so SuperCC came up looking almost
 * normal, but the rest of startup never ran: the saved tile size was not applied (the window stayed
 * laid out for 20-pixel tiles) nor the saved TWS notation, and nothing said why except the error
 * log's one notice, "SuperCC recorded an error" (BASELINE-ISSUES.md UB-20; jc-22 to jc-33). MOD
 * (Jeremy, fix layer, UB-20): it goes through TileSheet.load now, which draws the default tileset
 * instead and says why, once, leaving the saved choice alone, and startup carries on. Each case below
 * writes a settings file -- every one with 32-pixel tiles and the TWS notation on, so a start that
 * stops early shows -- then starts the program in a child JVM as SuperCC.main does --
 * SuperCC.initialise on the event thread -- with every dialog it shows read and closed, and reports  // aen-exempt: upstream method name
 * what it saw. Two controls (a built-in tileset, and a good custom pair) start normally, show
 * nothing, and give what a normal start lays out. Section 2 starts with a level set on the command
 * line, which SuperCC opens BEFORE it reaches the tileset: opening it runs the ruleset swap, still
 * upstream's (UB-21), so there a number out of range still stops startup partway; two pins there
 * flip when UB-21's fix comes back.
 *
 * Like GuiWindowTest, it needs a desktop, and skips without one. A SuperCC window appears for a
 * moment for each case.
 */
public class StartupTilesetTest {

    public static void main(String[] args) {
        System.exit(Harness.run("StartupTilesetTest", StartupTilesetTest::body));
    }

    /** What one start reported, by key; "message" holds every message shown, in order. */
    static final class Start {
        final Map<String, String> values = new HashMap<>();
        final List<String> messages = new ArrayList<>();
        final List<String> behind = new ArrayList<>();   // the main window's size as each message showed
        boolean headless, ended;
        String get(String key) { return values.getOrDefault(key, "(not reported)"); }
        /** How many messages, and the first line of the first. */
        String firstMessage() {
            return messages.size() + (messages.size() == 1 ? " message" : " messages")
                    + (messages.isEmpty() ? "" : ", the first: " + messages.get(0).split("\n")[0]);
        }
    }

    /** Added to every case's [Graphics] lines: what a start that stops early would not apply. */
    private static final String EVERY_START = "TileWidth = 32\nTileHeight = 32\nTWSNotate = true\n";

    /** A start in a folder of its own with these [Graphics] lines; args go to SuperCC as main's would. */
    private static Start start(String name, String settings, String... args) throws Exception {
        Path dir = Harness.tempDir("scc-startup-" + name + "-");
        Files.writeString(dir.resolve(io.SuccPaths.SETTINGS_FILE_NAME), settings.replace("{dir}", dir.toString()) + EVERY_START);
        return launch(dir, args);
    }

    private static Start launch(Path dir, String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Djava.awt.headless=false",
                "-cp", System.getProperty("java.class.path"), "StartupTilesetTest$Probe"));
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String s; (s = r.readLine()) != null; ) lines.add(s);
            } catch (Exception ignored) { }
        });
        reader.start();
        Start s = new Start();
        s.ended = p.waitFor(60, TimeUnit.SECONDS);
        if (!s.ended) p.destroyForcibly();
        reader.join(5000);
        for (String line : new ArrayList<>(lines)) {
            if (line.equals("HEADLESS")) s.headless = true;
            if (!line.startsWith("RESULT ")) continue;
            String[] kv = line.substring(7).split("\t", 2);
            String value = kv.length > 1 ? kv[1].replace("\\n", "\n") : "";
            if (kv[0].equals("message")) s.messages.add(value);
            else if (kv[0].equals("behind")) s.behind.add(value);
            else s.values.put(kv[0], value);
        }
        return s;
    }

    private static void body() throws Exception {
        Harness.section("1. a saved tileset that cannot be drawn: SuperCC still starts, and says why");
        Path pictures = Harness.tempDir("scc-startup-pictures-");
        File base = pictures.resolve("base.png").toFile(), overlay = pictures.resolve("overlay.png").toFile();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", base);
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(7 * 4, 16 * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", overlay);
        File notes = pictures.resolve("notes.png").toFile();
        Files.writeString(notes.toPath(), "this is text, not a picture");

        // The controls first: a normal start, and the size it lays the window out to.
        Start builtIn = start("builtin", "[Graphics]\nTilesheetNum = 3\n");
        if (builtIn.headless) { Harness.skip("no desktop to draw on here, so SuperCC cannot start a window"); return; }
        String normal = builtIn.get("size");
        Harness.eq("a built-in tileset: startup throws nothing", builtIn.get("threw"), "nothing");
        Harness.eq("a built-in tileset: it is drawn", builtIn.get("drawn"), "MSCC");
        Harness.eq("a built-in tileset: no message", builtIn.messages.toString(), "[]");
        Harness.check("a built-in tileset: the window is showing, laid out (" + normal + ")",
                builtIn.get("window").equals("showing") && normal.matches("\\d{3,}x\\d{3,}"));
        Harness.eq("a built-in tileset: the saved tile size is applied", builtIn.get("tiles"), "32x32");
        Harness.eq("a built-in tileset: the saved TWS notation is applied", builtIn.get("notation"), "true");
        Start custom = start("custom", "[Graphics]\nTilesheetNum = 5\nCustomBaseImage = " + base + "\nCustomOverlayImage = " + overlay + "\n");
        Harness.eq("a custom tileset whose pictures load: startup throws nothing", custom.get("threw"), "nothing");
        Harness.eq("a custom tileset whose pictures load: it is drawn", custom.get("drawn"), "CUSTOM");
        Harness.eq("a custom tileset whose pictures load: no message", custom.messages.toString(), "[]");
        Harness.eq("a custom tileset whose pictures load: laid out, tile size and notation as saved",
                custom.get("size") + " " + custom.get("tiles") + " " + custom.get("notation"), normal + " 32x32 true");

        String fallback = "CCEDIT_TW", shown = "Tile World (CCEdit)";
        String kept = "Your choice is kept; pick a tileset in View > Tileset to change it.";
        String missingCustom = "[Graphics]\nTilesheetNum = 5\nCustomBaseImage = {dir}" + File.separator
                + "gone-base.png\nCustomOverlayImage = {dir}" + File.separator + "gone-overlay.png\n";
        Object[][] cases = {
            { "number 99", "[Graphics]\nTilesheetNum = 99\n", "TilesheetNum = 99",
              "Tileset number 99 in succ_settings.ini is not one this version has.\n\nShowing " + shown + " instead. " + kept },
            { "number -1", "[Graphics]\nTilesheetNum = -1\n", "TilesheetNum = -1",
              "Tileset number -1 in succ_settings.ini is not one this version has.\n\nShowing " + shown + " instead. " + kept },
            { "custom, its pictures missing", missingCustom, "TilesheetNum = 5",
              "The custom tileset could not be loaded:\nCustom tileset image not found:\n" },
            { "custom, a file that is not a picture", "[Graphics]\nTilesheetNum = 5\nCustomBaseImage = " + notes
              + "\nCustomOverlayImage = " + overlay + "\n", "TilesheetNum = 5",
              "The custom tileset could not be loaded:\nNot an image SuperCC can read:\n" } };
        for (Object[] c : cases) {
            String what = (String) c[0], expected = (String) c[3];
            boolean exact = !expected.endsWith("\n");   // the custom cases end in a path, read by prefix
            Start s = start(what.replaceAll("[^a-z0-9]+", "-"), (String) c[1]);
            Harness.eq(what + ": startup throws nothing", s.get("threw"), "nothing");
            Harness.eq(what + ": the window is showing, laid out as a normal start is", s.get("window") + " " + s.get("size"), "showing " + normal);
            Harness.eq(what + ": the default tileset is drawn instead", s.get("drawn"), fallback);
            Harness.eq(what + ": the rest of startup ran: the saved tile size is applied", s.get("tiles"), "32x32");
            Harness.eq(what + ": the rest of startup ran: the saved TWS notation is applied", s.get("notation"), "true");
            String said = s.messages.size() == 1 ? s.messages.get(0) : s.messages.size() + " messages: " + s.messages;
            Harness.check(what + ": one message, saying why" + (said.equals(expected) || said.startsWith(expected) ? "" : " (said: " + said + ")"),
                    s.messages.size() == 1 && (exact ? said.equals(expected) : said.startsWith(expected) && said.endsWith(kept)));
            // Said last, so the window behind it is the finished one (jc-34 review; jc-18 said it
            // before the layout, over the window Gui builds at 20-pixel tiles).
            Harness.eq(what + ": the message shows over the finished window", s.behind.toString(), "[" + normal + "]");
            Harness.eq(what + ": the saved choice is kept", s.get("saved"), (String) c[2]);
            Harness.check(what + ": the child ended by itself", s.ended && s.get("end").equals("reached"));
        }

        Harness.section("2. ... with a level set on the command line: opening it swaps the tileset first (UB-21, still upstream's)");
        /* SuperCC.initialise opens a set named on the command line (ArgumentParser) BEFORE it reaches  // aen-exempt: upstream method name
         * the tileset, and opening a set runs Gui.swapRulesetTilesheet, which is still upstream's
         * (UB-21): it indexes TileSheet.values() with the saved number, and says only "Could not load
         * relevant tilesheet" when the tileset fails. So with a set on the command line a number out
         * of range still throws before UB-20's fallback is reached -- startup stops partway, as
         * before jc-34 -- and custom that fails says the swap's words first, then UB-20's. Pinned
         * here, so UB-21's fix shows as the correct answer coming back. */
        Path setDir = Harness.tempDir("scc-startup-set-");
        DatBuilder b = new DatBuilder().signature(DatBuilder.SIG_MS);
        b.level().title("Startup").chips(0).timeLimit(60).tile(1, 1, game.Tile.CHIP).end();
        String set = b.writeTo(setDir, "startup.dat").toString();
        Start withSet = start("builtin-set", "[Graphics]\nTilesheetNum = 3\n", set);
        Harness.eq("a built-in tileset and a set: nothing thrown, nothing said, the set open, laid out as saved",
                withSet.get("threw") + " " + withSet.messages + " " + withSet.get("title") + " " + withSet.get("size") + " "
                + withSet.get("tiles") + " " + withSet.get("notation"), "nothing [] [SuperCC - Startup] " + normal + " 32x32 true");
        Start n99Set = start("number-99-set", "[Graphics]\nTilesheetNum = 99\n", set);
        Harness.upstreamBug("number 99 and a set: startup throws nothing (the swap indexes the number first)",
                n99Set.get("threw"), "java.lang.ArrayIndexOutOfBoundsException: Index 99 out of bounds for length 6", "nothing");
        Start missingSet = start("custom-missing-set", missingCustom, set);
        Harness.upstreamBug("custom missing and a set: the first message says why (the swap's comes first, naming nothing)",
                missingSet.firstMessage().replaceFirst("^\\d+ messages?, ", ""), "the first: Could not load relevant tilesheet",
                "the first: The custom tileset could not be loaded:");
        Harness.eq("custom missing and a set: startup still finishes, in the default tileset, after the swap's message (UB-20)",
                missingSet.get("threw") + " " + missingSet.get("drawn") + " " + missingSet.get("tiles") + " " + missingSet.get("notation"),
                "nothing CCEDIT_TW 32x32 true");
        for (Start s : new Start[] {withSet, n99Set, missingSet})
            Harness.check("and that child ended by itself (" + s.get("title") + ")", s.ended && s.get("end").equals("reached"));
    }

    /** The child JVM: starts SuperCC as main does, reads and closes each dialog, reports. */
    public static final class Probe {
        static void report(String key, Object value) {
            System.out.println("RESULT " + key + "\t" + String.valueOf(value).replace("\r", "").replace("\n", "\\n"));
        }

        public static void main(String[] args) throws Exception {
            if (GraphicsEnvironment.isHeadless()) { System.out.println("HEADLESS"); System.exit(0); }
            List<String> messages = Collections.synchronizedList(new ArrayList<>());
            List<String> behind = Collections.synchronizedList(new ArrayList<>());
            Set<Window> taken = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
            Thread closer = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    for (Window win : Window.getWindows()) {
                        if (!(win instanceof JDialog) || !win.isShowing() || !taken.add(win)) continue;
                        SwingUtilities.invokeLater(() -> read(win, "", messages, behind));
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            });
            closer.setDaemon(true);
            closer.start();
            String threw = "nothing";
            try {
                SwingUtilities.invokeAndWait(() -> SuperCC.initialise(args));  // aen-exempt: upstream method name
            } catch (InvocationTargetException e) {
                threw = String.valueOf(e.getCause());
            }
            Thread.sleep(500);                      // a dialog queued by startup gets read too
            closer.interrupt();
            closer.join(2000);
            SwingUtilities.invokeAndWait(() -> { });  // and what the closer queued has run
            // A dialog that came after the closer stopped is still read, marked "(late) ", so it
            // fails a count instead of going unseen (jc-34 review).
            SwingUtilities.invokeAndWait(() -> {
                for (Window win : Window.getWindows())
                    if (win instanceof JDialog && win.isShowing() && taken.add(win)) read(win, "(late) ", messages, behind);
            });
            graphics.Gui gui = null;
            for (Window win : Window.getWindows()) if (win instanceof graphics.Gui) gui = (graphics.Gui) win;
            graphics.Gui g = gui;
            String[] seen = new String[5];
            SwingUtilities.invokeAndWait(() -> {
                seen[0] = g == null ? "none" : g.isShowing() ? "showing" : "not showing";
                seen[1] = g == null ? "-" : g.getWidth() + "x" + g.getHeight();
                seen[2] = g == null || g.getGamePanel() == null ? "-" : String.valueOf(g.getGamePanel().getTileSheet());
                seen[3] = g == null || g.getGamePanel() == null ? "-" : g.getGamePanel().getTileWidth() + "x" + g.getGamePanel().getTileHeight();
                seen[4] = g == null ? "-" : "[" + g.getTitle() + "]";   // names the level, once one is open
            });
            // What the level panel draws times with: upstream's private flag, set only by
            // changeNotation, which startup calls after the tileset (read here, not through the
            // settings, whose getter reads the file and so says "true" whatever startup did).
            java.lang.reflect.Field notation = graphics.LevelPanel.class.getDeclaredField("twsNotation");
            notation.setAccessible(true);
            report("threw", threw);
            report("window", seen[0]);
            report("size", seen[1]);
            report("drawn", seen[2]);
            report("tiles", seen[3]);
            report("notation", notation.getBoolean(null));
            report("title", seen[4]);
            for (String m : new ArrayList<>(messages)) report("message", m);
            for (String b : new ArrayList<>(behind)) report("behind", b);
            String saved = "(none)";
            for (String line : Files.readAllLines(Path.of(io.SuccPaths.SETTINGS_FILE_NAME), StandardCharsets.UTF_8))
                if (line.startsWith("TilesheetNum")) saved = line.trim();
            report("saved", saved);
            if (g != null) SwingUtilities.invokeAndWait(g::dispose);
            report("end", "reached");
            System.exit(0);
        }

        /** On the event thread: one dialog's message (prefixed), and the main window's size behind
         *  it as it showed; then it is closed. */
        static void read(Window win, String prefix, List<String> messages, List<String> behind) {
            JOptionPane pane = find((Container) win, JOptionPane.class);
            messages.add(prefix + (pane == null ? "(a dialog that is not a message)" : String.valueOf(pane.getMessage())));
            String size = "(no main window)";
            for (Window w : Window.getWindows())
                if (w instanceof graphics.Gui) size = w.getWidth() + "x" + w.getHeight();
            behind.add(size);
            win.dispose();
        }

        static <T> T find(Container c, Class<T> type) {
            for (Component k : c.getComponents()) {
                if (type.isInstance(k)) return type.cast(k);
                if (k instanceof Container) {
                    T f = find((Container) k, type);
                    if (f != null) return f;
                }
            }
            return null;
        }
    }
}
