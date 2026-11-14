package graphics;

import emulator.SavestateManager;
import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Direction;
import game.Level;
import game.Ruleset;
import game.Step;
import io.SuccPaths;
import io.TWSWriter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import tools.*;
import util.CharList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.awt.event.KeyEvent.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

class MenuBar extends JMenuBar{

    final SuperCC emulator;
    final Gui window;
    final LevelMenu levelMenu;
    final SolutionMenu solutionMenu;
    final TWSMenu twsMenu;
    final ViewMenu viewMenu;
    final ToolMenu toolMenu;
    final CheatMenu cheatMenu;
//    final HelpMenu helpMenu;

    JRadioButton[] tilesetButtons;
    
    private void addIcon(JMenuItem m, String path){
        try {
            m.setIcon(new ImageIcon(ImageIO.read(Objects.requireNonNull(getClass().getResource(path)))));
        }
        catch (Exception ignored) {}
    }

    private class LevelMenu extends JMenu{
        LevelMenu(){
            super("Level");

            JMenuItem openLevelset = worksWithoutALevel(new JMenuItem("Open levelset"));  // MOD (Jeremy, fix layer, UB-11)
            openLevelset.addActionListener(e -> {
                File levelset = openFile(emulator.getPaths().getLevelsetFolderPath(), "Levelset file", "dat", "ccl");
                if (levelset != null) {
                    emulator.getPaths().setLevelsetFolderPath(levelset.getParent());
                    emulator.openLevelset(levelset);
                }
            });
            openLevelset.setAccelerator(KeyStroke.getKeyStroke(VK_O, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
            addIcon(openLevelset, "/resources/icons/open.gif");
            add(openLevelset);

            add(new JSeparator());
    
            JMenuItem restart = new JMenuItem("Restart");
            restart.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded()) {
                    emulator.getSavestates().restart();
                    emulator.getLevel().load(emulator.getSavestates().getSavestate());
                    emulator.showAction("Restarted Level");
                    window.repaint(false);
                }
            });
            restart.setAccelerator(KeyStroke.getKeyStroke(VK_R, InputEvent.CTRL_DOWN_MASK));
            addIcon(restart, "/resources/icons/restart.gif");
            add(restart);
    
