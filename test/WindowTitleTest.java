import emulator.SuperCC;
import io.SuccPaths;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The window title, and the three switches that decide what is in it (jc-14).
 *
 *     SuperCC [jc-N] - &lt;pack&gt; - &lt;level&gt;
 *             \_____/   \____/   \_____/
 *          ShowBuildTag  ShowLevelPack  ShowLevelName
 *
 * WHY THIS IS WORTH A TEST OF ITS OWN
 * ------------------------------------
 * Three independent switches over two separators is exactly the shape that produces a dangling
 * " - " or a doubled " -  - " in some combination nobody tried. There are only eight combinations,
 * so there is no excuse for guessing: section 1 walks all of them.
 *
 * composeWindowTitle() is static and pure -- no settings, no Level, no Gui -- precisely so that can
 * be done without building a level or opening a window. The settings-aware wrapper is four lines on
 * top of it.
 *
 * THE TWO NEW SWITCHES USE OPPOSITE PREDICATES, AND THAT IS DELIBERATE
 * ---------------------------------------------------------------------
 * ShowLevelPack is strictly opt-in, like ShowBuildTag: its shipped default is OFF, so blank, a typo
 * and anything unrecognized all mean OFF. ShowLevelName is the mirror: its default is ON, so only an
 * explicit "false" or "0" hides it. The case that separates them is an unrecognized VALUE -- a
 * settings file with no key at all is filled in by seedDefaults() before either predicate sees it,
 * so that one would come out right either way. Section 3 pins both directions and both cases.
 */
public class WindowTitleTest {

    public static void main(String[] args) {
        System.exit(Harness.run("WindowTitleTest", WindowTitleTest::body));
    }

    private static final String PACK  = "CCLP1";
    private static final String LEVEL = "Key Pyramid";

    private static String title(boolean tag, boolean pack, boolean name) {
        return SuperCC.composeWindowTitle(tag, pack, name, PACK, LEVEL, "");
    }

