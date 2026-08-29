import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared assertion counters and result reporting for everything in test\.
 *
 * There is no JUnit here, and adding one would mean adding a dependency manager to a project that
 * deliberately has none (see CLAUDE.md). What JUnit was actually providing that plain main() does
 * not is two things, and this supplies both:
 *
 *   1. MACHINE-READABLE RESULTS. -Dsupercc.results=&lt;base path&gt; writes &lt;base&gt;.xml (JUnit XML, which
 *      GitHub Actions renders as annotations on the failing line) and &lt;base&gt;.json (the same data,
 *      easier to diff between two runs).
 *   2. RESULTS EVEN WHEN THE RUN DIES. Assertions here never throw, but the code around them does:
 *      Files.writeString, ZipFile and reflection all throw checked exceptions, and a test that dies
 *      halfway used to print nothing and produce no file at all -- precisely the case where you
 *      most want to know which assertions had already run. run() catches Throwable, records it as
 *      a failure, and still writes the report.
 *
 * Usage:
 *
 *     public static void main(String[] args) {
 *         System.exit(Harness.run("EngineTest", EngineTest::body));
 *     }
 *     private static void body() throws Exception { ... }
 */
public final class Harness {

    /** A test body that is allowed to throw, unlike Runnable. */
    @FunctionalInterface
    public interface Body { void run() throws Exception; }

    private static final class Result {
        final String suite, name, status, detail;
        Result(String suite, String name, String status, String detail) {
            this.suite = suite; this.name = name; this.status = status; this.detail = detail;
        }
    }

    private static final List<Result> results = new ArrayList<>();
    private static String suite = "(no section)";
    private static int pass, fail, skipped;

    private Harness() { }

    /**
     * Starts a named section. The name becomes the JUnit testsuite name, so it wants to be short
     * and stable -- these show up in CI as the grouping for every assertion under them.
     */
    public static void section(String name) {
        suite = name;
        System.out.println("\n== " + name + " ==");
    }

    public static void check(String what, boolean ok) {
        if (ok) { pass++; System.out.println("  PASS  " + what); results.add(new Result(suite, what, "pass", null)); }
        else    { fail++; System.out.println("  FAIL  " + what); results.add(new Result(suite, what, "fail", "assertion failed")); }
    }

