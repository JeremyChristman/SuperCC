package emulator;

import game.*;
import graphics.Gui;
import graphics.SmallGamePanel;
import graphics.TileSheet;
import io.DatParser;
import io.ErrorLog;
import io.SuccPaths;
import io.TWSReader;
import tools.SeedSearch;
import tools.TSPGUI;
import tools.VariationTesting;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class SuperCC {

    public static final char UP = 'u', LEFT = 'l', DOWN = 'd', RIGHT = 'r', WAIT = '-', UP_LEFT = '↖', DOWN_LEFT = '↙',
            DOWN_RIGHT = '↘', UP_RIGHT = '↗',  MIN_CLICK_LOWERCASE = '¯', MAX_CLICK_LOWERCASE = 'ÿ',
    MIN_CLICK_UPPERCASE = 'Ā', MAX_CLICK_UPPERCASE = 'Ő';
    private static final char[] CHAR_MOVEMENT_KEYS = {UP, LEFT, DOWN, RIGHT, UP_LEFT, DOWN_LEFT, DOWN_RIGHT, UP_RIGHT, WAIT};
    private static final Map<Character, Direction> DIRECTIONS = Map.of(UP, Direction.UP, LEFT, Direction.LEFT,
            DOWN, Direction.DOWN, RIGHT, Direction.RIGHT, UP_LEFT, Direction.UP_LEFT, DOWN_LEFT, Direction.DOWN_LEFT,
            DOWN_RIGHT, Direction.DOWN_RIGHT, UP_RIGHT, Direction.UP_RIGHT, WAIT, Direction.NONE);
    public static final byte CHIP_RELATIVE_CLICK = 1;

    /* MOD (Jeremy): window title = "SuperCC [jc-N] - <pack> - <level>". Bump BUILD_TAG on each
     * production deploy so the running build is identifiable. The tag is TOGGLEABLE, and since
     * jc-8 it is OPT-IN: succ_settings.ini [Graphics] ShowBuildTag = true (or 1) shows it, and
     * ANYTHING else -- including the key being absent and the file not existing -- hides it. No
     * rebuild or redeploy is needed either way. Settings are read once at construction, so the
     * change applies at the next launch. See SuccPaths.getShowBuildTag(), which owns that rule
     * and explains why it must not be relaxed back.
     *
     * ⚠ Compose titles through windowTitlePrefix(), NEVER by concatenating TITLE and BUILD_TAG
     * directly. Both are compile-time String constants, so javac inlines them at every use site.
     * build.ps1 is a SPLICE build -- it recompiles only the files in $MODIFIED and overlays them on
     * a prebuilt class baseline -- so a reference from a class that is not recompiled (a .form
     * class can NEVER be recompiled) would keep the OLD tag baked in, and the jar would report two
     * different versions depending on which window you looked at. */
    public static final String TITLE = "SuperCC";
    public static final String BUILD_TAG = "[jc-13]";

    private SavestateManager savestates;
//    SavestateCompressor savestateCompressor = new SavestateCompressor();
    private Level level;
    private Gui window;
    private DatParser dat;
    private Solution solution;
    public TWSReader twsReader;
    private SuccPaths paths;
    private EmulatorKeyListener controls;
    public boolean hasGui = true;

    public void setControls(EmulatorKeyListener l) {
        controls = l;
    }
    public EmulatorKeyListener getControls() {
        return controls;
    }
    
    public SuccPaths getPaths() {
        return paths;
    }

    /* MOD (Jeremy): the program name, with the build tag appended when it is switched on.
     * `paths` is null under the GUI-less test constructor SuperCC(boolean) -- which tools like
     * SeedSearch use -- so fall back to the bare name rather than throwing; tworld's equivalent
     * re-title crashed every headless batch run for exactly this reason. (SuccPaths.load() always
     * returns an instance, so the GUI constructor can no longer leave it null.) */
    public String windowTitlePrefix() {
        if (paths == null || !paths.getShowBuildTag()) return TITLE;
        return TITLE + " " + BUILD_TAG;
    }

    /* MOD (Jeremy): marks a session that is running on defaults and discarding settings changes,
     * because settings.ini could not be read. The startup dialog fires once and is then dismissed
     * and forgotten; this keeps the state visible for as long as it lasts. Appended at the very
     * END of the title so it stays clear of supercc_driver.ps1's "SuperCC [tag] - <set> - ..."
     * match, which anchors on the front of the string. */
    public String windowTitleSuffix() {
        return (paths != null && !paths.isPersisting()) ? "  [settings read-only]" : "";
    }
    
    public String getJSONPath() {
        String levelName = level.getTitle().replaceAll("[^a-zA-Z0-9 ]",""); //Delete everything except letters, numbers, and spaces so you won't get issues with illegal filenames
        //levelName = levelName.substring(0, levelName.length()-1).replaceAll("\\s","_"); //No longer needed as the previous line now takes care of this but kept commented in case its needed in future
        return paths.getJSONPath(dat.getLevelsetName(), level.getLevelNumber(), levelName, level.getRuleset().name());
    }
    
    public String getSerPath() {
        return getJSONPath().replace(".json", ".ser");
    }

    public String getLevelsetPath() {
        return dat.getLevelsetPath();
    }

    public void repaint(boolean fromScratch) {
        window.repaint(fromScratch);
    }
    
    public static char capital(char c){
        if (isClick(c) && isLowercase(c))
            return (char) (c + (MAX_CLICK_UPPERCASE - MAX_CLICK_LOWERCASE)); //puts it into the uppercase click range
        return switch (c) {
            case WAIT -> '_';
            case UP_LEFT -> '⇖';
            case DOWN_LEFT -> '⇙';
            case DOWN_RIGHT -> '⇘';
            case UP_RIGHT -> '⇗';
            default -> Character.toUpperCase(c);
        };
    }
    
    public static char lowerCase(char c) {
        if (isClick(c) && isUppercase(c))
            return (char) (c - (MAX_CLICK_UPPERCASE - MAX_CLICK_LOWERCASE)); //puts it into the lowercase click range
        return switch (c) {
            case 'U' -> UP;
            case 'L' -> LEFT;
            case 'D' -> DOWN;
            case 'R' -> RIGHT;
            case '_' -> WAIT;
            case '⇖' -> UP_LEFT;
            case '⇙' -> DOWN_LEFT;
            case '⇘' -> DOWN_RIGHT;
            case '⇗' -> UP_RIGHT;
            default -> Character.toLowerCase(c);
        };
    }
    
    public int lastLevelNumber() {
        return dat.lastLevel();
    }

    public void setTWSFile(File twsFile){
        try{
            this.twsReader = new TWSReader(twsFile);
        }
        catch (IOException e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Could not read file:\n"+e.getLocalizedMessage());
        }
    }

    public Level getLevel(){
        return level;
    }
    public Solution getSolution() {
        return solution;
    }
    public SavestateManager getSavestates(){
        return savestates;
    }
    public void setSavestates(SavestateManager sm) {
        this.savestates = sm;
    }
    public Gui getMainWindow(){
        return window;
    }

    public SuperCC() {
        /* MOD (Jeremy): recovery policy lives in SuccPaths.load() now. This block used to treat
         * ANY IOException as "the file is missing" and call createSettingsFile(), which truncates
         * and writes defaults -- so a settings file merely LOCKED for a moment by Dropbox or
         * antivirus was destroyed, with the reassuring dialog "Could not find settings.ini file,
         * creating" as the only clue. load() distinguishes absent from unreadable, retries a
         * locked file, and falls back to non-persisting defaults rather than overwriting it.
         * It always returns an instance, so `paths` can no longer be left null either.
         *
         * MOD (Jeremy, jc-8): the name comes from SuccPaths.SETTINGS_FILE_NAME -- it is
         * "succ_settings.ini" now, not "settings.ini", so that Tile World can have an
         * initialization file of its own in this same folder without a collision. */
        paths = SuccPaths.load(new File(SuccPaths.SETTINGS_FILE_NAME));
        if (paths.getLoadWarning() != null) throwMessage(paths.getLoadWarning());
        paths.setWriteErrorHandler(this::onSettingsWriteFailed);
        window = new Gui(this);
    }

    /* MOD (Jeremy): a settings write that failed every retry. Reported ONCE per session -- the
     * likeliest cause is Dropbox holding settings.ini open, which affects every subsequent write
     * too, and a dialog per setting change would be worse than useless. The file itself is never
     * damaged (the write is atomic), so the only thing lost is the change. */
    private boolean settingsWriteWarned;
    private void onSettingsWriteFailed(IOException e) {
        if (settingsWriteWarned) return;
        settingsWriteWarned = true;
        throwMessage("Could not save " + SuccPaths.SETTINGS_FILE_NAME + ":\n" + e + "\n\n"
                + "It is probably held open by Dropbox or antivirus. Your settings file is intact,\n"
                + "but this change was not written to it and will be gone at the next launch.\n"
                + "This message is shown only once per session.");
    }

    // GUI-less emulator - used for tests
    public SuperCC(boolean hasGui) {
        this.hasGui = hasGui;
    }

    public void openLevelset(File levelset){
        try{
            dat = new DatParser(levelset);
        }
        catch (IOException e){
            throwError("Could not read file:\n"+e.getLocalizedMessage());
            /* MOD (Jeremy, jc-11): STOP here. This used to fall through to loadLevel() with dat
             * still null, so opening a file that is not a valid level set produced a perfectly
             * clear "Could not read file: Invalid signature" dialog and then, immediately behind
             * it, a NullPointerException out of startingRuleset().
             *
             * It never hurt anything while stderr went nowhere. Now that errors are recorded, it
             * would put a confusing NPE at the top of a brand-new log for one of the commonest
             * things a stranger does wrong -- opening the wrong file -- which is exactly the
             * "an error on an ordinary mistake teaches people to ignore the log" argument that
             * ADR 0009 makes about the startup NPE.
             *
             * Returning is also more correct when a set was already open: the previous levelset
             * stays loaded, instead of being silently replaced by level 1 of itself. */
            return;
        }
        loadLevel(1, 0, Step.EVEN, false, startingRuleset(), Direction.UP);
        if (hasGui)
            window.swapRulesetTilesheet(level.getRuleset());
    }

    /* MOD (Jeremy, jc-9): the ruleset a newly opened level set starts in. Normally the .dat's own
     * signature decides (MO3.dat is flagged Lynx, so it opens under Lynx), but
     * [Emulation] AlwaysOpenInMS = true in succ_settings.ini overrides that to MS every time.
     *
     * Applied HERE rather than in DatParser on purpose. DatParser is a file reader and should not
     * grow a dependency on the settings file; and its parseLevel() records whatever ruleset it is
     * handed, so passing MS in at open time makes every later load of that set MS as well -- the
     * override propagates without a second special case.
     *
     * The paths == null guard is not decoration: the headless SuperCC(boolean) constructor never
     * builds a SuccPaths, and openLevelset() is reachable from it. */
    private Ruleset startingRuleset() {
        if (paths != null && paths.getAlwaysOpenInMS()) return Ruleset.MS;
        return dat.getRuleset();
    }

    public synchronized void loadLevel(int levelNumber, int rngSeed, Step step, boolean keepMoves, Ruleset rules, Direction initialSlide){
        /* MOD (Jeremy, jc-11): no level set open, nothing to load.
         *
         * The two lastLevelNumber() calls below sit OUTSIDE the try block and dereference `dat`
         * with no guard, so reaching here with dat == null throws before anything is caught. That
         * is reachable from any caller that opens a set and then loads without checking: passing a
         * bad file on the command line (ArgumentParser) or via a file association, and SeedSearch.
         * On the command-line path the NPE escaped the static launch path entirely -- whose try
         * catches only IllegalArgumentException -- so the tilesheet setup below never ran and the
         * window was left half-built, with an NPE as the first entry in a brand-new error log.
         *
         * Guarding here rather than at each caller covers all of them, including future ones, and
         * keeps the fix inside a file that is already spliced. */
        if (dat == null) return;
        if (levelNumber == 0) levelNumber = lastLevelNumber()-1; //If the level number is 0 (player goes back from level 1, load the last level)
        if (levelNumber == lastLevelNumber()) levelNumber = 1; //And vice versa
        try{
            if (keepMoves && level != null && levelNumber == level.getLevelNumber()) {
                solution = new Solution(getSavestates().getMoveList(), rngSeed, step, level.getRuleset(), initialSlide);
                solution.load(this);
            }
            else {
                level = dat.parseLevel(levelNumber, rngSeed, step, rules, initialSlide);
                savestates = new SavestateManager(this, level);
                solution = new Solution(new char[] {}, 0, Step.EVEN, Solution.BASIC_MOVES, level.getRuleset(), Direction.UP);
                if(hasGui) {
                    window.repaint(true);
                    // MOD (Jeremy): title = "SuperCC [jc-N] - <pack> - <level>", where the "[jc-N]"
                    // half is switched on/off by settings.ini's ShowBuildTag -- see windowTitlePrefix().
                    window.setTitle(windowTitlePrefix() + " - " + dat.getLevelsetName() + " - "
                            + level.getTitle() + windowTitleSuffix());
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
            throwError("Could not load level: "+e.getMessage());
        }
    }

    public synchronized void loadLevel(int levelNumber){
        loadLevel(levelNumber, 0, Step.EVEN, true, Ruleset.CURRENT, Direction.UP);
    }

    public boolean tick(char c, Direction[] directions, TickFlags flags){
        if (level == null)
            return false;
        if (directions[0].isDiagonal() && !level.supportsDiagonal()) {
            directions[0] = directions[0].decompose()[0]; //take vertical
            for (char c1 : DIRECTIONS.keySet()) { //switch the char part to vertical
                if (directions[0] == DIRECTIONS.get(c1))
                    c = c1;
            }
        }
        boolean tickMulti = level.tick(c, directions);
        if (flags.multiTick && tickMulti) {
            for (int i=0; i < level.ticksPerMove() - 1; i++) {
                c = capital(c);
                level.tick(c, new Direction[] {Direction.NONE});
            }
        }
        if (flags.save) {
            savestates.addRewindState(level, c);
        }
        if (flags.repaint) window.repaint(false);

        return tickMulti;
    }
    
    public boolean tick(char c, TickFlags flags){
        if (level == null) return false;
        Direction[] directions;
        if (isClick(c)){
            Position screenPosition = Position.screenPosition(level.getChip().getPosition());
            Position clickedPosition = Position.clickPosition(screenPosition, c);
            directions = level.getChip().getPosition().seek(clickedPosition);
            level.setClick(clickedPosition.getIndex());
            return tick(c, directions, flags);
        }
        else{
            for (char charMovementKey : CHAR_MOVEMENT_KEYS) {
                if (charMovementKey == c) {
                    directions = new Direction[] {DIRECTIONS.get(c)};
                    return tick(c, directions, flags);
                }
            }
        }
        return false;
    }

    public boolean isLevelLoaded() {
        return level != null;
    }

    public static boolean isClick(char c){
        return c <= MAX_CLICK_UPPERCASE && c >= MIN_CLICK_LOWERCASE;
    }

    public static boolean isUppercase(char c) {
        return c == 'U' || c == 'L' || c == 'D' || c == 'R' || c == '_' || c == '⇖' || c == '⇙' || c == '⇘' || c == '⇗'
                || (c <= MAX_CLICK_UPPERCASE && c >= MIN_CLICK_UPPERCASE);
    }

    public static boolean isLowercase(char c) {
        return c == UP || c == LEFT || c == DOWN || c == RIGHT || c == UP_LEFT || c == DOWN_LEFT || c == WAIT
                || c == DOWN_RIGHT || c == UP_RIGHT || (c <= MAX_CLICK_LOWERCASE && c >= MIN_CLICK_LOWERCASE);
    }

    public void showAction(String s){
        getMainWindow().getLastActionPanel().update(s);
        getMainWindow().getLastActionPanel().repaint();
    }

    void testTWS() {
        System.out.println(dat.getLevelsetName());

        for (int j = 1; j <= level.getLevelsetLength(); j++) {
            loadLevel(j);
            try {
                Solution s = twsReader.readSolution(level);
                // System.out.println(s.efficiency);
                s.load(this);
                if (level.getLayerFG().get(level.getChip().getPosition()) != Tile.EXITED_CHIP && !level.isCompleted()) {
                    System.out.println("failed level "+level.getLevelNumber()+" "+ level.getTitle());
                }
            }
            catch (Exception exc) {
                System.out.println("Error loading "+level.getLevelNumber()+" "+ level.getTitle());
                exc.printStackTrace();
            }
        }
    }
    
    private void runBenchmark(int levelNumber, int runs){
        loadLevel(levelNumber);
        Solution s;
        try {
            s = twsReader.readSolution(level);
            System.out.println("Running test without writing.");
            long startTime = System.nanoTime();
            for (int i = 0; i < runs; i++) s.load(this, TickFlags.LIGHT);
            long endTime = System.nanoTime();
            double timePerIteration = (endTime - startTime) / (double) runs;
            System.out.println("Time per iteration:");
            System.out.println((timePerIteration / 1000000)+"ms");
            System.out.println((timePerIteration / 1000000000)+"s\n");
            System.out.println("Running test with writing.");
            startTime = System.nanoTime();
            for (int i = 0; i < runs; i++) s.load(this);
            endTime = System.nanoTime();
            timePerIteration = (endTime - startTime) / (double) runs;
            System.out.println("Time per iteration:");
            System.out.println((timePerIteration / 1000000)+"ms");
            System.out.println((timePerIteration / 1000000000)+"s");
            double numMoves = savestates.getMoves().length;
            int size = savestates.getSavestate().length;
            while (savestates.getNode().hasParent()){
                savestates.rewind();
                size += savestates.getSavestate().length;
            }
            System.out.println("\nTotal state size:");
            System.out.println((size / (double) 1000)+" kb");
            System.out.println("\nAverage state size:");
            System.out.println((size / numMoves / 1000)+" kb");
        }
        catch (IOException e){
            System.out.println("Benchmark of level "+level+"failed");
        }
    }

    public static void initialise(String[] args){
        SuperCC emulator = new SuperCC();

        // The GUI exists now, so a first error can actually be reported to the user. Under javaw
        // this dialog is the only way anyone learns the log file is there at all.
        ErrorLog.setNotifier(emulator::onErrorLogged);

        try {
            ArgumentParser.parseArguments(emulator, args); //Parses any command line arguments given
        } catch (IllegalArgumentException e) {
            emulator.throwError(e.toString() + "\nSee stderr for flag use");
        }

        emulator.initialiseTilesheet();
    }

    /* MOD (Jeremy, jc-11): told once per session, the first time anything is written to the log.
     * Mirrors onSettingsWriteFailed: a repeated dialog would be worse than useless, and the point
     * is only to make sure the file is discoverable. */
    private boolean errorLogWarned;
    private void onErrorLogged(File log) {
        /* The check and the set live INSIDE the lambda so that errorLogWarned is only ever touched
         * on the event thread. This is called from whatever thread happened to fail -- an arbitrary
         * worker, or the uncaught-exception handler -- so testing it out here would be an
         * unsynchronized cross-thread read, and the failure it invites is two modal dialogs. */
        SwingUtilities.invokeLater(() -> {
            if (errorLogWarned) return;
            errorLogWarned = true;
            /* "Paste the contents", not "attach the file": the file NAME carries this PC's name,
             * which a GitHub attachment preserves in its URL, while a paste does not. The body
             * carries absolute paths -- and therefore the Windows account name -- so the caution
             * belongs here rather than only in the README. For most people this dialog is the only
             * thing they will ever read about the log. */
            throwMessage(
                "SuperCC recorded an error.\n\nDetails were written to:\n" + log + "\n\n"
                + "The program may still be working normally. If something looks wrong, open that\n"
                + "file and paste its contents into a bug report.\n\n"
                + "It contains paths from your own machine, including your Windows user name, so\n"
                + "read it before posting it anywhere public.\n\n"
                + "This message is shown only once per session.");
        });
    }

    private void initialiseTilesheet() {
        Gui window = this.getMainWindow();
        SuccPaths paths = this.getPaths();
        TileSheet[] tileSheets = TileSheet.values();
        TileSheet tileSheet = tileSheets[paths.getMSTilesetNum()];
        int[] tileSizes = paths.getTileSizes();
        int width = tileSizes[0];
        int height = tileSizes[1];
        SmallGamePanel gamePanel = (SmallGamePanel) window.getGamePanel();
        this.getMainWindow().getGamePanel().setTileSheet(tileSheet);
        BufferedImage[] tilesetImages = null;
        try {
            tilesetImages = tileSheet.getTileSheets(width, height);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //see if all of this can't be refactored out of existence by pressing the buttons in MenuBar if their setting is changed
        window.getGamePanel().initialise(this, tilesetImages, tileSheet, tileSizes[0], tileSizes[1]);
        window.getInventoryPanel().initialise(this);
        window.setSize(200+width*gamePanel.getWindowSizeX(), 200+height*gamePanel.getWindowSizeY());
        window.getGamePanel().setPreferredSize(new Dimension(width * gamePanel.getWindowSizeX(), height * gamePanel.getWindowSizeY()));
        window.getGamePanel().setSize(width*gamePanel.getWindowSizeX(), height*gamePanel.getWindowSizeY());

        window.getLevelPanel().changeNotation(paths.getTWSNotation());

        window.pack();
        /* MOD (Jeremy, jc-11): guard the startup repaint.
         *
         * Gui.repaint(boolean) ends with changePlayButton(emulator.getSavestates().isPaused()),
         * which is NOT guarded, even though the two statements above it already sit behind
         * isLevelLoaded(). With no level open -- the normal case on a fresh launch -- that threw a
         * NullPointerException here on EVERY startup. It was harmless -- the panels above it had
         * already repainted, and what the throw skipped was the play-button refresh itself plus
         * repaintRightContainer() after it -- and invisible, because javaw discards stderr.
         *
         * jc-11 makes stderr visible in a log file, so an error printed on every single launch
         * would train everyone to ignore the artifact. Gui is form-based and cannot be recompiled
         * from this repo, so this call site is the only fixable end.
         *
         * Component.repaint() covers what actually survived the NPE. When a level set WAS passed
         * on the command line, ArgumentParser has already run and savestates is non-null, so the
         * full repaint happens exactly as before. */
        if (isLevelLoaded() && getSavestates() != null) window.repaint(true);
        else window.repaint();
    }

    public static boolean areToolsRunning() {
        return (SeedSearch.isRunning() || TSPGUI.isRunning() || VariationTesting.isRunning());
    }

    public void throwError(String s){
        if (hasGui)
            JOptionPane.showMessageDialog(getMainWindow(), s, "Error", JOptionPane.ERROR_MESSAGE);
        else
            System.err.println("[SuperCC Error] " + s);
    }

    public void throwMessage(String s){
        if (hasGui)
            JOptionPane.showMessageDialog(getMainWindow(), s, "SuCC Message", JOptionPane.PLAIN_MESSAGE);
        else
            System.out.println("[SuperCC Message] " + s);
    }

    public boolean throwQuestion(String s) {
        if (hasGui) {
            return JOptionPane.showConfirmDialog(getMainWindow(), s, "SuCC Option",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
        else {
            System.err.println("Tried throwing question without GUI!");
            return false;
        }
    }

    public static void main(String[] args){
        /* MOD (Jeremy, jc-11): start logging BEFORE anything at all can fail.
         *
         * Under javaw -- which is what a double-click uses -- stderr goes nowhere, so every
         * printStackTrace in this program and every uncaught exception used to vanish. Teeing
         * System.err catches all of them without editing a single call site.
         *
         * Installed here rather than inside the launch path below so that a failure in the event
         * thread's own startup is covered too, not just failures after it is running.
         *
         * The directory is derived exactly the way SuccPaths finds the settings file: a bare
         * relative name resolved against the working directory. It deliberately does NOT ask
         * SuccPaths, because this runs before any SuccPaths exists -- a failure while constructing
         * one is precisely the kind this is here to capture. */
        ErrorLog.install(new File(SuccPaths.SETTINGS_FILE_NAME).getAbsoluteFile().getParentFile());

        /* A fallback notice for the window that never opens. The real notifier is registered once
         * the Gui exists, but a failure while CONSTRUCTING it is precisely the "it just closed"
         * case this whole feature is for -- and until now that produced a log with nothing to tell
         * anyone it was there. JOptionPane with a null parent works before any window exists. */
        /* invokeLater, exactly like onErrorLogged, and for a reason that is not stylistic: the
         * notifier runs on whatever thread wrote to stderr, WITH the System.err PrintStream monitor
         * held. A modal dialog opened directly here would keep that monitor for as long as the
         * dialog is up, so any other thread that printed would block on it -- and if that thread is
         * the event thread, the dialog can never be painted or dismissed and the app is wedged.
         * Handing it to the event queue releases the monitor immediately. */
        ErrorLog.setNotifier(log -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                    "SuperCC hit an error while starting up.\n\nDetails were written to:\n" + log
                    + "\n\nOpen that file and paste its contents into a bug report. It contains paths\n"
                    + "from your own machine, including your Windows user name, so read it first.",
                    "SuperCC", JOptionPane.ERROR_MESSAGE)));

        SwingUtilities.invokeLater(() -> initialise(args));
    }

}