    private static void body() throws Exception {

        /* ================================================================
         * 1. All eight combinations, and the separator count for each
         * ================================================================
         * The dash count is the thing that goes wrong, so it is asserted as well as the string:
         * two separators only when both halves are shown, one when exactly one is, none when
         * neither. The build tag never contributes a separator -- it is a space, not a dash. */

        Harness.section("1. all eight switch combinations");

        String tagged = SuperCC.TITLE + " " + SuperCC.BUILD_TAG;

        Harness.eq("all three on",      title(true,  true,  true),  tagged + " - " + PACK + " - " + LEVEL);
        Harness.eq("tag off, both on",  title(false, true,  true),  SuperCC.TITLE + " - " + PACK + " - " + LEVEL);
        Harness.eq("pack off, name on", title(false, false, true),  SuperCC.TITLE + " - " + LEVEL);
        Harness.eq("pack on, name off", title(false, true,  false), SuperCC.TITLE + " - " + PACK);
        Harness.eq("both off",          title(false, false, false), SuperCC.TITLE);
        Harness.eq("tag on, both off",  title(true,  false, false), tagged);
        Harness.eq("tag on, pack only", title(true,  true,  false), tagged + " - " + PACK);
        Harness.eq("tag on, name only", title(true,  false, true),  tagged + " - " + LEVEL);

        /* Stated again as a count, because "no extra dashes" is the actual requirement and reading
         * it off eight expected strings is not the same as checking it. */
        for (boolean tag : new boolean[]{ false, true }) {
            Harness.eq("both halves shown gives exactly two separators (tag=" + tag + ")",
                       separators(title(tag, true, true)), 2);
            Harness.eq("pack only gives exactly one (tag=" + tag + ")",
                       separators(title(tag, true, false)), 1);
            Harness.eq("name only gives exactly one (tag=" + tag + ")",
                       separators(title(tag, false, true)), 1);
            Harness.eq("neither gives none (tag=" + tag + ")",
                       separators(title(tag, false, false)), 0);
        }

        /* And in none of the eight is there a dangling or doubled one. */
        for (boolean tag : new boolean[]{ false, true })
            for (boolean pack : new boolean[]{ false, true })
                for (boolean name : new boolean[]{ false, true }) {
                    String t = title(tag, pack, name);
                    Harness.check("no trailing separator: '" + t + "'",
                                  !t.endsWith("-") && !t.endsWith("- ") && !t.endsWith(" -"));
                    Harness.check("no doubled separator: '" + t + "'", !t.contains("-  -"));
                    Harness.check("always starts with the program name: '" + t + "'",
                                  t.startsWith(SuperCC.TITLE));
                }

        /* ================================================================
         * 2. An absent pack or level name is not shown, switch or no switch
         * ================================================================
         * A set with no title would otherwise produce a separator with nothing after it. Null and
         * empty both mean "nothing to show", so the switch being on is not sufficient. */

        Harness.section("2. an absent pack or level name");

        Harness.eq("a null pack contributes no separator",
                   SuperCC.composeWindowTitle(false, true, true, null, LEVEL, ""),
                   SuperCC.TITLE + " - " + LEVEL);
        Harness.eq("an empty pack contributes no separator",
                   SuperCC.composeWindowTitle(false, true, true, "", LEVEL, ""),
                   SuperCC.TITLE + " - " + LEVEL);
        Harness.eq("a null level name contributes no separator",
                   SuperCC.composeWindowTitle(false, true, true, PACK, null, ""),
                   SuperCC.TITLE + " - " + PACK);
        Harness.eq("an empty level name contributes no separator",
                   SuperCC.composeWindowTitle(false, true, true, PACK, "", ""),
                   SuperCC.TITLE + " - " + PACK);
        Harness.eq("both absent leaves the bare program name",
                   SuperCC.composeWindowTitle(false, true, true, null, null, ""),
                   SuperCC.TITLE);

        /* The read-only-settings marker rides on the very end, after everything, so it can never
         * land between a name and its separator. */
        String readOnly = "  [settings read-only]";
        Harness.eq("the read-only marker goes last, after both halves",
                   SuperCC.composeWindowTitle(false, true, true, PACK, LEVEL, readOnly),
                   SuperCC.TITLE + " - " + PACK + " - " + LEVEL + readOnly);
        Harness.eq("and still last when nothing else is shown",
                   SuperCC.composeWindowTitle(false, false, false, PACK, LEVEL, readOnly),
                   SuperCC.TITLE + readOnly);
        Harness.eq("a null suffix appends nothing rather than the text 'null'",
                   SuperCC.composeWindowTitle(false, false, false, PACK, LEVEL, null),
                   SuperCC.TITLE);

        /* ================================================================
         * 3. The settings, and the two OPPOSITE defaults
         * ================================================================
         * ShowLevelPack defaults OFF and ShowLevelName defaults ON, so they cannot share a
         * predicate. The upgrade case is the one that matters: a settings file written by jc-13 has
         * neither key and must still show the level name. */

        Harness.section("3. the two opposite defaults");

        SuccPaths fresh = SuccPaths.load(freshDir("default").resolve(SuccPaths.SETTINGS_FILE_NAME).toFile());
        Harness.check("a fresh settings file hides the level pack by default", !fresh.getShowLevelPack());
        Harness.check("and shows the level name by default", fresh.getShowLevelName());
        Harness.check("and hides the build tag, as every public build must", !fresh.getShowBuildTag());

        /* That combination IS the shipped default, so spell out the title it produces -- this is
         * the one a downloader sees, and it has to match what jc-13 showed. */
        Harness.eq("so a fresh install's title is the program name and the level, one separator",
                   SuperCC.composeWindowTitle(fresh.getShowBuildTag(), fresh.getShowLevelPack(),
                                              fresh.getShowLevelName(), PACK, LEVEL, ""),
                   SuperCC.TITLE + " - " + LEVEL);

        /* The headless constructor never builds a SuccPaths, and the retitle is reachable from it
         * -- Tile World's equivalent re-title crashed every batch run for exactly this reason. Its
         * fallbacks have to be the SHIPPED defaults (pack off, name on), not "everything off",
         * or a GUI-less instance would compose a title no real install ever shows. */
        SuperCC headless = new SuperCC(false);
        Harness.eq("a headless instance composes the shipped default title",
                   headless.windowTitle(PACK, LEVEL), SuperCC.TITLE + " - " + LEVEL);
        Harness.eq("and yields the bare name, not the text 'null', when both are absent",
                   headless.windowTitle(null, null), SuperCC.TITLE);

        /* A file that predates both keys entirely -- the jc-13 upgrade path. seedDefaults() is what
         * carries this one, not the predicate, but it is the case a real user will actually hit on
         * upgrade, so it is asserted rather than reasoned about. */
        SuccPaths upgraded = SuccPaths.load(settingsWith(freshDir("upgrade"),
                "[Graphics]", "TilesheetNum = 0", "ShowBuildTag = false"));
        Harness.check("a settings file with NEITHER key still shows the level name",
                      upgraded.getShowLevelName());
        Harness.check("and still hides the pack", !upgraded.getShowLevelPack());

        /* Explicit values in both directions, including the unrecognized ones the two predicates
         * deliberately resolve in opposite directions. */
        Harness.check("ShowLevelName = false hides it",   !nameValue("false").getShowLevelName());
        Harness.check("ShowLevelName = 0 hides it too",   !nameValue("0").getShowLevelName());
        Harness.check("ShowLevelName = FALSE hides it, case and all",
                      !nameValue("FALSE").getShowLevelName());
        Harness.check("ShowLevelName = true shows it",     nameValue("true").getShowLevelName());
        Harness.check("ShowLevelName = yes is not a recognized OFF, so it stays SHOWN",
                      nameValue("yes").getShowLevelName());
        Harness.check("ShowLevelPack = true shows it",     packValue("true").getShowLevelPack());
        Harness.check("ShowLevelPack = 1 shows it too",    packValue("1").getShowLevelPack());
        Harness.check("ShowLevelPack = TRUE shows it, case and all",
                      packValue("TRUE").getShowLevelPack());
        Harness.check("ShowLevelPack = yes is not a recognized ON, so it stays HIDDEN",
                      !packValue("yes").getShowLevelPack());

        /* The two settings-file traps from CLAUDE.md section 5, which have caught every switch
         * added here so far: the space before '=' is load-bearing, and keys are section-scoped. */
        Harness.check("ShowLevelPack=true with no spaces is NOT honored (the parser drops the k)",
                      !graphicsLine("ShowLevelPack=true").getShowLevelPack());
        Harness.check("ShowLevelName=false with no spaces does NOT hide it either",
                      graphicsLine("ShowLevelName=false").getShowLevelName());
        Harness.check("ShowLevelPack under [Paths] is a different key nothing reads",
                      !SuccPaths.load(settingsWith(freshDir("packsection"),
                              "[Paths]", "ShowLevelPack = true")).getShowLevelPack());
        Harness.check("ShowLevelName under [Paths] cannot hide the name either",
                      SuccPaths.load(settingsWith(freshDir("namesection"),
                              "[Paths]", "ShowLevelName = false")).getShowLevelName());

        /* ================================================================
         * 4. The template rewrite does not erase the new keys
         * ================================================================
         * CLAUDE.md section 5, trap 4: ANY settings change rewrites the whole file from a fixed
         * template, so a key the template does not know about is destroyed the next time the user
         * picks a TWS folder. Both keys were added to render(); this proves it by making a real
         * settings change and reading the file back. */

        Harness.section("4. surviving the template rewrite");

        Path dir = freshDir("rewrite");
        File ini = settingsWith(dir, "[Graphics]", "ShowLevelPack = true", "ShowLevelName = false");
        SuccPaths p = SuccPaths.load(ini);
        Harness.check("loaded: pack on",  p.getShowLevelPack());
        Harness.check("loaded: name off", !p.getShowLevelName());

        p.setTWSPath(dir.resolve("tws").toString());   // any settings change rewrites the whole file
        String written = Files.readString(ini.toPath(), StandardCharsets.UTF_8);
        Harness.check("the rewritten file still carries ShowLevelPack", written.contains("ShowLevelPack = "));
        Harness.check("and still carries ShowLevelName", written.contains("ShowLevelName = "));
        Harness.check("both land under [Graphics], where the getters look",
                      written.indexOf("[Graphics]") < written.indexOf("ShowLevelPack")
                   && written.indexOf("ShowLevelPack") < written.indexOf("[Emulation]"));

        SuccPaths reread = SuccPaths.load(ini);
        Harness.check("ShowLevelPack survives the rewrite", reread.getShowLevelPack());
        Harness.check("ShowLevelName survives the rewrite", !reread.getShowLevelName());

        /* The written values go through the same predicates the getters use, so the file can never
         * echo back something the program would read differently -- the invariant ShowBuildTag
         * already holds. An unrecognized value must come back normalized, not preserved. */
        Path odd = freshDir("normalize");
        File oddIni = settingsWith(odd, "[Graphics]", "ShowLevelPack = yes", "ShowLevelName = yes");
        SuccPaths q = SuccPaths.load(oddIni);
        q.setTWSPath(odd.resolve("tws").toString());
        String normalized = Files.readString(oddIni.toPath(), StandardCharsets.UTF_8);
        Harness.check("an unrecognized ShowLevelPack is rewritten as the false the getter reads",
                      normalized.contains("ShowLevelPack = false"));
        Harness.check("an unrecognized ShowLevelName is rewritten as the true the getter reads",
                      normalized.contains("ShowLevelName = true"));
    }