            JMenuItem next = new JMenuItem("Next");
            next.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded())
                    emulator.loadLevel(emulator.getLevel().getLevelNumber() + 1);
            });
            next.setAccelerator(KeyStroke.getKeyStroke(VK_N, InputEvent.CTRL_DOWN_MASK));
            addIcon(next, "/resources/icons/right.gif");
            add(next);

            JMenuItem previous = new JMenuItem("Previous");
            previous.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded())
                    emulator.loadLevel(emulator.getLevel().getLevelNumber() - 1);
            });
            previous.setAccelerator(KeyStroke.getKeyStroke(VK_P, InputEvent.CTRL_DOWN_MASK));
            addIcon(previous, "/resources/icons/left.gif");
            add(previous);

            JMenuItem goTo = new JMenuItem("Go to...");
            goTo.addActionListener(e -> {
                if (!SuperCC.areToolsRunning()) {
                    String s = JOptionPane.showInputDialog(window, "Choose a level number");
                    if (s.length() == 0) return;
                    try {
                        int n = Integer.parseInt(s);
                        emulator.loadLevel(n, 0, Step.EVEN, true, Ruleset.CURRENT, Direction.UP);
                    } catch (NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(window, "Please Enter a Whole Number");
                    }
                }
            });
            goTo.setAccelerator(KeyStroke.getKeyStroke(VK_G, InputEvent.CTRL_DOWN_MASK));
            addIcon(goTo, "/resources/icons/goto.gif");
            add(goTo);

            add(new JSeparator());

            JMenuItem changeStep = new JMenuItem("Change stepping value");
            changeStep.addActionListener(e -> {
                // (72476bc guarded its neighbors but not this item; requireALevel() covers it --
                // MOD (Jeremy, fix layer, UB-11).)
                if (!SuperCC.areToolsRunning()) {
                    Level oldLevel = emulator.getLevel();
                    Step newStep = Step.EVEN;
                    if (oldLevel.ticksPerMove() == 2 && oldLevel.getStep() == Step.EVEN)
                        newStep = Step.ODD;
                    else if (oldLevel.ticksPerMove() != 2)
                        newStep = oldLevel.getStep().next();
                    emulator.loadLevel(oldLevel.getLevelNumber(), oldLevel.getRngSeed(), newStep, true,
                            Ruleset.CURRENT, oldLevel.getInitialRFFDirection());
                }
            });
            changeStep.setAccelerator(KeyStroke.getKeyStroke(VK_F1, 0));
            add(changeStep);

            JMenuItem rngSeed = new JMenuItem("Set RNG Seed");
            rngSeed.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded()) {
                    String s = JOptionPane.showInputDialog(window, "Choose a starting seed");
                    if (s.equals("")) return;
                    try {
                        Level oldLevel = emulator.getLevel();
                        int n = Integer.parseInt(s);
                        emulator.loadLevel(oldLevel.getLevelNumber(), n, oldLevel.getStep(), true,
                                Ruleset.CURRENT, oldLevel.getInitialRFFDirection());
                    } catch (NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(window, "Please Enter a Whole Number");
                    }
                }
            });
            rngSeed.setAccelerator(KeyStroke.getKeyStroke(VK_F2, 0));
            addIcon(rngSeed, "/resources/icons/rng.gif");
            add(rngSeed);

            JMenuItem changeRules = new JMenuItem("Change ruleset");
            changeRules.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded()) {
                    Level level = emulator.getLevel();
                    Ruleset ruleset = level.getRuleset().swap();
                    emulator.loadLevel(level.getLevelNumber(), level.getRngSeed(), level.getStep(), false,
                            ruleset, level.getInitialRFFDirection());
                    window.swapRulesetTilesheet(ruleset);
                }
            });
            changeRules.setAccelerator(KeyStroke.getKeyStroke(VK_F3, 0));
            addIcon(changeRules, "/resources/icons/change.gif");
            add(changeRules);

            JMenuItem changeInitialSlide = new JMenuItem("Change Initial RFF Direction");
            changeInitialSlide.addActionListener(e -> {
                if (!SuperCC.areToolsRunning() && emulator.isLevelLoaded()) {
                    Level level = emulator.getLevel();
                    emulator.loadLevel(level.getLevelNumber(), level.getRngSeed(), level.getStep(), true,
                            level.getRuleset(), level.getInitialRFFDirection().turn(Direction.TURN_RIGHT));
                }
            });
            changeInitialSlide.setAccelerator(KeyStroke.getKeyStroke(VK_F4, 0));
            addIcon(changeInitialSlide, "/resources/icons/RFF.png");
            add(changeInitialSlide);
        }
    }
    
    private class SolutionMenu extends JMenu{
        SolutionMenu(){
            super("Solution");

            JMenuItem saveAs = new JMenuItem("Save as");
            saveAs.setAccelerator(KeyStroke.getKeyStroke(VK_S, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
            saveAs.addActionListener(event -> {
                Level l = emulator.getLevel();
                Solution solution = new Solution(emulator.getSavestates().getMoveList(), l.getRngSeed(), l.getStep(), l.getRuleset(), l.getInitialRFFDirection());
                saveNewFile(solution.toString().getBytes(UTF_8), emulator.getJSONPath(), "json");
            });
            addIcon(saveAs, "/resources/icons/saveAs.gif");
            add(saveAs);
    
            JMenuItem save = new JMenuItem("Save");
            save.setAccelerator(KeyStroke.getKeyStroke(VK_S, InputEvent.CTRL_DOWN_MASK));
            save.addActionListener(event -> {
                Level l = emulator.getLevel();
                Solution solution = new Solution(emulator.getSavestates().getMoveList(), l.getRngSeed(), l.getStep(), l.getRuleset(), l.getInitialRFFDirection());
                try{
                    FileOutputStream fos = new FileOutputStream(emulator.getJSONPath());
                    fos.write(solution.toString().getBytes(UTF_8));
                    fos.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                    emulator.throwError("Could not save file: "+e.getMessage());
                }
            });
            addIcon(save, "/resources/icons/save.gif");
            add(save);
    
            JMenuItem open = new JMenuItem("Open");
            open.setAccelerator(KeyStroke.getKeyStroke(VK_O, InputEvent.CTRL_DOWN_MASK));
            open.addActionListener(event -> {
                byte[] fileBytes = openFileBytes(emulator.getJSONPath(), "JSON route file", "json", "route");
                if (fileBytes != null) {
                    Solution solution;
                    JSONParser parser = new JSONParser();
                    String fileString = new String(fileBytes, UTF_8);
                    JSONObject json;
                    try {
                        json = (JSONObject) parser.parse(fileString);
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Invalid solution file:\n" + emulator.getJSONPath());
                    }
                    String encoding = (String) json.get(Solution.ENCODE);

                    if ("UTF-8".equals(encoding)) solution = Solution.fromJSON(fileString);
                    else solution = Solution.fromJSON(new String(fileBytes, ISO_8859_1));
                    solution.load(emulator);
                }
            });
            addIcon(open, "/resources/icons/open.gif");
            add(open);
            
            JMenuItem seedSearch = new JMenuItem("Search for seeds");
            seedSearch.addActionListener(event -> {
                byte[] fileBytes = openFileBytes(emulator.getJSONPath(), "JSON route file", "json", "route");
                if (fileBytes != null) {
                    Solution solution;
                    JSONParser parser = new JSONParser();
                    String fileString = new String(fileBytes, UTF_8);
                    JSONObject json;
                    try {
                        json = (JSONObject) parser.parse(fileString);
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Invalid solution file:\n" + emulator.getJSONPath());
                    }
                    String encoding = (String) json.get(Solution.ENCODE);

                    if ("UTF-8".equals(encoding)) solution = Solution.fromJSON(fileString);
                    else solution = Solution.fromJSON(new String(fileBytes, ISO_8859_1));
                    new SeedSearch(emulator, solution);
                }
            });
            addIcon(seedSearch, "/resources/icons/open.gif");
            add(seedSearch);
            
            addSeparator();
    
            JMenuItem copy = new JMenuItem("Copy solution");
            copy.setAccelerator(KeyStroke.getKeyStroke(VK_C, InputEvent.CTRL_DOWN_MASK));
            copy.addActionListener(event -> {
                Level level = emulator.getLevel();
                Solution solution = new Solution(emulator.getSavestates().getMoveList(), level.getRngSeed(), level.getStep(), level.getRuleset(), level.getInitialRFFDirection());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(solution.toString()), null);
                emulator.showAction("Copied solution");
                emulator.getMainWindow().repaint(false);
            });
            addIcon(copy, "/resources/icons/copy.gif");
            add(copy);
    
            JMenuItem paste = new JMenuItem("Paste solution");
            paste.setAccelerator(KeyStroke.getKeyStroke(VK_V, InputEvent.CTRL_DOWN_MASK));
            paste.addActionListener(event -> {
                Level level = emulator.getLevel();
                Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(this);
                try {
                    Solution.fromJSON((String) t.getTransferData(DataFlavor.stringFlavor)).load(emulator);
                    emulator.showAction("Pasted solution");
                    emulator.getMainWindow().repaint(false);
                }
                catch (IllegalArgumentException e){ //If the clipboard isn't an entire JSON solution it might be raw moves, which should be put in
                    try {
                        char[] moves = (t.getTransferData(DataFlavor.stringFlavor)).toString().toCharArray();
                        for (int i = 0; i < moves.length; i++) {
                            char ch = moves[i];
                            if (level.getChip().isDead())
                                break;
                            char c = SuperCC.lowerCase(ch);
                            if (emulator.tick(c, TickFlags.PRELOADING))
                                i += level.ticksPerMove() - 1;
                        }
                        emulator.repaint(true);
                        emulator.showAction("Pasted moves");
                    }
                    catch (UnsupportedFlavorException | IOException e2) {
                        emulator.throwError(e2.getMessage());
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            });
            addIcon(paste, "/resources/icons/paste.gif");
            add(paste);
    
            addSeparator();

            JMenuItem copyFromStart = new JMenuItem("Copy all previous moves");
            copyFromStart.addActionListener(e -> {
                Level level = emulator.getLevel();
                int currentIndex = emulator.getSavestates().getPlaybackIndex();
                Solution solution = new Solution(emulator.getSavestates().getMoveList().sublist(0, currentIndex),
                        level.getRngSeed(), level.getStep(), level.getRuleset(), level.getInitialRFFDirection());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new String(solution.basicMoves)), null);
                emulator.showAction("Copied moves");
            });
            add(copyFromStart);

            JMenuItem copyToEnd = new JMenuItem("Copy all future moves");
            copyToEnd.addActionListener(e -> {
                Level level = emulator.getLevel();
                int currentIndex = emulator.getSavestates().getPlaybackIndex();
                CharList moves = emulator.getSavestates().getMoveList();
                Solution solution = new Solution(moves.sublist(currentIndex, moves.size()),
                        level.getRngSeed(), level.getStep(), level.getRuleset(), level.getInitialRFFDirection());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new String(solution.basicMoves)), null);
                emulator.showAction("Copied moves");
            });
            add(copyToEnd);

            JMenu copyMacroMenu = new JMenu("Copy macro moves");
            copyMacroMenu.addMenuListener(new MenuListener() {
                @Override
                public void menuSelected(MenuEvent e) {
                    copyMacroMenu.removeAll(); //clear the previous menu
                    // MOD (Jeremy, fix layer, UB-11): merely hovering here with no level open threw
                    // (getSavestates() is null). requireALevel() wraps only ActionListeners, so this
                    // one MenuListener checks for itself.
                    if (!emulator.isLevelLoaded()) return;

                    CharList[] macros = emulator.getSavestates().getMacros();
                    Level level = emulator.getLevel();
                    for (int i = 0; i < macros.length; i++) {
                        CharList macro = macros[i];
                        if (macro == null || macro.size() == 0)
                            continue;

                        JMenuItem copyMacro = new JMenuItem("Copy moves from macro #" + i);
                        copyMacro.addActionListener(e2 -> {
                            Solution solution = new Solution(macro, level.getRngSeed(), level.getStep(),
                                    level.getRuleset(), level.getInitialRFFDirection());
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new String(solution.basicMoves)), null);
                            emulator.showAction("Copied moves from macro");
                        });
                        copyMacroMenu.add(copyMacro);
                    }
                }
                @Override
                public void menuDeselected(MenuEvent e) {
                }
                @Override
                public void menuCanceled(MenuEvent e) {
                }
            });
            add(copyMacroMenu);

            addSeparator();
    
            JMenuItem saveSavestates = new JMenuItem("Save all states to disk");
            saveSavestates.addActionListener(event -> {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream out = new ObjectOutputStream(bos);
                    out.writeObject(emulator.getSavestates());
                    out.flush();
                    saveNewFile(bos.toByteArray(), emulator.getSerPath(), "ser");
                    out.close();
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    emulator.throwError("Could not save file:" + e.getMessage());
                }
            });
            addIcon(saveSavestates, "/resources/icons/saveAs.gif");
            add(saveSavestates);
    
            JMenuItem loadStates = new JMenuItem("Load states from disk");
            loadStates.addActionListener(event -> {
                try {
                    byte[] fileBytes = openFileBytes(emulator.getSerPath(), "Savestate file", "ser");
                    if (fileBytes != null) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes);
                        SavestateManager savestates = (SavestateManager) new ObjectInputStream(bis).readObject();
                        if(Arrays.equals(emulator.getLevel().getTitle().getBytes("Windows-1252"), savestates.getLevelTitle())) {
                            savestates.setEmulator(emulator);
                            savestates.setNode(emulator.getSavestates().getNode());
                            savestates.setMoves(emulator.getSavestates().getMoveList().clone());
                            savestates.setPlaybackIndex(emulator.getSavestates().getPlaybackIndex());
                            savestates.setPlaybackNodes(emulator.getSavestates().getNode());
                            savestates.setPlaybackSpeed(emulator.getSavestates().getPlaybackSpeed());
                            emulator.setSavestates(savestates);
                        }
                        else {
                            String levelString = new String(savestates.getLevelTitle());
                            emulator.throwMessage("Cannot load savestates for level " + levelString + " into current level.");
                        }
                    }
                }
                catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                    emulator.throwError("could not open file: " + e.getMessage());
                }
            });
            addIcon(loadStates, "/resources/icons/open.gif");
            add(loadStates);
        }
    }

    private class TWSMenu extends JMenu{
        TWSMenu(){
            super("TWS");

            JMenuItem newTWS = new JMenuItem("Write solution to new tws");
            newTWS.addActionListener(event -> {
                Level level = emulator.getLevel();
                Solution solution = new Solution(emulator.getSavestates().getMoveList(),
                        level.getRngSeed(),
                        level.getStep(), level.getRuleset(), level.getInitialRFFDirection());

                /* MOD (Jeremy, jc-10): remember the folder actually written to, so the next tws
                 * chooser opens there -- see SuccPaths.getTWSPath().
                 * ⚠ saveNewFile() returns NULL when the save dialog is canceled, and getParent()
                 * is null for a file with no parent. The pre-jc-8 code dereferenced the first of
                 * those unconditionally and crashed; both are checked here. */
                Path tws = saveNewFile(TWSWriter.write(level, solution, emulator.getSavestates()), emulator.getPaths().getTWSPath(), "tws");
                assert tws != null;
                if (tws != null && tws.getParent() != null) {
                    emulator.getPaths().setTWSPath(tws.getParent().toString());
                }
            });
            add(newTWS);
            addIcon(newTWS, "/resources/icons/new.gif");

            JMenuItem openTWS = worksWithoutALevel(new JMenuItem("Open tws"));  // it only chooses the file (MOD (Jeremy, fix layer, UB-11))
            openTWS.addActionListener(e -> {
                File file = openFile(emulator.getPaths().getTWSPath(), "TWS solution file", "tws");
                if (file != null) {
                    // MOD (Jeremy, jc-10): remember this folder too -- see the comment above.
                    // getParent() is null for a file with no parent directory, so check it.
                    if (file.getParent() != null) emulator.getPaths().setTWSPath(file.getParent());
                    emulator.setTWSFile(file);
                }
            });
            add(openTWS);
            addIcon(openTWS, "/resources/icons/open.gif");

            add(new JSeparator());

            JMenuItem loadSolution = new JMenuItem("Load solution");
            loadSolution.addActionListener(event -> {
                try {
                    emulator.twsReader.readSolution(emulator.getLevel()).load(emulator);
                } catch (IOException | NullPointerException e) {
                    emulator.throwError("Error while loading solution: " + e.getMessage());
                }
            });
            add(loadSolution);
            addIcon(loadSolution, "/resources/icons/skip.gif");

            JMenuItem verify = new JMenuItem("Verify tws");
            verify.addActionListener(e -> new VerifyTWS(emulator));
            add(verify);
        }
    }

    private class ViewMenu extends JMenu{
        ButtonGroup tilesetButtonGroup;

        ViewMenu(){
            super("View");
    
            JMenu tileset = new JMenu("Tileset");
            tilesetButtonGroup = new ButtonGroup();
            String[] tilesetNames = TileSheet.getNames();
            TileSheet[] tileSheets = TileSheet.values();
            tilesetButtons = new JRadioButton[tilesetNames.length];
            for (int i = 0; i < tilesetNames.length; i++) {
                /* MOD (Jeremy, fix layer, UB-11): works with no level open. requireALevel() would
                 * otherwise skip this listener while the radio mark still moved, so the menu would
                 * show a tileset that was neither drawn nor saved. With no level there is no ruleset
                 * to file the choice under, so it is saved as the MS tileset (below) -- the one
                 * SuperCC draws at startup, which is what is on screen in exactly that state. */
                JRadioButton tilesheetButton = worksWithoutALevel(new JRadioButton(tilesetNames[i]));
                TileSheet tileSheet = tileSheets[i];
                tilesetButtons[i] = tilesheetButton;
                tilesheetButton.addActionListener(e -> {
                    int tileWidth, tileHeight;
                    try {
                        tileWidth = emulator.getMainWindow().getGamePanel().getTileWidth();
                        tileHeight = emulator.getMainWindow().getGamePanel().getTileHeight();
                    }
                    catch (NullPointerException exc) {
                        tileWidth = Gui.DEFAULT_TILE_WIDTH;
                        tileHeight = Gui.DEFAULT_TILE_HEIGHT;
                    }
                    try {
                        if (tileSheet == TileSheet.CUSTOM) {
                            SuccPaths paths = emulator.getPaths();
                            String basePath;
                            String overlayPath;
                            String[] customImages = paths.getCustomTilesetImages();
                            if (customImages != null) {
                                basePath = customImages[0];
                                overlayPath = customImages[1];
                            } else {
                                basePath = "";
                                overlayPath = "";
                            }
                            File baseImage = openFile(basePath, "Tileset base image", "png");
                            File overlayImage = openFile(overlayPath, "Tileset overlay image", "png");
                            if (baseImage == null || overlayImage == null) throw new IOException("No tileset image provided");
                            basePath = baseImage.getAbsolutePath();
                            overlayPath = overlayImage.getAbsolutePath();
                            paths.setCustomTilesetImages(basePath, overlayPath);
                        }
                        BufferedImage[] tilesetImages = tileSheet.getTileSheets(emulator,tileWidth, tileHeight);
                        window.getGamePanel().initialise(emulator, tilesetImages, tileSheet,  // aen-exempt: upstream method name
                                window.getGamePanel().getTileWidth(), window.getGamePanel().getTileHeight());
                        window.getInventoryPanel().initialise(emulator);  // aen-exempt: upstream method name
                        window.repaint(true);

                        // MOD (Jeremy, fix layer, UB-11): no level open -> the MS tileset (see above).
                        if (!emulator.isLevelLoaded() || emulator.getLevel().getRuleset() == Ruleset.MS)
                            emulator.getPaths().setMSTilesetNum(tileSheet.ordinal());
                        else
                            emulator.getPaths().setLynxTilesetNum(tileSheet.ordinal());
                    } catch (IOException exc) {
                        emulator.throwError(exc.getMessage());
                    }
                });
                tilesetButtonGroup.add(tilesheetButton);
                tileset.add(tilesheetButton);
            }
            add(tileset);
    
            JMenu tileSize = new JMenu("Tile size");
            ButtonGroup tileSizes = new ButtonGroup();
            int[] sizes = new int[] {16, 20, 24, 32};
            for (int size : sizes) {
                JRadioButton sizeButton = worksWithoutALevel(new JRadioButton(size + "x" + size));  // MOD (Jeremy, fix layer, UB-11)
                sizeButton.addActionListener((e) -> {
                    try {
                        TileSheet ts;
                        try {
                            ts = emulator.getMainWindow().getGamePanel().getTileSheet();
                        } catch (NullPointerException npe) {
                            ts = Gui.DEFAULT_TILESHEET;
                        }
                        SmallGamePanel gamePanel = (SmallGamePanel) emulator.getMainWindow().getGamePanel();
                        emulator.getMainWindow().getGamePanel().initialise(emulator, ts.getTileSheets(emulator, size, size), ts, size, size);  // aen-exempt: upstream method name
                        window.getInventoryPanel().initialise(emulator);  // aen-exempt: upstream method name
                        window.setSize(size * gamePanel.getWindowSizeX(), size * gamePanel.getWindowSizeY());
                        window.getGamePanel().setPreferredSize(new Dimension(size * gamePanel.getWindowSizeX(), size * gamePanel.getWindowSizeY()));
                        window.getGamePanel().setSize(size * gamePanel.getWindowSizeX(), size * gamePanel.getWindowSizeY());
                        window.pack();
                        window.repaint(true);

                        emulator.getPaths().setTileSizes(new int[]{size, size});
                    } catch (IOException e1) {
                        emulator.throwError(e1.getMessage());
                    }
                });
                tileSizes.add(sizeButton);
                tileSize.add(sizeButton);
            }
            // MOD (Jeremy, fix layer, UB-11): needs no level, now that ChooseTileSize's dead
            // getKeys() line is gone.
            JRadioButton sizeButton = worksWithoutALevel(new JRadioButton("custom"));
            sizeButton.addActionListener(e -> new ChooseTileSize(emulator, window.getGamePanel().getTileWidth(), 0, 256));
            tileSizes.add(sizeButton);
            tileSize.add(sizeButton);
            add(tileSize);
            
            JMenu windowSize = worksWithoutALevel(new JMenu("Game window size"));  // MOD (Jeremy, fix layer, UB-11): all of it
            ButtonGroup windowSizes = new ButtonGroup();
            sizes = new int[] {9, 32};
            for (int size : sizes) {
                sizeButton = new JRadioButton(size + "x" + size);
                sizeButton.addActionListener((e) -> {
                    SmallGamePanel gamePanel = (SmallGamePanel) emulator.getMainWindow().getGamePanel();
                    gamePanel.setWindowSize(size, size);
                    int tileWidth = gamePanel.getTileWidth(), tileHeight = gamePanel.getTileHeight();
                    window.setSize(200 + tileWidth * size, 200 + tileHeight * size);
                    window.getGamePanel().setPreferredSize(new Dimension(tileWidth * size, tileHeight * size));
                    window.getGamePanel().setSize(tileWidth * size, tileHeight * size);
                    window.pack();
                    window.repaint(true);
                });
                windowSizes.add(sizeButton);
                windowSize.add(sizeButton);
            }
            sizeButton = new JRadioButton("custom");
            sizeButton.addActionListener(e -> new ChooseWindowSize(emulator));
            windowSizes.add(sizeButton);
            windowSize.add(sizeButton);
            add(windowSize);
            
            add(new JSeparator());
    
            String[] setterNames = new String[] {
                "Show Monster List",
                "Show Slip List",
                "Show Clone Connections",
                "Show Trap Connections",
                "Show Move History"
            };
            
            List<Consumer<Boolean>> setters = Arrays.asList(
                b -> window.getGamePanel().setMonsterListVisible(b),
                b -> window.getGamePanel().setSlipListVisible(b),
                b -> window.getGamePanel().setClonesVisible(b),
                b -> window.getGamePanel().setTrapsVisible(b),
                b -> window.getGamePanel().setHistoryVisible(b)
            );
            
            for (int i = 0; i < setterNames.length; i++){
                JToggleButton b = worksWithoutALevel(new JToggleButton(setterNames[i]));  // MOD (Jeremy, fix layer, UB-11)
                Consumer<Boolean> setter = setters.get(i);
                if (i == 0 || i == 1 || i == 2 || i == 3) b.setSelected(true); //MOD (Jeremy): Clone (2) & Trap (3) buttons default ON too, matching the backend state in GamePanel.java
                b.addActionListener(e -> {
                    setter.accept(((AbstractButton) e.getSource()).isSelected());
                    window.repaint(true);
                });
                add(b);
            }

            add(new JSeparator());

            String[] setterHUDNames = new String[] { //Should probably rename this sometime
                    "Switch Decimal Notation",
            };

            List<Consumer<Boolean>> HUDSetters = List.of( //Yes yes this and the next are stupid as its only one value, but this also future proofs it
                    b -> window.getLevelPanel().changeNotation(b)
            );

            List<Boolean> selected = List.of(
                    emulator.getPaths().getTWSNotation()
            );

            for (int i = 0; i < setterHUDNames.length; i++){
                JToggleButton b = worksWithoutALevel(new JToggleButton(setterHUDNames[i]));  // MOD (Jeremy, fix layer, UB-11)
                Consumer<Boolean> setter = HUDSetters.get(i);
                b.setSelected(selected.get(i));
                b.addActionListener(e -> {
                    setter.accept(((AbstractButton) e.getSource()).isSelected());
                    window.repaint(false);
                });
                add(b);
            }
        }
    }
    
    private class ToolMenu extends JMenu{
        ToolMenu() {
            super("Tools");
    
            JMenuItem controls = worksWithoutALevel(new JMenuItem("Controls"));  // MOD (Jeremy, fix layer, UB-11)
            controls.addActionListener(e -> new SwingWorker<Void, Void>(){
                @Override
                protected Void doInBackground() {
                    new ControlGUI(emulator);
                    return null;
                }
            }.execute()) ;
            add(controls);
    
            JMenuItem gif = new JMenuItem("Record gif");
            gif.addActionListener(e -> new GameGifRecorder(emulator));
            addIcon(gif, "/resources/icons/video.gif");
            add(gif);

            JMenuItem variations = new JMenuItem("Variation testing");
            variations.addActionListener(e -> {
                if (emulator.getLevel().getRuleset() == Ruleset.MS)
                    new VariationTesting(emulator);
                else
                    emulator.throwError("Variation testing is only supported in MS currently.");
            });
            add(variations);

            JMenuItem tsp = new JMenuItem("TSP Solver");
            tsp.addActionListener(e -> {
                if (emulator.getLevel().getRuleset() == Ruleset.MS)
                    new TSPGUI(emulator);
                else
                    emulator.throwError("TSP is only supported in MS currently.");
            });
            add(tsp);

        }
    }
    
    private class CheatMenu extends JMenu{
        CheatMenu() {
            super("Cheats");
    
            JMenuItem inventory = new JMenuItem("Change inventory");
            inventory.addActionListener(e -> new ChangeInventory(emulator));
            addIcon(inventory, "/resources/icons/green_key.gif");
            add(inventory);
    
            JMenuItem time = new JMenuItem("Change timer");
            time.addActionListener(e -> new ChangeTimer(emulator));
            add(time);

            JMenuItem monsterList = new JMenuItem("Change Monster List Positions");
            monsterList.addActionListener(e -> new MonsterlistRearrangeGUI(emulator, false));
            add(monsterList);

            JMenuItem slipList = new JMenuItem("Change Slip List Positions");
            slipList.addActionListener(e -> {
                if (emulator.getLevel().supportsSliplist())
                    new MonsterlistRearrangeGUI(emulator, true);
                else
                    emulator.throwError("The Slip List does not exist in this ruleset.");
            });
            add(slipList);
            
            add(new JSeparator());
    
            JMenuItem toggle = new JMenuItem("Press Green Button");
            toggle.addActionListener(e -> {
                emulator.getLevel().getCheats().pressGreenButton();
                emulator.getMainWindow().repaint(false);
            });
            addIcon(toggle, "/resources/icons/green_button.gif");
            add(toggle);
            
            JMenuItem tank = new JMenuItem("Press Blue Button");
            tank.addActionListener(e -> {
                emulator.getLevel().getCheats().pressBlueButton();
                emulator.getMainWindow().repaint(false);
            });
            addIcon(tank, "/resources/icons/blue_button.gif");
            add(tank);
            

        }
    }