    public static void eq(String what, Object actual, Object expected) {
        boolean ok = (expected == null) ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("  PASS  " + what); results.add(new Result(suite, what, "pass", null)); }
        else {
            fail++;
            System.out.println("  FAIL  " + what + "\n          expected: " + expected
                                              + "\n          actual:   " + actual);
            results.add(new Result(suite, what, "fail", "expected: " + expected + " | actual: " + actual));
        }
    }

    /**
     * Records a check that could not run because something it needs is absent -- a third-party
     * level set, say. Skips are counted and reported separately from passes on purpose: a suite of
     * green skips is not coverage, and it must never be able to look like it.
     */
    public static void skip(String why) {
        skipped++;
        System.out.println("  SKIP  " + why);
        results.add(new Result(suite, why, "skip", "skipped"));
    }

    public static int passed()  { return pass; }
    public static int failed()  { return fail; }
    public static int skipped() { return skipped; }

    private static final List<Path> tempDirs = new ArrayList<>();
    static {
        // File.deleteOnExit() deletes a DIRECTORY, and a directory a test has written into is never
        // empty, so it silently fails -- which is why every run used to leave a dozen populated
        // scc-*/jc8-* folders in %TEMP% forever. A shutdown hook that walks the tree actually works.
        Runtime.getRuntime().addShutdownHook(new Thread(Harness::deleteTempDirs));
    }

    /**
     * A temp directory that is really cleaned up when the JVM exits, unlike deleteOnExit().
     * Every test that writes fixtures should get its scratch space from here.
     */
    public static Path tempDir(String prefix) throws IOException {
        Path d = Files.createTempDirectory(prefix);
        synchronized (tempDirs) { tempDirs.add(d); }
        return d;
    }

    private static void deleteTempDirs() {
        synchronized (tempDirs) {
            for (Path d : tempDirs) {
                try (java.util.stream.Stream<Path> walk = Files.walk(d)) {
                    // Deepest first, so a directory is always empty by the time it is removed.
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
                } catch (IOException ignored) {
                    // Cleanup is best effort. A locked temp file must never fail a passing run.
                }
            }
        }
    }

    /**
     * Runs a test body, reports, and returns the process exit code: 0 only if nothing failed.
     * Skips do not fail the run; an exception does.
     */
    public static int run(String name, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            fail++;
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.out.println("  FAIL  " + name + " threw " + t);
            results.add(new Result(suite, name + " completed without throwing", "fail", sw.toString()));
        }
        System.out.println("\n== results ==");
        System.out.println("  " + pass + " passed, " + fail + " failed, " + skipped + " skipped");
        /* A one-line machine-readable count for run-tests.ps1's summary. Without it the summary
         * lists class NAMES only, so a suite that silently shrank from 32 assertions to 2 -- an
         * early return, or fixtures that all started skipping -- produces a byte-identical
         * "all green" line. */
        writeSummary(name);
        try {
            writeReports(name);
        } catch (IOException | RuntimeException e) {
            /* A reporting failure must not turn a green run red or a red run green; say so and let
             * the assertion counts decide the exit code. RuntimeException is caught alongside
             * IOException because Paths.get() throws InvalidPathException -- which is unchecked --
             * for a malformed -ResultsPath, and that would otherwise escape and fail a suite that
             * had already passed every assertion. */
            System.out.println("  WARN  could not write result files: " + e);
        }
        return fail == 0 ? 0 : 1;
    }

    /** Best effort by design: a summary that cannot be written must never change the exit code. */
    private static void writeSummary(String name) {
        String path = System.getProperty("supercc.summary");
        if (path == null || path.isBlank()) return;
        try {
            Files.writeString(Paths.get(path),
                    name + " " + pass + " " + fail + " " + skipped + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) { }
    }

    private static void writeReports(String name) throws IOException {
        String base = System.getProperty("supercc.results");
        if (base == null || base.isBlank()) return;
        Path xml  = Paths.get(base + ".xml");
        Path json = Paths.get(base + ".json");
        if (xml.getParent() != null) Files.createDirectories(xml.getParent());
        Files.writeString(xml,  junitXml(name), StandardCharsets.UTF_8);
        Files.writeString(json, json(name),     StandardCharsets.UTF_8);
        System.out.println("  wrote " + xml + " and " + json);
    }

    private static String junitXml(String name) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<testsuites name=\"").append(xml(name)).append("\" tests=\"").append(pass + fail + skipped)
         .append("\" failures=\"").append(fail).append("\" skipped=\"").append(skipped).append("\">\n");

        // Grouped into contiguous runs by suite so each <testsuite> can carry its own tests /
        // failures / skipped counts. Several Actions report consumers require those attributes
        // rather than deriving them from the child elements.
        int i = 0;
        while (i < results.size()) {
            String suiteName = results.get(i).suite;
            int end = i;
            int sTests = 0, sFail = 0, sSkip = 0;
            while (end < results.size() && results.get(end).suite.equals(suiteName)) {
                sTests++;
                if ("fail".equals(results.get(end).status)) sFail++;
                else if ("skip".equals(results.get(end).status)) sSkip++;
                end++;
            }
            b.append("  <testsuite name=\"").append(xml(name + " / " + suiteName))
             .append("\" tests=\"").append(sTests)
             .append("\" failures=\"").append(sFail)
             .append("\" skipped=\"").append(sSkip).append("\">\n");
            for (int j = i; j < end; j++) {
                Result r = results.get(j);
                b.append("    <testcase classname=\"").append(xml(name))
                 .append("\" name=\"").append(xml(r.name)).append("\"");
                if ("pass".equals(r.status)) {
                    b.append("/>\n");
                } else if ("skip".equals(r.status)) {
                    b.append(">\n      <skipped/>\n    </testcase>\n");
                } else {
                    /* The detail goes in the element BODY, not only the message attribute. XML
                     * attribute-value normalization collapses newlines and tabs to spaces on parse,
                     * so a stack trace placed in an attribute arrives as one flattened line -- and a
                     * stack trace is exactly what this element exists to carry. The attribute keeps
                     * a short first line for report UIs that only show that. */
                    b.append(">\n      <failure message=\"").append(xml(firstLine(r.detail)))
                     .append("\">").append(xml(r.detail)).append("</failure>\n    </testcase>\n");
                }
            }
            b.append("  </testsuite>\n");
            i = end;
        }
        b.append("</testsuites>\n");
        return b.toString();
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static String json(String name) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"name\": \"").append(js(name)).append("\",\n");
        b.append("  \"passed\": ").append(pass).append(",\n");
        b.append("  \"failed\": ").append(fail).append(",\n");
        b.append("  \"skipped\": ").append(skipped).append(",\n");
        b.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            b.append("    {\"suite\": \"").append(js(r.suite)).append("\", \"name\": \"").append(js(r.name))
             .append("\", \"status\": \"").append(r.status).append("\"");
            if (r.detail != null) b.append(", \"detail\": \"").append(js(r.detail)).append("\"");
            b.append("}").append(i + 1 < results.size() ? "," : "").append("\n");
        }
        b.append("  ]\n}\n");
        return b.toString();
    }

    /** XML attribute/text escaping. Assertion names here contain [ ], &lt; and quotes. */
    private static String xml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> b.append("&amp;");
                case '<'  -> b.append("&lt;");
                case '>'  -> b.append("&gt;");
                case '"'  -> b.append("&quot;");
                case '\'' -> b.append("&apos;");
                // XML 1.0 forbids most control characters outright, even escaped. A stack trace
                // carries tabs and newlines, which are legal; anything else becomes a space.
                default   -> b.append(c < 0x20 && c != '\t' && c != '\n' && c != '\r' ? ' ' : c);
            }
        }
        return b.toString();
    }

    private static String js(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
