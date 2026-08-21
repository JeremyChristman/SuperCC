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

    private static int pass = 0, fail = 0, skipped = 0;

    private static void skip(String why) {
        skipped++; System.out.println("  SKIP  " + why);
    }

    private static void check(String what, boolean ok) {
        if (ok) { pass++; System.out.println("  PASS  " + what); }
        else    { fail++; System.out.println("  FAIL  " + what); }
    }

    private static void eq(String what, Object actual, Object expected) {
        boolean ok = (expected == null) ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("  PASS  " + what); }
        else    { fail++; System.out.println("  FAIL  " + what + "\n          expected: " + expected
                                            + "\n          actual:   " + actual); }
    }

    private static Path freshDir(String name) throws IOException {
        Path d = Files.createTempDirectory("jc8-" + name + "-");
        d.toFile().deleteOnExit();
        return d;
    }

    /** Writes a settings file containing exactly the given lines. */
    private static File settingsWith(Path dir, String... lines) throws IOException {
        File f = dir.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(f.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        return f;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("\n== 1. the settings file is named succ_settings.ini ==");
        eq("SETTINGS_FILE_NAME", SuccPaths.SETTINGS_FILE_NAME, "succ_settings.ini");
        eq("DEFAULT_TWS_PATH", SuccPaths.DEFAULT_TWS_PATH, "tws");

        System.out.println("\n== 2. a fresh install creates a complete stock file ==");
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

        System.out.println("\n== 3. ShowBuildTag is strictly opt-in ==");
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

        System.out.println("\n== 4. the TWS folder remembers where you were (jc-10) ==");
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

        System.out.println("\n== 5. an unrelated setting change preserves TWS and ShowBuildTag ==");
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

        System.out.println("\n== 6. '1' is normalized to 'true' on the next write, staying ON ==");
        Path d6 = freshDir("normalize");
        File f6 = settingsWith(d6, "[Graphics]", "ShowBuildTag = 1");
        SuccPaths p6 = SuccPaths.load(f6);
        eq("reads ON", p6.getShowBuildTag(), true);
        p6.setMSTilesetNum(3);
        check("written back as 'ShowBuildTag = true'",
              Files.readString(f6.toPath(), StandardCharsets.UTF_8).contains("ShowBuildTag = true"));
        eq("still ON after the rewrite", SuccPaths.load(f6).getShowBuildTag(), true);

        System.out.println("\n== 7. an old settings.ini is ignored, not read ==");
        Path d7 = freshDir("legacy");
        Files.writeString(d7.resolve("settings.ini"),
                "[Graphics]\nShowBuildTag = true\nTilesheetNum = 7", StandardCharsets.UTF_8);
        SuccPaths p7 = SuccPaths.load(d7.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile());
        eq("the legacy file's build tag is NOT picked up", p7.getShowBuildTag(), false);
        eq("nor its tilesheet", p7.getMSTilesetNum(), 0);
        check("the legacy file is left untouched on disk", Files.exists(d7.resolve("settings.ini")));

        System.out.println("\n== 8. the jc-4/jc-5 protections still hold ==");
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

        System.out.println("\n== 9. byte-for-byte stability of the settings file ==");
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

        System.out.println("\n== 10. the shipped jar really does call setTWSPath again (jc-10) ==");
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

        System.out.println("\n== 11. AlwaysOpenInMS (jc-9) ==");
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

        System.out.println("\n== 12. the .dat signature MO3 actually carries ==");
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

        System.out.println("\n== results ==");
        System.out.println("  " + pass + " passed, " + fail + " failed, " + skipped + " skipped");
        if (fail > 0) System.exit(1);
    }

    private static boolean hasMethod(String name) {
        for (Method m : SuccPaths.class.getMethods()) if (m.getName().equals(name)) return true;
        return false;
    }
}
