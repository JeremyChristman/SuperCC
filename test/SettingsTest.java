import io.SuccPaths;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression tests for SuccPaths -- the settings file's name, layout, defaults and switches.
 *
 * This class deliberately tests through the BUILT JAR rather than the source tree, because
 * build.ps1 is a splice build: what ships is compiled classes overlaid on a prebuilt baseline, and
 * that is the artifact whose behavior matters. Run it with run-tests.ps1.
 *
 * Two checks need something this repo does not contain and SKIP when it is absent:
 *   -Dsupercc.jar=PATH  a built SuperCC.jar, scanned for stale references to removed methods
 *   -Dsupercc.mo3=PATH  MO3.dat, the Lynx-signature set that motivated jc-9's AlwaysOpenInMS
 */
public class SettingsTest {

    /* Counting, printing and reporting moved to Harness so that every test file reports the same
     * way and CI gets one result format. These delegate rather than being replaced at every call
     * site: 90 assertions read better as check(...) than as Harness.check(...), and a wrapper is
     * cheaper to review than 90 edited lines. */
    private static void skip(String why) { Harness.skip(why); }

    private static void check(String what, boolean ok) { Harness.check(what, ok); }

    private static void eq(String what, Object actual, Object expected) { Harness.eq(what, actual, expected); }

    /* Scratch space that is really cleaned up on exit. The old deleteOnExit() form could not
     * remove these, because File.deleteOnExit deletes a DIRECTORY and one a test has written a
     * settings file into is never empty -- which had quietly left 1,482 populated jc8-* folders
     * in %TEMP%. Harness.tempDir walks the tree in a shutdown hook instead. */
    private static Path freshDir(String name) throws IOException {
        return Harness.tempDir("jc8-" + name + "-");
    }

    /** A settings file's lines, CRs dropped (section 13b compares WHOLE lines). */
    private static java.util.List<String> lines(File f) throws IOException {
        return java.util.Arrays.asList(Files.readString(f.toPath(), StandardCharsets.UTF_8).replace("\r", "").split("\n"));
    }

    /** A ruleset's custom switch, by reflection (13b): "on", "off", or "no getCustomTileset" on a jar
     *  from before UB-16's fix (jc-22 to jc-36). */
    private static String customSwitch(SuccPaths p, boolean lynx) {
        try {
            return (Boolean) SuccPaths.class.getMethod("getCustomTileset", boolean.class).invoke(p, lynx) ? "on" : "off";
        } catch (NoSuchMethodException e) {
            return "no getCustomTileset";
        } catch (ReflectiveOperationException e) {
            return "cannot call getCustomTileset: " + e;
        }
    }

    /** setCustomTileset(lynx, base, overlay) or setBuiltInTileset(lynx, n), by reflection (13b):
     *  "done", or "no <name>" on a jar from before UB-16's fix. */
    private static String call(SuccPaths p, String name, boolean lynx, Object... rest) {
        Class<?>[] types = new Class<?>[rest.length + 1];
        Object[] args = new Object[rest.length + 1];
        types[0] = boolean.class;
        args[0] = lynx;
        for (int i = 0; i < rest.length; i++) {
            types[i + 1] = rest[i] instanceof Integer ? int.class : rest[i].getClass();
            args[i + 1] = rest[i];
        }
        try {
            SuccPaths.class.getMethod(name, types).invoke(p, args);
            return "done";
        } catch (NoSuchMethodException e) {
            return "no " + name;
        } catch (ReflectiveOperationException e) {
            return "cannot call " + name + ": " + e;
        }
    }

    /** A file whose LynxTilesheetNum is `five` and TilesheetNum 2, as read: "<Lynx switch> <Lynx
     *  number>, MS <MS switch> <MS number>" (13b). */
    private static String lynxMigrated(int five) throws IOException {
        SuccPaths p = SuccPaths.load(settingsWith(freshDir("ct-lynx-five"), "[Graphics]", "TilesheetNum = 2", "LynxTilesheetNum = " + five));
        return customSwitch(p, true) + " " + p.getLynxTilesetNum() + ", MS " + customSwitch(p, false) + " " + p.getMSTilesetNum();
    }

    /** getCustomTilesetImages()'s answer in words a pin can compare (section 13). */
    private static String chosen(String[] images) {
        if (images == null) return "null";
        boolean emptyBase = images[0].isEmpty(), emptyOverlay = images[1].isEmpty();
        if (emptyBase && emptyOverlay) return "a pair of empty paths";
        if (emptyBase || emptyOverlay) return "a pair, one of them empty";
        return "a pair of paths";
    }