    /** Separator count -- " - " occurrences, which is what "no extra dashes" actually means. */
    private static int separators(String title) {
        int n = 0, i = title.indexOf(" - ");
        while (i >= 0) { n++; i = title.indexOf(" - ", i + 3); }
        return n;
    }

    private static Path freshDir(String name) throws IOException {
        return Harness.tempDir("jc14-title-" + name + "-");
    }

    /** Writes a settings file containing exactly the given lines, and returns it. */
    private static File settingsWith(Path dir, String... lines) throws IOException {
        File f = dir.resolve(SuccPaths.SETTINGS_FILE_NAME).toFile();
        Files.writeString(f.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        return f;
    }

    /** A settings file whose only [Graphics] key is ShowLevelName, set to the given value. */
    private static SuccPaths nameValue(String value) throws IOException {
        return graphicsLine("ShowLevelName = " + value);
    }

    /** A settings file whose only [Graphics] key is ShowLevelPack, set to the given value. */
    private static SuccPaths packValue(String value) throws IOException {
        return graphicsLine("ShowLevelPack = " + value);
    }

    /** A settings file holding one [Graphics] line, loaded. */
    private static SuccPaths graphicsLine(String line) throws IOException {
        return SuccPaths.load(settingsWith(
                Harness.tempDir("jc14-title-one-"), "[Graphics]", line));
    }
}