//    private class HelpMenu extends JMenu{
//        HelpMenu() {
//            super("Help");
//
//            JMenuItem helpPopup = new JMenuItem("Help");
//            helpPopup.addActionListener(e -> new HelpWindow(emulator));
//            addIcon(helpPopup, "/resources/icons/help.gif");
//            add(helpPopup);
//
//            JMenuItem variationHelp = new JMenuItem("Variation Tester Documentation");
//            variationHelp.addActionListener(e -> {
//                if (Desktop.isDesktopSupported()) {
//                    try {
//                        Desktop.getDesktop().browse(new URI("https://supercc.bitbusters.club/VariationScriptDocumentation.pdf"));
//                    } catch (IOException | URISyntaxException ex) {
//                        ex.printStackTrace();
//                    }
//                }
//            });
//            add(variationHelp);
//
//            JMenuItem tspHelpPopup = new JMenuItem("TSP Solver Help");
//            tspHelpPopup.addActionListener(e -> new TSPHelpWindow(emulator));
//            addIcon(tspHelpPopup, "/resources/icons/help.gif");
//            add(tspHelpPopup);
//        }
//    }

    private Path saveNewFile(byte[] out, String path, String extension) {
        try {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter(extension, extension));
            File filePath = new File(path);
            if (filePath.isDirectory()) fc.setCurrentDirectory(filePath);
            else {
                fc.setCurrentDirectory(filePath.getParentFile());
                fc.setSelectedFile(filePath);
            }
            if (fc.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                String filename = file.toString();
                if (!filename.endsWith("." + extension)) filename += "." + extension;
                FileOutputStream fos = new FileOutputStream(filename);
                fos.write(out);
                fos.close();
                return new File(filename).toPath();
            }
        }
        catch (IOException e){
            e.printStackTrace();
            emulator.throwError("Could not save file: "+e.getMessage());
        }
        return null;
    }

    /**
     * MOD (Jeremy, jc-12): a file that simply is not there was reported by dumping a fifty-line JDK
     * stack trace to stderr and putting NoSuchFileException's message -- which is the bare path,
     * with no words around it -- in front of the user. That is the ordinary case of asking for a
     * solution before one has been saved, not a defect worth a stack trace. It was found because
     * jc-11's error log recorded it three times in two sessions on succsave\SokobanCCLP\65.
     *
     * ⚠ UPSTREAM BASELINE: jc-12 also made a CANCELED chooser a no-op here (openFile() returns
     * null). Upstream's 72476bc wraps that null in Objects.requireNonNull instead, and the baseline
     * keeps upstream's line, so canceling Solution > Open, the seed search or Load states throws
     * NullPointerException on the event thread again (BASELINE-ISSUES.md UB-13).
     */
    private byte[] openFileBytes(String path, String description, String... extensions) {
        File file = Objects.requireNonNull(openFile(path, description, extensions));
        try {
            return Files.readAllBytes(file.toPath());
        }
        catch (NoSuchFileException e) {
            // Expected whenever nothing has been saved for this level yet. No stack trace.
            emulator.throwError("There is no file:\n" + file);
        }
        catch (IOException e) {
            e.printStackTrace();
            emulator.throwError("Could not load file:\n" + e.getMessage());
        }
        return null;
    }

    private File openFile(String path, String description, String... extensions) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(description, extensions));
        File file = new File(path);
        if (file.isDirectory()) fc.setCurrentDirectory(file);
        else {
            fc.setCurrentDirectory(file.getParentFile());
            fc.setSelectedFile(new File(path));
        }
        if (fc.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile();
        }
        return null;
    }

    MenuBar(Gui window, SuperCC emulator){
        this.window = window;
        this.emulator = emulator;
        setPreferredSize(new Dimension(0, 24));
        setLocation(0, 0);
        levelMenu = new LevelMenu();
        solutionMenu = new SolutionMenu();
        twsMenu = new TWSMenu();
        viewMenu = new ViewMenu();
        toolMenu = new ToolMenu();
        cheatMenu = new CheatMenu();
//        helpMenu = new HelpMenu();
        add(levelMenu);
        add(solutionMenu);
        add(twsMenu);
        add(viewMenu);
        add(toolMenu);
        add(cheatMenu);
//        add(helpMenu);
        for (int i = 0; i < getMenuCount(); i++) requireALevel(getMenu(i));
    }

    /* MOD (Jeremy, fix layer, UB-11): ONE guard for the whole menu bar, instead of one per item.
     * jc-17's fix (merged as "upstream merge", reverted for the upstream baseline in jc-21),
     * re-applied alone.
     *
     * Every launch starts with no level set open, and most of these controls read the level or its
     * savestates the moment they run. Upstream's 72476bc guarded six of them by hand; clicking every
     * button in these menus with no level open still finds 28 that throw NullPointerException on the
     * event thread -- Level > Go to... and Change stepping value, Solution (Save, Copy, the state
     * files...), TWS, View > Tileset and Tile size > custom, Tools > Variation testing and TSP
     * Solver, and every cheat -- each one written to jc-11's error log, and Ctrl+S is a keystroke
     * away (BASELINE-ISSUES.md UB-11).
     *
     * So the rule is inverted: a button in these menus does NOTHING until a level is loaded, unless
     * it is marked worksWithoutALevel(...) where it is made (Open levelset, Open tws, the tilesets,
     * the tile sizes, the Game window size submenu, the View toggles, Controls). A new item made in
     * a menu's constructor, as every item is, is covered without anyone having to remember.
     * Accelerators go through the same listeners, so they are covered too. GuiWindowTest clicks
     * every button in the bar with no level open, and fails on any that throws, on a guarded one
     * that opens anything, and on an exempt one that does not do its job.
     *
     * Only the ActionListeners present when this constructor ends are wrapped. Swing's menu UIs
     * listen through other interfaces; the one MenuListener here (Copy macro moves) checks the
     * level itself, and the items it builds on hover exist only once a level is loaded, which
     * nothing unloads. A listener added to a menu item later would NOT be guarded (review). Nor
     * does the guard stop a toggle or radio item's own mark from moving: there are none in a
     * guarded menu, and one added there must be made to work without a level and exempted.
     *
     * Swing calls a button's listeners newest first, and getActionListeners() lists them in that
     * order too, so each is re-added from the END of that array: re-added in the order the array
     * gives, a button's listeners would run in the reverse of their order without the guard. jc-17
     * did that; no item has two listeners, so nothing ran backward, and this keeps it that way
     * (review of jc-28; GuiWindowTest checks the order). */
    private static final String WORKS_WITHOUT_A_LEVEL = "supercc.worksWithoutALevel";

    private static <T extends JComponent> T worksWithoutALevel(T c) {
        c.putClientProperty(WORKS_WITHOUT_A_LEVEL, Boolean.TRUE);
        return c;
    }

    private void requireALevel(JMenu menu) {
        if (menu == null || Boolean.TRUE.equals(menu.getClientProperty(WORKS_WITHOUT_A_LEVEL))) return;
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenu) { requireALevel((JMenu) c); continue; }
            if (!(c instanceof AbstractButton)) continue;
            AbstractButton b = (AbstractButton) c;
            if (Boolean.TRUE.equals(b.getClientProperty(WORKS_WITHOUT_A_LEVEL))) continue;
            ActionListener[] listeners = b.getActionListeners();
            for (int i = listeners.length - 1; i >= 0; i--) {   // oldest first: see above
                ActionListener l = listeners[i];
                b.removeActionListener(l);
                b.addActionListener(e -> { if (emulator.isLevelLoaded()) l.actionPerformed(e); });
            }
        }
    }

}