    /** Writes a settings file containing exactly the given lines. */
    private static File settingsWith(Path dir, String... lines) throws IOException {
        File f = dir.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(f.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        return f;
    }

    public static void main(String[] args) {
        System.exit(Harness.run("SettingsTest", SettingsTest::body));
    }

    private static void body() throws Exception {

        Harness.section("1. the settings file is named succ_settings.ini");
        eq("SETTINGS_FILE_NAME", SuccPaths.SETTINGS_FILE_NAME, "succ_settings.ini");
        eq("DEFAULT_TWS_PATH", SuccPaths.DEFAULT_TWS_PATH, "tws");

        Harness.section("2. a fresh install creates a complete stock file");
        Path d2 = freshDir("fresh");
        File f2 = d2.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        SuccPaths p2 = SuccPaths.load(f2);
        check("the file was created", f2.exists());
        eq("no load warning", p2.getLoadWarning(), null);
        check("it persists settings", p2.isPersisting());
        String stock = Files.readString(f2.toPath(), StandardCharsets.UTF_8);
        System.out.println("---- stock succ_settings.ini ----");
        System.out.println(stock);
        System.out.println("---- (" + stock.getBytes(StandardCharsets.UTF_8).length + " bytes) ----");
        check("contains 'TWS = tws'", stock.contains("TWS = tws\n"));
        check("contains 'ShowBuildTag = false'", stock.contains("ShowBuildTag = false"));
        check("no literal 'null' anywhere", !stock.contains("null"));
        check("no trailing newline", !stock.endsWith("\n"));
        check("has all 3 sections", stock.contains("[Paths]") && stock.contains("[Controls]")
                                    && stock.contains("[Graphics]"));
        eq("build tag OFF on a fresh install", p2.getShowBuildTag(), false);
        eq("TWS resolves to <dir>\\tws", p2.getTWSPath(), d2.resolve("tws").toString());

        Harness.section("3. ShowBuildTag is strictly opt-in");
        String[] on  = {"true", "TRUE", "True", "1", " true ", "true "};
        String[] off = {"false", "FALSE", "0", "yes", "on", "y", "-1", "2", "true!", "",
                        "true ; my comment", "null"};
        for (String v : on) {
            Path d = freshDir("on");
            SuccPaths p = SuccPaths.load(settingsWith(d, "[Graphics]", "ShowBuildTag = " + v));
            eq("ON  for [" + v + "]", p.getShowBuildTag(), true);
        }
        for (String v : off) {
            Path d = freshDir("off");
            SuccPaths p = SuccPaths.load(settingsWith(d, "[Graphics]", "ShowBuildTag = " + v));
            eq("OFF for [" + v + "]", p.getShowBuildTag(), false);
        }
        Path d3a = freshDir("absent");
        eq("OFF when the key is absent",
           SuccPaths.load(settingsWith(d3a, "[Graphics]", "TileWidth = 20")).getShowBuildTag(), false);
        Path d3b = freshDir("nospace");
        eq("OFF for the no-space typo 'ShowBuildTag=true'",
           SuccPaths.load(settingsWith(d3b, "[Graphics]", "ShowBuildTag=true")).getShowBuildTag(), false);
        Path d3c = freshDir("wrongsection");
        eq("OFF when the key sits under [Paths]",
           SuccPaths.load(settingsWith(d3c, "[Paths]", "ShowBuildTag = true")).getShowBuildTag(), false);

        Harness.section("4. the TWS folder remembers where you were (jc-10)");
        /* jc-8 pinned this to a fixed folder; jc-10 restores the pre-jc-8 memory at Jeremy's
         * request. The setter is back, and the fallbacks below are unchanged from jc-8. */
        check("setTWSPath() exists again on SuccPaths", hasMethod("setTWSPath"));
        check("setLevelsetFolderPath() is still there", hasMethod("setLevelsetFolderPath"));

        Path d4r = freshDir("twsroundtrip");
        SuccPaths p4r = SuccPaths.load(d4r.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile());
        Path chosen = d4r.resolve("tws").resolve("CCLP5-MS");
        p4r.setTWSPath(chosen.toString());
        eq("the folder just used is read back", p4r.getTWSPath(), chosen.toString());
        eq("and survives a reload from disk",
           SuccPaths.load(d4r.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile()).getTWSPath(),
           chosen.toString());
        check("it is stored RELATIVE to the CC folder, so the file stays portable",
              Files.readString(d4r.resolve(SuccPaths.SETTINGS_FILE_NAME), StandardCharsets.UTF_8)
                   .contains("TWS = tws\\CCLP5-MS\n"));
        /* The canceled-dialog crash jc-8 retired must stay retired: MenuBar guards its callers,
         * and the setter refuses null/blank rather than blanking a good stored value. */
        p4r.setTWSPath(null);
        eq("setTWSPath(null) is ignored, not destructive", p4r.getTWSPath(), chosen.toString());
        p4r.setTWSPath("   ");
        eq("setTWSPath(blank) is ignored too", p4r.getTWSPath(), chosen.toString());
        /* Picking a folder OUTSIDE the CC folder has to be stored absolute -- there is no portable
         * way to express it. Restoring the memory makes this reachable in normal use (jc-8's fixed
         * folder never went there), so it is tested rather than assumed. */
        Path outside = freshDir("twsoutside").resolve("SomeOtherPlace");
        Files.createDirectories(outside);
        p4r.setTWSPath(outside.toString());
        eq("a folder outside the CC folder round-trips", p4r.getTWSPath(), outside.toString());
        check("and is stored as an absolute path, not a broken relative one",
              Files.readString(d4r.resolve(SuccPaths.SETTINGS_FILE_NAME), StandardCharsets.UTF_8)
                   .contains("TWS = " + outside));
        Path d4 = freshDir("tws");
        SuccPaths p4 = SuccPaths.load(settingsWith(d4, "[Paths]", "TWS = tws\\Walls_of_CCLP2-MS"));
        eq("a hand-written subfolder IS honored",
           p4.getTWSPath(), d4.resolve("tws").resolve("Walls_of_CCLP2-MS").toString());
        Path d4b = freshDir("twsblank");
        eq("a blank value falls back to tws",
           SuccPaths.load(settingsWith(d4b, "[Paths]", "TWS = ")).getTWSPath(),
           d4b.resolve("tws").toString());
        Path d4c = freshDir("twsabsent");
        eq("an absent key falls back to tws",
           SuccPaths.load(settingsWith(d4c, "[Paths]", "succ = succsave")).getTWSPath(),
           d4c.resolve("tws").toString());
        Path d4d = freshDir("twsabs");
        eq("a legacy absolute value still resolves",
           SuccPaths.load(settingsWith(d4d, "[Paths]", "TWS = C:\\somewhere\\else")).getTWSPath(),
           "C:\\somewhere\\else");

        Harness.section("5. an unrelated setting change preserves TWS and ShowBuildTag");
        /* The TWS value here is deliberately a NON-DEFAULT subfolder. Using "tws" would let a bug
         * that resets TWS to the default pass silently, and "a hand-written value survives a
         * program-triggered whole-file rewrite" IS the requirement. */
        Path d5 = freshDir("rewrite");
        File f5 = settingsWith(d5, "[Paths]", "Levelset = data", "TWS = tws\\Walls_of_CCLP2-MS",
                                    "succ = succsave",
                                    "[Graphics]", "TilesheetNum = 2", "ShowBuildTag = true");
        SuccPaths p5 = SuccPaths.load(f5);
        p5.setTileSizes(new int[]{22, 22});      // triggers a whole-file rewrite
        String after = Files.readString(f5.toPath(), StandardCharsets.UTF_8);
        check("a hand-written TWS subfolder survives the rewrite verbatim",
              after.contains("TWS = tws\\Walls_of_CCLP2-MS\n"));
        eq("and still resolves to that subfolder",
           SuccPaths.load(f5).getTWSPath(), d5.resolve("tws").resolve("Walls_of_CCLP2-MS").toString());
        check("ShowBuildTag survived as true", after.contains("ShowBuildTag = true"));
        check("the new tile size was written", after.contains("TileWidth = 22"));
        eq("and re-reads as ON", SuccPaths.load(f5).getShowBuildTag(), true);

        Harness.section("6. '1' is normalized to 'true' on the next write, staying ON");
        Path d6 = freshDir("normalize");
        File f6 = settingsWith(d6, "[Graphics]", "ShowBuildTag = 1");
        SuccPaths p6 = SuccPaths.load(f6);
        eq("reads ON", p6.getShowBuildTag(), true);
        p6.setMSTilesetNum(3);
        check("written back as 'ShowBuildTag = true'",
              Files.readString(f6.toPath(), StandardCharsets.UTF_8).contains("ShowBuildTag = true"));
        eq("still ON after the rewrite", SuccPaths.load(f6).getShowBuildTag(), true);

        Harness.section("7. an old settings.ini is ignored, not read");
        Path d7 = freshDir("legacy");
        Files.writeString(d7.resolve("settings.ini"),
                "[Graphics]\nShowBuildTag = true\nTilesheetNum = 7", StandardCharsets.UTF_8);
        SuccPaths p7 = SuccPaths.load(d7.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile());
        eq("the legacy file's build tag is NOT picked up", p7.getShowBuildTag(), false);
        eq("nor its tilesheet", p7.getMSTilesetNum(), 0);
        check("the legacy file is left untouched on disk", Files.exists(d7.resolve("settings.ini")));

        Harness.section("8. the jc-4/jc-5 protections still hold");
        Path d8 = freshDir("damaged");
        File f8 = d8.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(f8.toPath(), "this is not an ini file at all", StandardCharsets.UTF_8);
        SuccPaths p8 = SuccPaths.load(f8);
        check("a damaged file produces a load warning", p8.getLoadWarning() != null);
        check("and is NOT overwritten", !p8.isPersisting());
        eq("the damaged file survives byte for byte",
           Files.readString(f8.toPath(), StandardCharsets.UTF_8), "this is not an ini file at all");
        Path d8b = freshDir("existing");
        File f8b = settingsWith(d8b, "[Paths]", "Levelset = data");
        try {
            SuccPaths.createSettingsFile(f8b);
            check("createSettingsFile refuses to clobber an existing file", false);
        } catch (IOException expected) {
            check("createSettingsFile refuses to clobber an existing file", true);
        }
        eq("that file is unchanged", Files.readString(f8b.toPath(), StandardCharsets.UTF_8),
           "[Paths]\nLevelset = data");
        Path d8c = freshDir("bom");
        File f8c = d8c.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(f8c.toPath(), "\uFEFF[Graphics]\nShowBuildTag = true", StandardCharsets.UTF_8);
        eq("a UTF-8 BOM is still stripped", SuccPaths.load(f8c).getShowBuildTag(), true);

        Harness.section("9. byte-for-byte stability of the settings file");
        /* The jc-4 constraint: the rendered layout must stay byte-compatible, so upgrading does not
         * rewrite anyone's file gratuitously. Every other assertion here uses contains(); this one
         * uses equality, which is the only way that constraint can actually be tested. */
        Path d9 = freshDir("bytes");
        File f9 = d9.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        SuccPaths p9 = SuccPaths.load(f9);                       // creates the stock file
        byte[] before9 = Files.readAllBytes(f9.toPath());
        p9.setTileSizes(new int[]{20, 20});                      // a NO-OP write of the same values
        byte[] after9 = Files.readAllBytes(f9.toPath());
        check("a no-op setting write leaves the file byte-identical",
              java.util.Arrays.equals(before9, after9));
        String stock9 = new String(before9, StandardCharsets.UTF_8);
        check("section headers end with the platform separator",
              stock9.contains("[Paths]" + System.lineSeparator()));
        check("key lines end with a bare LF",
              stock9.contains("TWS = tws\n") && !stock9.contains("TWS = tws\r\n"));
        check("no BOM", before9.length > 0 && before9[0] != (byte) 0xEF);

        Harness.section("10. the shipped jar really does call setTWSPath again (jc-10)");
        /* Reflection on SuccPaths (test 4) only proves the method exists. The thing that actually
         * delivers the behavior is the CALLER in MenuBar -- and build.ps1 is a SPLICE build, so if
         * MenuBar were not recompiled the jar would keep the jc-9 class that never calls it and
         * the feature would silently not work. Nothing else here would catch that. Scan every
         * .class in the built jar for the method-name constant. */
        String jarPath = System.getProperty("supercc.jar");
        if (jarPath == null || !new File(jarPath).exists()) { skip("no -Dsupercc.jar given"); }
        else {
            int scanned = 0;
            java.util.List<String> hits = new java.util.ArrayList<>();
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarPath)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry ze = en.nextElement();
                    if (!ze.getName().endsWith(".class")) continue;
                    scanned++;
                    byte[] raw = zf.getInputStream(ze).readAllBytes();
                    if (new String(raw, StandardCharsets.ISO_8859_1).contains("setTWSPath")) hits.add(ze.getName());
                }
            }
            check("scanned every class in the jar (" + scanned + " classes)", scanned > 150);
            check("at least one class references setTWSPath -- found in " + hits, !hits.isEmpty());
            boolean menuBar = false;
            for (String h : hits) if (h.startsWith("graphics/MenuBar")) menuBar = true;
            check("and MenuBar is one of them, so the spliced jar carries the new callers", menuBar);
        }

        Harness.section("11. AlwaysOpenInMS (jc-9)");
        Path d11 = freshDir("ms");
        SuccPaths p11 = SuccPaths.load(d11.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile());
        eq("OFF on a fresh install", p11.getAlwaysOpenInMS(), false);
        String stock11 = Files.readString(d11.resolve(SuccPaths.SETTINGS_FILE_NAME), StandardCharsets.UTF_8);
        check("the stock file has an [Emulation] section", stock11.contains("[Emulation]"));
        check("with AlwaysOpenInMS = false", stock11.contains("AlwaysOpenInMS = false"));
        check("and still no trailing newline", !stock11.endsWith("\n"));
        for (String v : new String[]{"true", "TRUE", "1", " true "}) {
            Path d = freshDir("mson");
            eq("ON  for [" + v + "]",
               SuccPaths.load(settingsWith(d, "[Emulation]", "AlwaysOpenInMS = " + v)).getAlwaysOpenInMS(), true);
        }
        for (String v : new String[]{"false", "0", "yes", "on", "", "MS", "true!"}) {
            Path d = freshDir("msoff");
            eq("OFF for [" + v + "]",
               SuccPaths.load(settingsWith(d, "[Emulation]", "AlwaysOpenInMS = " + v)).getAlwaysOpenInMS(), false);
        }
        Path d11b = freshDir("mswrongsection");
        eq("OFF when the key sits under [Graphics]",
           SuccPaths.load(settingsWith(d11b, "[Graphics]", "AlwaysOpenInMS = true")).getAlwaysOpenInMS(), false);
        Path d11c = freshDir("msnospace");
        eq("OFF for the no-space typo",
           SuccPaths.load(settingsWith(d11c, "[Emulation]", "AlwaysOpenInMS=true")).getAlwaysOpenInMS(), false);
        // A jc-8 file has no [Emulation] section at all: it must read OFF and then GAIN the section.
        Path d11d = freshDir("jc8file");
        File f11d = settingsWith(d11d, "[Paths]", "TWS = tws", "[Graphics]", "TilesheetNum = 2",
                                 "ShowBuildTag = true");
        SuccPaths p11d = SuccPaths.load(f11d);
        eq("a jc-8 file (no [Emulation]) reads OFF", p11d.getAlwaysOpenInMS(), false);
        p11d.setMSTilesetNum(2);
        String up = Files.readString(f11d.toPath(), StandardCharsets.UTF_8);
        check("and gains the section on the next write", up.contains("[Emulation]")
                                                      && up.contains("AlwaysOpenInMS = false"));
        check("without disturbing the older settings",
              up.contains("TWS = tws\n") && up.contains("ShowBuildTag = true"));

        Harness.section("12. the .dat signature MO3 actually carries");
        String mo3 = System.getProperty("supercc.mo3");
        if (mo3 == null || !new File(mo3).exists()) { skip("no -Dsupercc.mo3 given (MO3.dat is not in this repo)"); }
        else {
            byte[] sig = new byte[4];
            try (java.io.InputStream in = new java.io.FileInputStream(mo3)) { in.read(sig); }
            int signature = (sig[0] & 0xFF) | ((sig[1] & 0xFF) << 8) | ((sig[2] & 0xFF) << 16) | ((sig[3] & 0xFF) << 24);
            System.out.printf("  MO3.dat signature = 0x%08X%n", signature);
            eq("it is the Lynx signature (which is why it opened in Lynx)", signature, 0x0102AAAC);
            io.DatParser parser = new io.DatParser(new File(mo3));
            eq("DatParser reports LYNX for it", parser.getRuleset(), game.Ruleset.LYNX);
        }

        Harness.section("13. the custom tileset's images (upstream d78f6f3)");
        /* Upstream stores the two image paths ABSOLUTE. This file is Dropbox-synced between two
         * machines whose user folders differ, which is why Levelset and TWS are stored relative to
         * the CC folder (jc-5), and jc-18 to jc-21 stored the custom images the same way. They also
         * read an empty path as "none chosen", and kept "custom" as a switch of its own beside a
         * built-in TilesheetNum, where upstream stores it as tileset number 5.
         *
         * ⚠ UPSTREAM BASELINE (from jc-22): all REVERTED (FORK.md, "The upstream baseline";
         * BASELINE-ISSUES.md UB-16, UB-17, UB-19), and coming back one fix at a time (FORK.md, "The
         * fix layer"). MOD (Jeremy, fix layer, UB-19): empty reads as "none chosen" again from jc-33
         * (upstream's getter returned null only for an ABSENT key, and this fork's settings structure
         * seeds every absent key with "", so on jc-22 to jc-32 it returned the pair, empty or not;
         * those three were pinned there). The paths are stored relative to the CC folder again from
         * jc-38 (UB-17; pinned at upstream's absolute on jc-22 to jc-37). Custom's own switch is back from jc-37 (UB-16), with its checks in 13b. To re-apply all of them at once (the fix layer, FORK.md,
         * takes them back one fix at a time instead): `git revert` the commit "Upstream baseline:
         * revert the custom-tileset fixes"; it holds this change too. */
        Path d13 = freshDir("customtiles");
        File f13 = d13.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        SuccPaths p13 = SuccPaths.load(f13);
        String stock13 = Files.readString(f13.toPath(), StandardCharsets.UTF_8);
        check("the stock file lists both keys, empty",
              stock13.contains("CustomBaseImage = \n") && stock13.contains("CustomOverlayImage = \n"));
        check("and under [Graphics], where the getter looks",
              stock13.indexOf("CustomBaseImage") > stock13.indexOf("[Graphics]")
              && stock13.indexOf("CustomBaseImage") < stock13.indexOf("[Emulation]"));
        Harness.eq("none chosen reads as null", chosen(p13.getCustomTilesetImages()), "null");
        Path inBase = d13.resolve("tilesets").resolve("base.png"), inOverlay = d13.resolve("tilesets").resolve("overlay.png");
        p13.setCustomTilesetImages(inBase.toString(), inOverlay.toString());
        String file13 = Files.readString(f13.toPath(), StandardCharsets.UTF_8);
        // MOD (Jeremy, fix layer, UB-17): relative, as Levelset and TWS are (jc-5); jc-22 to jc-37 stored
        // them absolute, as upstream does, and this was pinned there.
        String sep = File.separator;
        check("images inside the CC folder are stored RELATIVE to it, so the file works on both machines",
              file13.contains("CustomBaseImage = tilesets" + sep + "base.png\n") && file13.contains("CustomOverlayImage = tilesets" + sep + "overlay.png\n"));
        String[] reread13 = SuccPaths.load(f13).getCustomTilesetImages();
        check("and read back, after a reload, as the paths chosen",
              reread13 != null && reread13[0].equals(inBase.toString()) && reread13[1].equals(inOverlay.toString()));
        Path outside13 = freshDir("customtiles-out").resolve("art.png");
        p13.setCustomTilesetImages(outside13.toString(), inOverlay.toString());
        String mixed13 = Files.readString(f13.toPath(), StandardCharsets.UTF_8);
        check("an image outside the CC folder is stored absolute -- there is nothing to anchor it to -- and the one inside still relative",
              mixed13.contains("CustomBaseImage = " + outside13 + "\n") && mixed13.contains("CustomOverlayImage = tilesets" + sep + "overlay.png\n"));
        Harness.eq("and it reads back as it was chosen",
              java.util.Arrays.toString(SuccPaths.load(f13).getCustomTilesetImages()),
              java.util.Arrays.toString(new String[] {outside13.toString(), inOverlay.toString()}));
        // The point of it: a relative path written on the other computer resolves against THIS one's folder.
        Path d13o = freshDir("customtiles-other");
        Harness.eq("a relative path (as the other computer writes it) reads as a picture in THIS computer's folder",
              java.util.Arrays.toString(SuccPaths.load(settingsWith(d13o, "[Graphics]", "CustomBaseImage = tilesets" + sep + "b.png",
                    "CustomOverlayImage = tilesets" + sep + "o.png")).getCustomTilesetImages()),
              java.util.Arrays.toString(new String[] {d13o.resolve("tilesets").resolve("b.png").toString(), d13o.resolve("tilesets").resolve("o.png").toString()}));
        // A value already stored absolute (jc-22 to jc-37 wrote them so) still reads as it is (jc-5's
        // rule); and, from the jc-38 review, one INSIDE this folder is put in the relative form at
        // load, so the next write -- every launch makes one -- saves it the way the other computer
        // can read, without custom being chosen again. One outside this folder is left alone.
        Path d13a = freshDir("customtiles-legacy");
        Path elsewhere13 = freshDir("customtiles-elsewhere").resolve("far.png");
        File f13a = settingsWith(d13a, "[Graphics]", "CustomBaseImage = " + d13a.resolve("tilesets").resolve("old.png"),
              "CustomOverlayImage = " + elsewhere13);
        SuccPaths p13a = SuccPaths.load(f13a);
        Harness.eq("pictures stored absolute by an earlier build still read as they are",
              java.util.Arrays.toString(p13a.getCustomTilesetImages()),
              java.util.Arrays.toString(new String[] {d13a.resolve("tilesets").resolve("old.png").toString(), elsewhere13.toString()}));
        p13a.setTileSizes(new int[] {20, 20});   // a write of something else, as every launch makes
        java.util.List<String> legacy13 = lines(f13a);
        check("and at the next write the one inside this folder is saved relative, the one outside it left exactly as it was ("
              + legacy13.stream().filter(l -> l.startsWith("Custom")).collect(java.util.stream.Collectors.joining("; ")) + ")",
              legacy13.contains("CustomBaseImage = tilesets" + sep + "old.png") && legacy13.contains("CustomOverlayImage = " + elsewhere13));
        Path d13b = freshDir("customtiles-half");
        Harness.eq("only one of the two given reads as null (both are needed)",
              chosen(SuccPaths.load(settingsWith(d13b, "[Graphics]", "CustomBaseImage = a.png", "CustomOverlayImage = ")).getCustomTilesetImages()),
              "null");
        Path d13c = freshDir("customtiles-old");
        File f13c = settingsWith(d13c, "[Graphics]", "TilesheetNum = 2");
        SuccPaths p13c = SuccPaths.load(f13c);
        Harness.eq("a file from before jc-18 (no keys) reads as none chosen", chosen(p13c.getCustomTilesetImages()), "null");
        p13c.setMSTilesetNum(3);
        String up13 = Files.readString(f13c.toPath(), StandardCharsets.UTF_8);
        check("and gains both keys, empty, on its next write",
              up13.contains("CustomBaseImage = \n") && up13.contains("CustomOverlayImage = \n"));

        Harness.section("13b. custom as a switch of its own, beside a built-in TilesheetNum (UB-16)");
        /* MOD (Jeremy, fix layer, UB-16): upstream saves a chosen custom tileset as tileset number 5,
         * which builds before jc-18 do not have. jc-18 kept custom as a switch per ruleset
         * (CustomTileset, LynxCustomTileset) and TilesheetNum the last BUILT-IN choice, which every
         * build can read -- back since jc-37, with jc-18's checks. And, the maintainer's decision
         * (2026-11-16), not jc-18's: a 5 stored by jc-22 to jc-36 is read as that ruleset's switch on,
         * with the stock built-in number beside it, and saved that way at the next write. The new
         * methods are reached by reflection, so this file still compiles against jc-36's jar, where
         * these checks then fail as "no getCustomTileset". */
        Path d13s = freshDir("customswitch");
        File f13s = d13s.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        SuccPaths p13s = SuccPaths.load(f13s);
        java.util.List<String> stock13s = lines(f13s);
        check("the stock file has both switches, off, as whole lines",
              stock13s.contains("CustomTileset = false") && stock13s.contains("LynxCustomTileset = false"));
        String text13s = Files.readString(f13s.toPath(), StandardCharsets.UTF_8);
        check("under [Graphics], where the getter looks",
              text13s.indexOf("\nCustomTileset = ") > text13s.indexOf("[Graphics]") && text13s.indexOf("\nCustomTileset = ") < text13s.indexOf("[Emulation]"));
        Harness.eq("a fresh file: custom off for both rulesets", customSwitch(p13s, false) + " " + customSwitch(p13s, true), "off off");
        Harness.eq("choosing a built-in tileset for MS saves its number, custom off",
              call(p13s, "setBuiltInTileset", false, 3) + "; " + customSwitch(SuccPaths.load(f13s), false) + " " + SuccPaths.load(f13s).getMSTilesetNum(),
              "done; off 3");
        Path cBase13 = d13s.resolve("base.png"), cOver13 = d13s.resolve("overlay.png");
        Harness.eq("choosing custom for MS turns its switch on, the pictures saved with it",
              call(p13s, "setCustomTileset", false, cBase13.toString(), cOver13.toString()) + "; " + customSwitch(SuccPaths.load(f13s), false)
              + " " + java.util.Arrays.toString(SuccPaths.load(f13s).getCustomTilesetImages()),
              "done; on " + java.util.Arrays.toString(new String[] {cBase13.toString(), cOver13.toString()}));
        // MOD (Jeremy, fix layer, UB-17): this setter stores the pictures relative too.
        check("and stores the two pictures, inside the CC folder, relative to it (" + lines(f13s).stream()
              .filter(l -> l.startsWith("Custom")).collect(java.util.stream.Collectors.joining("; ")) + ")",
              lines(f13s).contains("CustomBaseImage = base.png") && lines(f13s).contains("CustomOverlayImage = overlay.png"));
        Harness.eq("and leaves TilesheetNum the built-in choice, and Lynx alone",
              SuccPaths.load(f13s).getMSTilesetNum() + " " + customSwitch(SuccPaths.load(f13s), true), "3 off");
        Harness.eq("choosing custom under Lynx turns the Lynx switch on",
              call(p13s, "setCustomTileset", true, cBase13.toString(), cOver13.toString()) + "; " + customSwitch(SuccPaths.load(f13s), true), "done; on");
        // WHOLE lines, not substrings: "TilesheetNum = 0" is inside "LynxTilesheetNum = 0" (jc-18 review).
        java.util.List<String> both13s = lines(f13s);
        check("with custom on for both, TilesheetNum and LynxTilesheetNum are still built-in numbers, never 5",
              both13s.contains("TilesheetNum = 3") && both13s.contains("LynxTilesheetNum = 0")
              && both13s.contains("CustomTileset = true") && both13s.contains("LynxCustomTileset = true"));
        Harness.eq("choosing a built-in tileset for MS then turns MS custom off, in one write, Lynx left on",
              call(p13s, "setBuiltInTileset", false, 2) + "; " + customSwitch(SuccPaths.load(f13s), false) + " "
              + SuccPaths.load(f13s).getMSTilesetNum() + " " + customSwitch(SuccPaths.load(f13s), true), "done; off 2 on");
        Harness.eq("an unrecognized switch value reads OFF",
              customSwitch(SuccPaths.load(settingsWith(freshDir("ct-yes"), "[Graphics]", "CustomTileset = yes")), false), "off");
        Harness.eq("and a switch under the wrong section is another key",
              customSwitch(SuccPaths.load(settingsWith(freshDir("ct-wrong"), "[Paths]", "CustomTileset = true")), false), "off");
        // Written back through the getter's own predicate (CLAUDE.md section 5), so the file can
        // never say ON while the program reads OFF.
        File f13y = settingsWith(freshDir("ct-yes-rw"), "[Graphics]", "CustomTileset = yes", "LynxCustomTileset = on");
        SuccPaths.load(f13y).setTileSizes(new int[] {20, 20});   // any write
        java.util.List<String> written13y = lines(f13y);
        check("and both switches are written back as what they read, false (" + written13y.stream()
              .filter(l -> l.contains("CustomTileset")).collect(java.util.stream.Collectors.joining("; ")) + ")",
              written13y.contains("CustomTileset = false") && written13y.contains("LynxCustomTileset = false"));
        // The migration: a 5 as jc-22 to jc-36 stored it.
        int five = graphics.TileSheet.CUSTOM.ordinal();
        File f13m = settingsWith(freshDir("ct-five"), "[Graphics]", "TilesheetNum = " + five, "LynxTilesheetNum = 1",
              "CustomBaseImage = " + cBase13, "CustomOverlayImage = " + cOver13);
        SuccPaths p13m = SuccPaths.load(f13m);
        Harness.eq("a stored TilesheetNum = " + five + " (custom, as jc-22 to jc-36 saved it) reads as MS custom ON, with the stock built-in number beside it",
              customSwitch(p13m, false) + " " + p13m.getMSTilesetNum(), "on 0");
        Harness.eq("and leaves Lynx, and the pictures, alone",
              customSwitch(p13m, true) + " " + p13m.getLynxTilesetNum() + " " + java.util.Arrays.toString(p13m.getCustomTilesetImages()),
              "off 1 " + java.util.Arrays.toString(new String[] {cBase13.toString(), cOver13.toString()}));
        p13m.setTileSizes(new int[] {20, 20});   // any write
        java.util.List<String> after13m = lines(f13m);
        check("and its next write saves it so: TilesheetNum = 0 and CustomTileset = true, no 5 left (" + after13m + ")",
              after13m.contains("TilesheetNum = 0") && after13m.contains("CustomTileset = true") && !after13m.contains("TilesheetNum = " + five));
        Harness.eq("a stored LynxTilesheetNum = " + five + " reads as Lynx custom ON, with the stock built-in number",
              lynxMigrated(five), "on 0, MS off 2");
        Harness.eq("a stored 5 wins over CustomTileset = false beside it (only a hand edit makes that pair)",
              customSwitch(SuccPaths.load(settingsWith(freshDir("ct-five-off"), "[Graphics]", "TilesheetNum = " + five, "CustomTileset = false")), false), "on");
        Harness.eq("any other number is left as it is, custom off",
              customSwitch(SuccPaths.load(settingsWith(freshDir("ct-four"), "[Graphics]", "TilesheetNum = 4")), false) + " "
              + SuccPaths.load(settingsWith(freshDir("ct-four2"), "[Graphics]", "TilesheetNum = 4")).getMSTilesetNum(), "off 4");

        Harness.section("14. AutoDiagonals (upstream 5f928e0), in [Controls]");
        /* Read through optedIn() and written back as the value read, between UpRight and Rewind, so
         * the file and the program cannot disagree. MOD (Jeremy, fix layer, UB-28): from jc-23 to
         * jc-44 it was exactly as upstream wrote it -- read with Boolean.parseBoolean, so only "true"
         * was ON, and written back raw, so "1" or "yes" stayed in the file while the switch was off
         * -- and the two checks marked UB-28 below were pins. Back from jc-45 (jc-18's fix), they
         * are exact checks again. Section 11's pattern otherwise: on, off, the wrong section, the
         * no-space typo, and Tools > Controls writing keys and switch together. */
        Path d14 = freshDir("autodiag");
        File f14 = d14.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        SuccPaths p14 = SuccPaths.load(f14);
        String stock14 = Files.readString(f14.toPath(), StandardCharsets.UTF_8);
        check("the stock file says AutoDiagonals = false", stock14.contains("AutoDiagonals = false\n"));
        check("under [Controls]", stock14.indexOf("AutoDiagonals") > stock14.indexOf("[Controls]")
                                  && stock14.indexOf("AutoDiagonals") < stock14.indexOf("[Graphics]"));
        eq("absent from the stock file's meaning: OFF", p14.getControls().autoDiagonals, false);
        eq("true is ON", autoDiagonals("ad-true", "AutoDiagonals = true"), true);
        eq("TRUE (any casing) is ON", autoDiagonals("ad-TRUE", "AutoDiagonals = TRUE"), true);
        eq("1 is ON, as for every other switch here (UB-28)", autoDiagonals("ad-1", "AutoDiagonals = 1"), true);
        eq("yes is OFF (a typo cannot switch it on)", autoDiagonals("ad-yes", "AutoDiagonals = yes"), false);
        eq("under [Graphics] it is another key, and OFF", SuccPaths.load(settingsWith(freshDir("ad-wrong"), "[Graphics]", "AutoDiagonals = true")).getControls().autoDiagonals, false);
        eq("AutoDiagonals=true (no space) is OFF", autoDiagonals("ad-nospace", "AutoDiagonals=true"), false);
        File f14y = settingsWith(freshDir("ad-yes-rw"), "[Controls]", "AutoDiagonals = yes");
        SuccPaths p14y = SuccPaths.load(f14y);
        p14y.setMSTilesetNum(1);
        eq("a value read as OFF is written back as false, so the file says what the program does (UB-28)",
              lineOf(Files.readString(f14y.toPath(), StandardCharsets.UTF_8), "AutoDiagonals"), "AutoDiagonals = false");
        /* ...and a value read as ON is written back as true: "1" becomes "true". A render that wrote
         * every switch as false, or skipped the line, would pass the check above. */
        File f14one = settingsWith(freshDir("ad-1-rw"), "[Controls]", "AutoDiagonals = 1");
        SuccPaths.load(f14one).setMSTilesetNum(1);
        String rw14one = Files.readString(f14one.toPath(), StandardCharsets.UTF_8);
        eq("a value read as ON (1) is written back as true", lineOf(rw14one, "AutoDiagonals"), "AutoDiagonals = true");
        check("still in upstream's place, between UpRight and Rewind, and only once",
              rw14one.indexOf("UpRight = ") < rw14one.indexOf("AutoDiagonals = ")
              && rw14one.indexOf("AutoDiagonals = ") < rw14one.indexOf("Rewind = ")
              && rw14one.indexOf("AutoDiagonals") == rw14one.lastIndexOf("AutoDiagonals"));
        eq("and reads back ON after a reload", SuccPaths.load(f14one).getControls().autoDiagonals, true);
        SuccPaths.Controls ctl = p14.getControls();
        eq("getControls() carries the switch", ctl.autoDiagonals, false);
        ctl.autoDiagonals = true;
        ctl.keys[0] = java.awt.event.KeyEvent.VK_W;
        p14.setControls(ctl);
        String written14 = Files.readString(f14.toPath(), StandardCharsets.UTF_8);
        check("setControls() writes the switch and the keys together",
              written14.contains("AutoDiagonals = true\n") && written14.contains("Up = " + java.awt.event.KeyEvent.VK_W + "\n"));
        eq("and it reads back ON after a reload", SuccPaths.load(f14).getControls().autoDiagonals, true);
        // getControls() repairs a key code that does not parse by rewriting the file. Upstream's key
        // listener asks it on every key press; launch has already repaired any such code by then (the
        // listener's constructor asks it first), so in practice that per-press call does not write.
        File f14g = settingsWith(freshDir("ad-garbled"), "[Controls]", "Up = x", "AutoDiagonals = true");
        SuccPaths p14g = SuccPaths.load(f14g);
        byte[] before14 = Files.readAllBytes(f14g.toPath());
        p14g.getControls();
        byte[] repaired14 = Files.readAllBytes(f14g.toPath());
        check("getControls() repairs a garbled key code by rewriting the file", !java.util.Arrays.equals(before14, repaired14));
        p14g.getControls();
        check("and once repaired, asking again does not write", java.util.Arrays.equals(repaired14, Files.readAllBytes(f14g.toPath())));

    }

    /** AutoDiagonals as the program reads it from a settings file holding `line` under [Controls]. */
    private static boolean autoDiagonals(String dir, String line) throws IOException {
        return SuccPaths.load(settingsWith(freshDir(dir), "[Controls]", line)).getControls().autoDiagonals;
    }

    /** The first line of `text` that starts with `key`, or "(no such line)". */
    private static String lineOf(String text, String key) {
        for (String l : text.split("\n")) if (l.startsWith(key)) return l.strip();
        return "(no such line)";
    }

    private static boolean hasMethod(String name) {
        for (Method m : SuccPaths.class.getMethods()) if (m.getName().equals(name)) return true;
        return false;
    }
}
