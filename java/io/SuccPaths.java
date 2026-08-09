package io;

import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SuccPaths {

    private final File settingsFile;
    private Map<String, String> settingsMap;

    /* MOD (Jeremy): false when settings.ini EXISTS but could not be read. In that state the file
     * is left strictly alone -- an unreadable file is far more likely to be a transient lock
     * (Dropbox, antivirus) than a corrupt one, and overwriting it with defaults would destroy
     * real settings to recover from a problem that fixes itself. */
    private boolean persist = true;
    private String loadWarning;

    /* MOD (Jeremy): retries for a file another process has open. BOTH sides need them: on Windows
     * Java opens files for reading WITHOUT FILE_SHARE_DELETE, so any concurrent reader -- notably
     * Dropbox reacting to the write we just did -- makes the atomic move below fail outright.
     * Measured at a 2 ms reader cadence, 14% of writes failed with no retry. */
    private static final int READ_ATTEMPTS = 3;
    private static final long READ_RETRY_MS = 150;
    private static final int WRITE_ATTEMPTS = 4;
    private static final long WRITE_RETRY_MS = 120;

    /* MOD (Jeremy): makes each staging file unique. A fixed name is not enough -- two SuperCC
     * instances sharing a working directory truncate each other's staging file mid-write, and the
     * loser promotes a BLEND of both renders into settings.ini (measured: 236 corrupt staging
     * files over 400 rounds). The PowerShell toggle script has always used "$PID"; this matches. */
    private static final java.util.concurrent.atomic.AtomicLong TMP_SEQ = new java.util.concurrent.atomic.AtomicLong();

    /* MOD (Jeremy): every key this class knows, in FILE ORDER, with the default its getter uses.
     * Two jobs: (1) seedDefaults() fills any missing key at load, so updateSettingsFile() can
     * never render a null and persist the literal text "null"; (2) createSettingsFile() writes
     * the complete set, instead of the partial set it used to write. */
    private static final Map<String, String> DEFAULTS = buildDefaults();

    private static Map<String, String> buildDefaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Paths:Levelset", "");
        d.put("Paths:TWS", "");
        d.put("Paths:succ", "succsave");
        d.put("Controls:Up", String.valueOf(KeyEvent.VK_UP));
        d.put("Controls:Left", String.valueOf(KeyEvent.VK_LEFT));
        d.put("Controls:Down", String.valueOf(KeyEvent.VK_DOWN));
        d.put("Controls:Right", String.valueOf(KeyEvent.VK_RIGHT));
        d.put("Controls:HalfWait", String.valueOf(KeyEvent.VK_SPACE));
        d.put("Controls:FullWait", String.valueOf(KeyEvent.VK_ESCAPE));
        d.put("Controls:UpLeft", String.valueOf(KeyEvent.VK_U));
        d.put("Controls:DownLeft", String.valueOf(KeyEvent.VK_J));
        d.put("Controls:DownRight", String.valueOf(KeyEvent.VK_K));
        d.put("Controls:UpRight", String.valueOf(KeyEvent.VK_I));
        d.put("Controls:Rewind", String.valueOf(KeyEvent.VK_BACK_SPACE));
        d.put("Controls:Play", String.valueOf(KeyEvent.VK_ENTER));
        d.put("Graphics:TilesheetNum", "0");
        d.put("Graphics:LynxTilesheetNum", "0");
        d.put("Graphics:TileWidth", "20");
        d.put("Graphics:TileHeight", "20");
        d.put("Graphics:TWSNotate", "false");
        d.put("Graphics:ShowBuildTag", "true");
        return d;
    }

    /* MOD (Jeremy): fill in anything the file did not supply, and REPAIR the literal string "null"
     * left behind by older builds (a real settings.ini in superCCDevelopment\ carries
     * "LynxTilesheetNum = null"). "null" is a non-null String, so without this the String getters
     * -- getTWSPath(), getLevelsetFolderPath(), getSuccPath() -- return the four characters n-u-l-l
     * forever and never self-heal. A path genuinely named "null" is not worth preserving. */
    private void seedDefaults() {
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            String v = settingsMap.get(e.getKey());
            if (v == null || v.equals("null")) settingsMap.put(e.getKey(), e.getValue());
        }
    }

    /* MOD (Jeremy): the settings file as text. Byte-for-byte the layout the previous PrintWriter
     * produced -- section headers end with the platform separator (they used println), key lines
     * with a bare \n (they used printf), and there is no trailing newline. Keeping the exact shape
     * means upgrading to jc-4 does not rewrite anyone's file gratuitously. */
    private String renderSettings() {
        return render(settingsMap);
    }

    /* MOD (Jeremy): static so a complete default file can be rendered without an instance -- the
     * earlier version built a throwaway SuccPaths and flipped a field on it just to reach this. */
    private static String render(Map<String, String> map) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder(512);
        sb.append("[Paths]").append(nl);
        append(sb, map, "Levelset", "Paths:Levelset");
        append(sb, map, "TWS", "Paths:TWS");
        append(sb, map, "succ", "Paths:succ");
        sb.append('\n').append("[Controls]").append(nl);
        for (String c : new String[]{"Up", "Left", "Down", "Right", "HalfWait", "FullWait",
                "UpLeft", "DownLeft", "DownRight", "UpRight", "Rewind", "Play"}) {
            append(sb, map, c, "Controls:" + c);
        }
        sb.append('\n').append("[Graphics]").append(nl);
        for (String g : new String[]{"TilesheetNum", "LynxTilesheetNum", "TileWidth", "TileHeight", "TWSNotate"}) {
            append(sb, map, g, "Graphics:" + g);
        }
        // Last line, no trailing newline. Rendered through the same predicate the getter uses, so
        // the file can never disagree with what getShowBuildTag() will read back out of it.
        sb.append("ShowBuildTag = ").append(showBuildTagOf(map.get("Graphics:ShowBuildTag")));
        return sb.toString();
    }

    private static void append(StringBuilder sb, Map<String, String> map, String name, String key) {
        String v = map.get(key);
        if (v == null) v = DEFAULTS.getOrDefault(key, "");   // never render the text "null"
        sb.append(name).append(" = ").append(v).append('\n');
    }

    /* MOD (Jeremy): ATOMIC, and no longer silent. The old version opened a PrintWriter straight
     * onto settings.ini, which TRUNCATES AT OPEN -- so anything that went wrong between open and
     * close (disk full, I/O error, the process killed) left a zero-byte settings.ini where a good
     * one used to be. Worse, PrintWriter never throws: it swallowed failures into a flag that only
     * checkError() reads, and nothing called it.
     *
     * Now the content is rendered first, staged to a UNIQUELY NAMED sibling temp file, and moved
     * into place, so settings.ini is only ever replaced by a complete file.
     *
     * The move is RETRIED, because making it atomic introduced a failure the old code did not
     * have: on Windows a concurrent reader (Dropbox, antivirus) blocks the destination's deletion,
     * and MoveFileEx must delete it. Truncate-in-place used to succeed there. Without retries this
     * traded "the file gets damaged" for "the change is silently dropped", which is better but
     * still wrong -- so a failure that survives the retries is reported through the handler. */
    private void updateSettingsFile() {
        if (!persist) return;   // we could not read this file; never write over it
        try {
            writeSettingsAtomically();
            lastWriteError = null;
        }
        catch (IOException e) {
            lastWriteError = e;
            e.printStackTrace();
            if (writeErrorHandler != null) writeErrorHandler.accept(e);
        }
    }

    private void writeSettingsAtomically() throws IOException {
        Path target = settingsFile.toPath();
        Path parent = target.getParent();
        Path dir = (parent == null) ? Paths.get("") : parent;
        /* The staging file MUST be a sibling of the target. Same directory means same volume, which
         * is what keeps Files.move on the ATOMIC_MOVE path; the non-atomic fallback below is
         * copy-then-delete, which truncates the destination and would reintroduce exactly the torn
         * file this method exists to prevent. Do not "tidy" this into %TEMP%. */
        Path tmp = dir.resolve(settingsFile.getName() + ".tmp-" + ProcessHandle.current().pid()
                + "-" + TMP_SEQ.incrementAndGet());
        try {
            Files.writeString(tmp, renderSettings(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            IOException last = null;
            for (int attempt = 0; attempt < WRITE_ATTEMPTS; attempt++) {
                try {
                    try {
                        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    }
                    catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
                catch (IOException e) {
                    last = e;
                    if (attempt < WRITE_ATTEMPTS - 1) sleepQuietly(WRITE_RETRY_MS);
                }
            }
            throw last;
        }
        finally {
            // No-op after a successful move; on failure this is what stops a stray .tmp-* from
            // being left in a Dropbox folder and syncing to the other machine.
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /* MOD (Jeremy): the last settings-write failure, or null once a write succeeds. The write is
     * atomic, so a failure always means "this change was not saved", never "the file was damaged". */
    private IOException lastWriteError;
    public IOException getLastWriteError() { return lastWriteError; }

    /* MOD (Jeremy): called when a settings write fails after all retries. Without this the failure
     * is invisible -- printStackTrace goes nowhere under javaw, and polling getLastWriteError()
     * would mean a check at every one of the eleven setter call sites. */
    private java.util.function.Consumer<IOException> writeErrorHandler;
    public void setWriteErrorHandler(java.util.function.Consumer<IOException> handler) {
        this.writeErrorHandler = handler;
    }

    /* MOD (Jeremy): PORTABLE PATHS (jc-5). Levelset and TWS used to be stored as absolute paths,
     * and settings.ini is Dropbox-synced between two PCs with different usernames -- so the desktop
     * wrote "C:\Users\Jeremy\..." and the laptop wrote "C:\Users\jerem\...", every settings change
     * rewrote the whole file, and whichever machine was used last left the other pointing at a
     * folder that does not exist. Observed live: Levelset from one PC and TWS from the other, in
     * the same file.
     *
     * The fix is the one already in the file: "succ = succsave" has always been stored relative and
     * has never caused this. So a path INSIDE the Chip's Challenge folder is now stored relative to
     * it ("data", "tws\JacquesOld-MS") and re-expanded on the way out. A path outside stays
     * absolute -- there is nothing sensible to anchor it to.
     *
     * Callers see NO change: these getters still return absolute, usable paths, exactly as before.
     * Only the on-disk representation moved. An existing absolute value still resolves (it is
     * returned as-is) and is converted the next time that folder is set, so the migration needs no
     * separate step.
     *
     * getSuccPath() is deliberately NOT routed through this. It is the one data-bearing path (it is
     * where solution JSONs get written), it is already relative, and its callers resolve it against
     * the working directory. Changing what it returns would move where solutions are saved. */
    public String getLevelsetFolderPath() {
        String levelsetFolder = settingsMap.get("Paths:Levelset");
        if (levelsetFolder != null) return toUsablePath(levelsetFolder);
        else {
            setLevelsetFolderPath("");
            return "";
        }
    }
    public String getTWSPath() {
        String tws = settingsMap.get("Paths:TWS");
        if (tws != null) return toUsablePath(tws);
        else {
            setTWSPath("");
            return "";
        }
    }

    /** MOD (Jeremy): the Chip's Challenge folder -- the directory settings.ini itself lives in. */
    private Path ccFolder() {
        File parent = settingsFile.getAbsoluteFile().getParentFile();
        return (parent == null ? Paths.get("") : parent.toPath()).toAbsolutePath().normalize();
    }

    /** MOD (Jeremy): absolute path in -> the form stored in settings.ini (relative when it can be). */
    private String toStoredPath(String path) {
        if (path == null || path.isBlank()) return path;   // "" means "no folder chosen" - keep it
        try {
            Path abs = Paths.get(path).toAbsolutePath().normalize();
            Path cc = ccFolder();
            // startsWith is case-insensitive on Windows, so a path stored with different casing
            // than the CC folder still matches. (No backslash-u in this comment: the Java lexer
            // decodes unicode escapes even inside comments, so "\ users" would be a compile error.)
            if (!abs.startsWith(cc)) return path;          // outside the CC folder: leave absolute
            String rel = cc.relativize(abs).toString();
            return rel.isEmpty() ? "." : rel;              // the CC folder itself
        }
        catch (RuntimeException e) {                       // InvalidPathException, or a different drive
            return path;
        }
    }

    /** MOD (Jeremy): the form stored in settings.ini -> an absolute path callers can use. */
    private String toUsablePath(String path) {
        if (path == null || path.isBlank()) return path;
        try {
            Path p = Paths.get(path);
            if (p.isAbsolute()) return path;               // legacy absolute value: still works
            return ccFolder().resolve(p).normalize().toString();
        }
        catch (RuntimeException e) {
            return path;
        }
    }
    public String getSuccPath() {
        String succ = settingsMap.get("Paths:succ");
        if (succ != null) return succ;
        else {
            setSuccPath("succsave");
            return "";
        }
    }
    public int[] getControls() {
        String[] mapKeys = new String[] {"Controls:Up", "Controls:Left", "Controls:Down", "Controls:Right",
                "Controls:UpLeft", "Controls:DownLeft", "Controls:DownRight", "Controls:UpRight",
                "Controls:HalfWait", "Controls:FullWait", "Controls:Rewind", "Controls:Play"};
        int[] controls = new int[mapKeys.length];
        int[] defaultControls = new int[] {KeyEvent.VK_UP, KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT,
                KeyEvent.VK_U, KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_I, KeyEvent.VK_SPACE, KeyEvent.VK_ESCAPE,
                KeyEvent.VK_BACK_SPACE, KeyEvent.VK_ENTER};
        boolean error = false;

        for (int i=0; i < mapKeys.length; i++) {
            try {
                controls[i] = Integer.parseInt(settingsMap.get(mapKeys[i]));
            }
            catch (NumberFormatException e) {
                controls[i] = defaultControls[i];
                error = true;
            }
        }
        if (error)
            setControls(controls);
        return controls;
    }
    public int getMSTilesetNum() {
        try {
            return Integer.parseInt(settingsMap.get("Graphics:TilesheetNum"));
        }
        catch (NumberFormatException e) {
            setMSTilesetNum(0);
            return 0;
        }
    }
    public int getLynxTilesetNum() {
        try {
            return Integer.parseInt(settingsMap.get("Graphics:LynxTilesheetNum"));
        }
        catch (NumberFormatException e) {
            setLynxTilesetNum(0);
            return 0;
        }
    }
    public int[] getTileSizes() {
        try {
            return new int[]{Integer.parseInt(settingsMap.get("Graphics:TileWidth")),
                    Integer.parseInt(settingsMap.get("Graphics:TileHeight"))};
        }
        catch (NumberFormatException e) {
            int[] tileSizes = new int[] {20, 20};
            setTileSizes(tileSizes);
            return tileSizes;
        }
    }
    public boolean getTWSNotation() {
        if (settingsMap.get("Graphics:TWSNotate") != null) return Boolean.parseBoolean(settingsMap.get("Graphics:TWSNotate"));
        else {
            setTWSNotation(false);
            return false;
        }
    }
    /* MOD (Jeremy): the "[jc-N]" build tag in the window title is TOGGLEABLE, by a setting
     * rather than by a rebuild -- it is useful while the fork is under active modification and
     * just noise the rest of the time:
     *
     *     settings.ini  [Graphics]  ShowBuildTag = true   ->  "SuperCC [jc-N] - <pack> - <level>"
     *                               ShowBuildTag = false  ->  "SuperCC - <pack> - <level>"
     *                               (absent)              ->  ON, the default
     *
     * "Anything but an explicit off is ON", so a typo or a settings.ini predating this key keeps
     * the tag; Boolean.parseBoolean() would silently flip the default the other way instead.
     * Settings are parsed once in the constructor, so an edit applies at the next SuperCC launch.
     *
     * WARNING -- hand-editing settings.ini: parseSettings() reads the key as substring(0, pivot-1),
     * so the line MUST have the space before '='. "ShowBuildTag = false" works; "ShowBuildTag=false"
     * parses as the key "ShowBuildTa" and is silently ignored, leaving the tag ON.
     *
     * NOTE: switching the tag off does not make a build unidentifiable -- BUILD_TAG is still a
     * string constant inside SuperCC.jar, so unzipping it (or `strings`) still names the release.
     *
     * ⚠ DO NOT "fix" this getter to match its siblings. Every other getter here self-heals by
     * calling its own setter when the key is absent (see getTWSNotation()), and each self-heal is a
     * whole-file write. This one deliberately does not: "absent" is a meaningful state here that
     * means ON, so there is nothing to repair, and render() calls the same predicate while building
     * the file -- a self-heal would make rendering re-enter the writer for no gain. After
     * seedDefaults() the key is always present anyway, so the null branch is belt-and-braces for
     * the in-memory instances.
     */
    public boolean getShowBuildTag() {
        return showBuildTagOf(settingsMap.get("Graphics:ShowBuildTag"));
    }

    /* MOD (Jeremy): the single definition of "is the tag on", shared by the getter and by render(),
     * so the file written can never disagree with the value read back from it. */
    private static boolean showBuildTagOf(String raw) {
        if (raw == null) return true;
        raw = raw.trim();
        return !(raw.equalsIgnoreCase("false") || raw.equals("0"));
    }
    public String getJSONPath(String levelsetName, int levelNumber, String levelName, String ruleset) {
        String json = getSuccPath();
        new File(Paths.get(json, levelsetName).toString()).mkdirs();
        return Paths.get(json, levelsetName, levelNumber +"_"+levelName+"-"+ruleset+".json").toString();
    }
    public String getSccPath(String levelsetName, int levelNumber, String levelName) {
        return Paths.get(getSuccPath(), levelsetName, Integer.toString(levelNumber), levelName+".scc").toString();
    }

    public void setLevelsetFolderPath(String levelsetFolderPath) {
        // MOD (Jeremy): stored relative to the CC folder when possible -- see getLevelsetFolderPath().
        settingsMap.put("Paths:Levelset", toStoredPath(levelsetFolderPath));
        updateSettingsFile();
    }
    public void setTWSPath(String twsPath) {
        settingsMap.put("Paths:TWS", toStoredPath(twsPath));
        updateSettingsFile();
    }
    public void setSuccPath(String succPath) {
        settingsMap.put("Paths:succ", succPath);
        updateSettingsFile();
    }
    public void setControls(int[] controls) {
        settingsMap.put("Controls:Up", String.valueOf(controls[0]));
        settingsMap.put("Controls:Left", String.valueOf(controls[1]));
        settingsMap.put("Controls:Down", String.valueOf(controls[2]));
        settingsMap.put("Controls:Right", String.valueOf(controls[3]));
        settingsMap.put("Controls:UpLeft", String.valueOf(controls[4]));
        settingsMap.put("Controls:DownLeft", String.valueOf(controls[5]));
        settingsMap.put("Controls:DownRight", String.valueOf(controls[6]));
        settingsMap.put("Controls:UpRight", String.valueOf(controls[7]));
        settingsMap.put("Controls:HalfWait", String.valueOf(controls[8]));
        settingsMap.put("Controls:FullWait", String.valueOf(controls[9]));
        settingsMap.put("Controls:Rewind", String.valueOf(controls[10]));
        settingsMap.put("Controls:Play", String.valueOf(controls[11]));
        updateSettingsFile();
    }
    public void setMSTilesetNum(int tilesetNum) {
        settingsMap.put("Graphics:TilesheetNum", String.valueOf(tilesetNum));
        updateSettingsFile();
    }
    public void setLynxTilesetNum(int tilesetNum) {
        settingsMap.put("Graphics:LynxTilesheetNum", String.valueOf(tilesetNum));
        updateSettingsFile();
    }
    public void setTileSizes(int[] tileSizes) {
        settingsMap.put("Graphics:TileWidth", String.valueOf(tileSizes[0]));
        settingsMap.put("Graphics:TileHeight", String.valueOf(tileSizes[1]));
        updateSettingsFile();
    }
    public void setTWSNotation(boolean twsNotation) {
        settingsMap.put("Graphics:TWSNotate", String.valueOf(twsNotation));
        updateSettingsFile();
    }
    public void setShowBuildTag(boolean showBuildTag) { // MOD (Jeremy): see getShowBuildTag()
        settingsMap.put("Graphics:ShowBuildTag", String.valueOf(showBuildTag));
        updateSettingsFile();
    }

    public SuccPaths(File settingsFile) throws IOException {
        this.settingsFile = settingsFile;
        settingsMap = new HashMap<>();
        // MOD (Jeremy): try-with-resources. The reader used to be left open, and on Windows that
        // dangling handle can make the atomic move in updateSettingsFile() fail.
        try (BufferedReader r = new BufferedReader(new FileReader(settingsFile, StandardCharsets.UTF_8))) {
            parseSettings(settingsMap, r);
        }
        /* MOD (Jeremy): a file with content but not one recognizable key is damaged, not empty --
         * treat it as unreadable so load() falls back to NOT writing, rather than seeding defaults
         * and persisting them over whatever is in there. A genuinely zero-byte file has nothing
         * left to protect, so that one is allowed through and gets defaults written. */
        if (settingsMap.isEmpty() && settingsFile.length() > 0) {
            throw new IOException(settingsFile.getName() + " has content but no readable settings in it"
                    + " (" + settingsFile.length() + " bytes). It looks damaged rather than empty.");
        }
        seedDefaults();
    }

    /* MOD (Jeremy): in-memory instance used when settings.ini exists but cannot be read. */
    private SuccPaths(File settingsFile, String loadWarning) {
        this.settingsFile = settingsFile;
        this.settingsMap = new HashMap<>();
        this.persist = false;
        this.loadWarning = loadWarning;
        seedDefaults();
    }

    /* MOD (Jeremy): THE safe way to get a SuccPaths -- it owns the recovery policy that used to
     * live in SuperCC's constructor, where it was wrong.
     *
     * The old code caught ANY IOException from the read and responded by calling
     * createSettingsFile(), which truncates and writes defaults. It never asked whether the file
     * existed. So a settings.ini momentarily locked by Dropbox or antivirus -- a lock Windows
     * makes MANDATORY, i.e. genuinely unreadable for that instant -- was silently replaced by
     * defaults, permanently losing the Levelset, TWS and succ paths. The only feedback was the
     * dialog "Could not find settings.ini file, creating", which reads as harmless.
     *
     * Now: create defaults ONLY when the file is genuinely absent; retry a few times when it
     * exists but will not open; and if it still will not open, run from in-memory defaults for
     * this session WITHOUT writing, so the real file survives to be read on the next launch.
     * Callers should surface getLoadWarning() when it is non-null. */
    public static SuccPaths load(File settingsFile) {
        if (!settingsFile.exists()) {
            try {
                createSettingsFile(settingsFile);
            }
            catch (FileAlreadyExistsException e) {
                // Something created it between the check above and the CREATE_NEW write -- Dropbox
                // materializing it is the realistic case. Nothing is wrong; go read it.
            }
            catch (IOException e) {
                e.printStackTrace();
                return new SuccPaths(settingsFile,
                        "Could not create " + settingsFile.getName() + ":\n" + e + "\n\n"
                      + "Running with default settings, and nothing will be saved this session.");
            }
            // deliberately falls through to the retry loop -- a file just created in a Dropbox
            // folder is at its most likely to be grabbed by the sync daemon, and reporting that
            // as "could not create" (which the previous version did) is simply false.
        }
        IOException last = null;
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            try { return new SuccPaths(settingsFile); }
            catch (IOException e) {
                last = e;
                if (attempt < READ_ATTEMPTS - 1) sleepQuietly(READ_RETRY_MS);
            }
        }
        return new SuccPaths(settingsFile,
                settingsFile.getName() + " could not be read after " + READ_ATTEMPTS + " attempts:\n"
              + last + "\n\n"
              + "If it is locked by Dropbox or antivirus this normally clears on its own.\n"
              + "SuperCC is running on DEFAULT settings for this session and will NOT save any\n"
              + "settings changes, so your file is left intact -- note the levelset and TWS folders\n"
              + "will be the defaults until you close SuperCC and reopen it.");
    }

    /** MOD (Jeremy): a message the caller should show the user, or null when the load was clean. */
    public String getLoadWarning() { return loadWarning; }

    /** MOD (Jeremy): false when settings changes are being discarded rather than written. */
    public boolean isPersisting() { return persist; }

    private static void parseSettings(Map<String, String> settingsMap, Reader source)
    throws IOException
        {
            BufferedReader reader = new BufferedReader(source);
            String
                    section = "",
                    line = null;
            Pattern headerPattern = null;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                /* MOD (Jeremy): strip a UTF-8 BOM. Without this the BOM sits in front of the first
                 * "[Paths]" header, so charAt(0) != '[' and the header is missed -- every key under
                 * it is then filed under section "" and NEVER READ, silently blanking Levelset, TWS
                 * and succ. Before seedDefaults() that damage at least showed up in the file as the
                 * visible text "null"; now it would render as a plausible empty value, so the BOM
                 * has to be handled here rather than being laundered into something that looks
                 * fine. Notepad and PowerShell 5.1's `-Encoding utf8` both write one. */
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1).trim();
                if (line.isEmpty() || line.charAt(0) == ';')
                {
                    continue;
                }
                else if (line.charAt(0) == '[')
                {
                    if (headerPattern == null)
                        headerPattern = Pattern.compile("\\[\\W*(\\w*)\\W*\\]?");
                    Matcher matcher = headerPattern.matcher(line);
                    if (matcher.find()) section = matcher.group(1);
                }
                else
                {
                    int pivot = line.indexOf('=');
                    if (pivot > 0)
                    {
                        String name = line.substring(0, pivot - 1).trim(),
                        value = line.substring(pivot + 1).trim();
                        if (!name.isEmpty())
                            settingsMap.put(section + ':' + name, value);
                        /*`[View]
                          Zoom = 1.0000`
                          would get put into the map as:
                          ["View:Zoom": "1.0000"]
                         */
                    }
                }
            }
        }

    public static void createSettingsFile() {
        try { createSettingsFile(new File("settings.ini")); }
        catch (IOException g) { g.printStackTrace(); }
    }

    /* MOD (Jeremy): writes a COMPLETE default settings.ini, and reports failure instead of
     * swallowing it. The old version omitted LynxTilesheetNum, TWSNotate, ShowBuildTag and the
     * four diagonal controls -- which is precisely how the literal string "null" got into a
     * settings file in the first place: the keys it skipped were absent from the map, and the
     * next whole-file write rendered them as "null". Rendering from DEFAULTS cannot skip a key.
     * Refuses to overwrite an existing file: this is the "there is nothing here yet" path, and
     * clobbering real settings is the exact bug load() exists to prevent. */
    public static void createSettingsFile(File settingsFile) throws IOException {
        // CREATE_NEW makes "refuse if it already exists" atomic. An exists() check followed by a
        // write is check-then-act: the file can appear in between (Dropbox syncing it down), and
        // the write would then truncate real settings and push the defaults back up to both PCs.
        Files.writeString(settingsFile.toPath(), render(DEFAULTS), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
