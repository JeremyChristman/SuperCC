package graphics;

import emulator.EmulatorKeyListener;
import emulator.SavestateManager;
import emulator.SuperCC;
import game.Ruleset;
import io.SuccPaths;
import util.TreeNode;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Gui extends JFrame{
    private JPanel mainPanel;
    private JPanel rightContainer;
    private JPanel gamePanel;
    private JPanel levelPanel;
    private JPanel inventoryPanel;
    private JPanel movePanel;
    private JPanel lastActionPanel;
    private JPanel leftPanel;
    private JSlider timeSlider;
    private JSlider speedSlider;
    private JPanel sliderPanel;
    private JButton playButton;
    private final MenuBar menuBar;
    
    static final int DEFAULT_TILE_WIDTH = 20;
    static final int DEFAULT_TILE_HEIGHT = 20;
    public static final TileSheet DEFAULT_TILESHEET = TileSheet.CCEDIT_TW;

    private final SuperCC emulator;
    
    public JSlider getTimeSlider(){
        return timeSlider;
    }

    public static final Color DARK_GREY = new Color(0x3C3F41);
    
    public GamePanel getGamePanel() {
        return (GamePanel) gamePanel;
    }
    
    public LevelPanel getLevelPanel() {
        return (LevelPanel) levelPanel;
    }
    
    public MovePanel getMovePanel() {
        return (MovePanel) movePanel;
    }
    
    public LastActionPanel getLastActionPanel() {
        return (LastActionPanel) lastActionPanel;
    }
    
    public InventoryPanel getInventoryPanel() {
        return (InventoryPanel) inventoryPanel;
    }
    
    public JPanel getRightContainer() {
        return rightContainer;
    }
    
    public JButton getPlayButton() {
        return playButton;
    }
    
    private void createUIComponents() {
        playButton = new JButton();
        playButton.setOpaque(false);
        playButton.setBorder(null);
        levelPanel = new LevelPanel();
        inventoryPanel = new InventoryPanel();
        movePanel = new MovePanel();
        gamePanel = new SmallGamePanel(32, 32);
        gamePanel.setPreferredSize(new Dimension(32*DEFAULT_TILE_WIDTH, 32*DEFAULT_TILE_HEIGHT));
        lastActionPanel = new LastActionPanel();
        speedSlider = new JSlider(0, SavestateManager.NUM_SPEEDS - 1);
        speedSlider.setBackground(DARK_GREY);
        speedSlider.setUI(new BasicSliderUI(speedSlider));
        timeSlider = new JSlider(0, 0);
        timeSlider.setBackground(DARK_GREY);
        timeSlider.setUI(new BasicSliderUI(timeSlider));
        try {
            ((GamePanel) gamePanel).initialise(emulator, DEFAULT_TILESHEET.getTileSheets(emulator, DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT), DEFAULT_TILESHEET, DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT);  // aen-exempt: upstream method name
            changePlayButton(false);
        }
        catch (IOException e){
            emulator.throwError("Error loading tileset: "+e.getMessage());
            try {
                ((GamePanel) gamePanel).initialise(emulator,  // aen-exempt: upstream method name
                        new BufferedImage[] {ImageIO.read(Objects.requireNonNull(getClass().getResource("/resources/tw-edit-overlay.png"))), //complete backfall in case of emergencies
                                ImageIO.read(Objects.requireNonNull(getClass().getResource("/resources/tw-edit-tiles.png")))},
                        TileSheet.CCEDIT_TW, DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT);
            }
            catch (IOException ignored){ }
        }
    }
    
    public void updateTimeSlider(SavestateManager manager) {
        List<TreeNode<byte[]>> playbackNodes = manager.getPlaybackNodes();
        timeSlider.setMaximum(playbackNodes.size() - 1);
        timeSlider.setValue(manager.getPlaybackIndex());
    }
    
    public void repaintRightContainer(){
        levelPanel.repaint();
        inventoryPanel.repaint();
        lastActionPanel.repaint();
        movePanel.repaint();
    }

    public void changePlayButton(boolean paused) {
        try {
            if (paused)
                playButton.setIcon(new ImageIcon(ImageIO.read(Objects.requireNonNull(getClass().getResource("/resources/icons/play.gif")))));
            else
                playButton.setIcon(new ImageIcon(ImageIO.read(Objects.requireNonNull(getClass().getResource("/resources/icons/pause.gif")))));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void repaint(boolean fromScratch){
        if (!emulator.isLevelLoaded())
            return;
        updateTimeSlider(emulator.getSavestates());
        getGamePanel().updateGraphics(fromScratch);
        leftPanel.repaint();
        gamePanel.repaint();
        changePlayButton(emulator.getSavestates().isPaused());
        repaintRightContainer();
    }

    public void swapRulesetTilesheet(Ruleset ruleset) {
        if (ruleset == Ruleset.CURRENT || ruleset == null)
            return;
        try {
            SuccPaths paths = emulator.getPaths();
            int tilesheetNum;
            if (ruleset == Ruleset.MS)
                tilesheetNum = paths.getMSTilesetNum();
            else
                tilesheetNum = paths.getLynxTilesetNum();
            /* MOD (Jeremy, upstream merge): through TileSheet.load, as at startup. A saved custom
             * tileset whose images are gone, or a number this version does not have, draws the
             * default tileset instead -- with the reason said once a session, not at every set
             * opened -- where this used to index values() directly and, when the images failed,
             * keep the previous ruleset's tileset under a message that named no reason. The radio
             * mark follows what is drawn. */
            TileSheet.Choice choice = TileSheet.load(emulator, tilesheetNum, DEFAULT_TILESHEET,
                    paths.getTileSizes()[0], paths.getTileSizes()[1]);
            TileSheet tileSheet = choice.sheet();
            BufferedImage[] tilesetImages = choice.images();
            if (choice.problem() != null) emulator.throwErrorOnce(choice.problem());
            menuBar.tilesetButtons[tileSheet.ordinal()].setSelected(true);

            getGamePanel().initialise(emulator, tilesetImages, tileSheet, getGamePanel().getTileWidth(), getGamePanel().getTileHeight());  // aen-exempt: upstream method name
            getInventoryPanel().initialise(emulator);  // aen-exempt: upstream method name
            repaint(true);
        }
        catch (IOException e1) {
            emulator.throwError("Could not load relevant tilesheet");
        }
    }

    public Gui(SuperCC emulator) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e){
            e.printStackTrace();
        }
        try {
            setIconImage(ImageIO.read(Objects.requireNonNull(getClass().getResource("/resources/icons/windowIcon.png"))));
        }
        catch (IOException ignored) {
        }
        playButton.addActionListener((e) -> {
            if (emulator.getSavestates() == null)
                return;
            emulator.getMainWindow().requestFocus();
            emulator.getSavestates().togglePause();
            if (emulator.getSavestates().isPaused()) {
                emulator.showAction("Pausing solution playback");
                changePlayButton(false);
            }
            else {
                emulator.showAction("Playing solution");
                changePlayButton(true);
                new Thread(() -> emulator.getSavestates().play(emulator)).start();
            }
        });
        speedSlider.addChangeListener((e) -> {
            if (emulator.getSavestates() == null)
                return;
            emulator.getSavestates().setPlaybackSpeed(speedSlider.getValue());
            emulator.getMainWindow().requestFocus();
        });
        timeSlider.addChangeListener((e) -> {
            if (emulator.getSavestates() == null)
                return;
            if (timeSlider.getValueIsAdjusting()) {
                emulator.getSavestates().playbackRewind(timeSlider.getValue());
                emulator.getLevel().load(emulator.getSavestates().getSavestate());
                emulator.showAction("Solution Rewind");
                emulator.repaint(false);
                emulator.getMainWindow().requestFocus();
            }
        });
        this.emulator = emulator;
        leftPanel.setBackground(DARK_GREY);
        sliderPanel.setBackground(DARK_GREY);
        getMovePanel().setEmulator(emulator);
        getLevelPanel().setEmulator(emulator);
        getInventoryPanel().initialise(emulator);  // aen-exempt: upstream method name
        getInventoryPanel().setOpaque(true);
        getGamePanel().setEmulator(emulator);
        setContentPane(mainPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        menuBar = new MenuBar(this, emulator);
        setJMenuBar(menuBar);
        pack();
        setVisible(true);
        rightContainer.setBackground(Color.DARK_GRAY);
        levelPanel.setBackground(Color.DARK_GRAY);
        inventoryPanel.setBackground(Color.DARK_GRAY);
        movePanel.setBackground(Color.DARK_GRAY);
        lastActionPanel.setBackground(Color.DARK_GRAY);
        setFocusable(true);
        gamePanel.setFocusable(true);
        EmulatorKeyListener keyListener = new EmulatorKeyListener(emulator);
        addKeyListener(keyListener);
        emulator.setControls(keyListener);
    }
}
