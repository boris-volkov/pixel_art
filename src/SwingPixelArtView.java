import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;


public class SwingPixelArtView implements PixelArtView {
    private JFrame frame;
    private CanvasViewport canvasHolder;
    private ControlBar controlBar;
    private JPanel controlBarSlot;
    private JPanel topBarSlot;
    private JPanel stampSlot;
    private TopBar topBar;
    private StampPanel stampPanel;
    private ConsolePanel console;
    private AnimationPanel timeline;
    private JPanel southWrap;
    private JPanel animationSlot;

    // Callbacks
    private Consumer<Color> canvasPickCallback;
    private IntConsumer brushSizeCallback;
    private Supplier<ToolMode> toolModeCallback;
    private Supplier<Color[][]> stampCallback;
    private Supplier<Color[][][]> onionCallback;
    private IntSupplier activeLayerCallback;
    private IntPredicate layerVisibleCallback;
    private Supplier<Boolean> panBlockCallback;
    private Runnable undoCallback;
    private Runnable redoCallback;
    private IntConsumer frameStepCallback;
    private Runnable toggleOnionCallback;

    // Controllers
    private PixelCanvas canvasController;
    private ControlBar controlBarController;
    private TopBar topBarController;
    private ConsolePanel consoleController;
    private AnimationPanel animationController;

    public SwingPixelArtView() {
        initialize();
    }

    @Override
    public void initialize() {
        frame = new JFrame("Pixel Art");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(PixelConstants.BG);

        // Initialize components
        canvasHolder = new CanvasViewport(null); // Will be set later
        canvasHolder.setBackground(PixelConstants.BG);

        controlBarSlot = new JPanel(new BorderLayout());
        controlBarSlot.setOpaque(false);
        controlBar = null; // Will be set later
        topBarSlot = new JPanel(new BorderLayout());
        topBarSlot.setOpaque(false);
        stampSlot = new JPanel(new BorderLayout());
        stampSlot.setOpaque(false);
        animationSlot = new JPanel(new BorderLayout());
        animationSlot.setOpaque(false);
        console = new ConsolePanel(cmd -> {
        }); // placeholder; will be replaced
        topBar = null; // Will be set later
        timeline = null; // Will be set later

        southWrap = new JPanel(new BorderLayout());
        southWrap.setBackground(PixelConstants.BG);
        southWrap.add(animationSlot, BorderLayout.NORTH);
        southWrap.add(console, BorderLayout.SOUTH);

        // Layout setup
        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(PixelConstants.BG);

        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));
        topRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        FocusWrap topWrapLeft = new FocusWrap(topBarSlot);
        FocusWrap topWrapRight = new FocusWrap(stampSlot);
        topRow.add(topWrapLeft);
        topRow.add(Box.createRigidArea(new Dimension(6, 1)));
        topRow.add(topWrapRight);
        JPanel topWrap = new JPanel();
        topWrap.setOpaque(false);
        topWrap.setLayout(new BoxLayout(topWrap, BoxLayout.Y_AXIS));
        topWrap.add(topRow);
        topWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        east.add(topWrap, BorderLayout.NORTH);
        FocusWrap controlWrap = new FocusWrap(controlBarSlot);
        east.add(controlWrap, BorderLayout.CENTER);

        frame.add(canvasHolder, BorderLayout.CENTER);
        frame.add(east, BorderLayout.EAST);
        frame.add(southWrap, BorderLayout.SOUTH);

        setupKeyBindings();
    }

    private void setupKeyBindings() {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Z"), "undo");
        root.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoCallback != null)
                    undoCallback.run();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control X"), "redo");
        root.getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (redoCallback != null)
                    redoCallback.run();
            }
        });
        installConsoleToggle(root);
        installPanKeys(root);
        installFrameStepper(root);
    }

    private void installConsoleToggle(JComponent root) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "toggleConsole");
        root.getActionMap().put("toggleConsole", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner()) {
                    if (canvasHolder != null) {
                        canvasHolder.requestFocusInWindow();
                    } else {
                        frame.requestFocusInWindow();
                    }
                } else {
                    if (console != null)
                        console.requestFocusInWindow();
                }
            }
        });
    }

    private void installPanKeys(JComponent root) {
        int step = 20;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "panLeft");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "panRight");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"), "panUp");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"), "panDown");
        root.getActionMap().put("panLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                panViewport(-step, 0);
            }
        });
        root.getActionMap().put("panRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                panViewport(step, 0);
            }
        });
        root.getActionMap().put("panUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                panViewport(0, -step);
            }
        });
        root.getActionMap().put("panDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                panViewport(0, step);
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("OPEN_BRACKET"), "brushDec");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("CLOSE_BRACKET"), "brushInc");
        root.getActionMap().put("brushDec", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                if (brushSizeCallback != null)
                    brushSizeCallback.accept(Math.max(1, getBrushSize() - 1));
            }
        });
        root.getActionMap().put("brushInc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                if (brushSizeCallback != null)
                    brushSizeCallback.accept(getBrushSize() + 1);
            }
        });
    }

    private void installFrameStepper(JComponent root) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, 0),
                "prevFrame");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0),
                "nextFrame");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0),
                "toggleOnion");
        root.getActionMap().put("prevFrame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                if (frameStepCallback != null)
                    frameStepCallback.accept(-1);
            }
        });
        root.getActionMap().put("nextFrame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                if (frameStepCallback != null)
                    frameStepCallback.accept(1);
            }
        });
        root.getActionMap().put("toggleOnion", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocusOwner())
                    return;
                if (toggleOnionCallback != null)
                    toggleOnionCallback.run();
            }
        });
    }

    @Override
    public void start() {
        frame.pack();
        enterFullScreen();
        Cursor cursor = createCursor();
        frame.setCursor(cursor);
    }

    private void enterFullScreen() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            frame.dispose();
            frame.setUndecorated(true);
            gd.setFullScreenWindow(frame);
        } else {
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(screen);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
    }

    private Cursor createCursor() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, size, size);
        Rectangle bounds = new Rectangle(0, 0, size, size);
        PixelFont.draw(g2, "+", bounds, 3, PixelConstants.TEXT);
        g2.dispose();
        Point hotspot = new Point(size / 2, size / 2);
        return Toolkit.getDefaultToolkit().createCustomCursor(img, hotspot, "pixel-plus");
    }

    @Override
    public void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    @Override
    public void setCanvasCellSize(int size) {
        if (canvasController != null) {
            canvasController.setCellSize(size);
            canvasHolder.refreshLayout();
        }
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    @Override
    public int getCanvasCellSize() {
        return canvasController != null ? canvasController.getCellSize() : 16;
    }

    @Override
    public void setViewportBackground(Color color) {
        if (canvasHolder != null) {
            canvasHolder.setBackground(color);
            canvasHolder.repaint();
        }
    }

    @Override
    public void panViewport(int dx, int dy) {
        if (canvasHolder != null) {
            canvasHolder.pan(dx, dy);
        }
    }

    @Override
    public void recenterViewport() {
        if (canvasHolder != null) {
            canvasHolder.recenter();
        }
    }

    @Override
    public void updateBrushTargets(Color color) {
        if (canvasController != null) {
            canvasController.setCurrentColor(color);
        }
        if (topBar != null) {
            topBar.repaint();
        }
    }

    @Override
    public void repaintCanvas() {
        if (canvasController != null) {
            canvasController.repaint();
        }
    }

    @Override
    public void repaintControls() {
        if (controlBar != null) {
            controlBar.repaint();
        }
        if (topBar != null) {
            topBar.repaint();
        }
    }

    @Override
    public void setConsoleStatus(String status) {
        if (console != null) {
            console.setStatus(status);
        }
    }

    @Override
    public void showAnimationPanel(boolean visible) {
        if (animationSlot != null) {
            animationSlot.setVisible(visible);
            animationSlot.revalidate();
        }
        if (timeline != null) {
            timeline.setVisible(visible);
        }
    }

    @Override
    public void setCanvasPickCallback(Consumer<Color> callback) {
        this.canvasPickCallback = callback;
    }

    @Override
    public void setBrushSizeCallback(IntConsumer callback) {
        this.brushSizeCallback = callback;
    }

    @Override
    public void setToolModeCallback(Supplier<ToolMode> callback) {
        this.toolModeCallback = callback;
    }

    @Override
    public void setStampCallback(Supplier<Color[][]> callback) {
        this.stampCallback = callback;
    }

    @Override
    public void setOnionCallback(Supplier<Color[][][]> callback) {
        this.onionCallback = callback;
    }

    @Override
    public void setActiveLayerCallback(IntSupplier callback) {
        this.activeLayerCallback = callback;
    }

    @Override
    public void setLayerVisibleCallback(IntPredicate callback) {
        this.layerVisibleCallback = callback;
    }

    @Override
    public void setPanBlockCallback(Supplier<Boolean> callback) {
        this.panBlockCallback = callback;
    }

    @Override
    public void setUndoCallback(Runnable callback) {
        this.undoCallback = callback;
    }

    @Override
    public void setRedoCallback(Runnable callback) {
        this.redoCallback = callback;
    }

    @Override
    public void setFrameStepCallback(IntConsumer callback) {
        this.frameStepCallback = callback;
    }

    @Override
    public void setToggleOnionCallback(Runnable callback) {
        this.toggleOnionCallback = callback;
    }

    @Override
    public void setCanvasController(Object canvasController) {
        this.canvasController = (PixelCanvas) canvasController;
        if (canvasHolder != null) {
            canvasHolder.setCanvas(this.canvasController);
        }
    }

    @Override
    public void setControlBarController(Object controlBarController) {
        this.controlBarController = (ControlBar) controlBarController;
        this.controlBar = this.controlBarController;
        if (controlBarSlot != null) {
            controlBarSlot.removeAll();
            controlBarSlot.add(controlBar, BorderLayout.CENTER);
            controlBarSlot.revalidate();
            controlBarSlot.repaint();
        }
    }

    @Override
    public void setTopBarController(Object topBarController) {
        this.topBarController = (TopBar) topBarController;
        this.topBar = this.topBarController;
        if (topBarSlot != null) {
            topBarSlot.removeAll();
            topBarSlot.add(topBar, BorderLayout.CENTER);
            topBarSlot.revalidate();
            topBarSlot.repaint();
        }
    }

    @Override
    public void setStampController(Object stampController) {
        this.stampPanel = (StampPanel) stampController;
        if (stampSlot != null) {
            stampSlot.removeAll();
            stampSlot.add(stampPanel, BorderLayout.CENTER);
            stampSlot.revalidate();
            stampSlot.repaint();
        }
    }

    @Override
    public void setConsoleController(Object consoleController) {
        this.consoleController = (ConsolePanel) consoleController;
        this.console = this.consoleController;
        if (southWrap != null) {
            java.awt.Component existing = ((BorderLayout) southWrap.getLayout())
                    .getLayoutComponent(BorderLayout.SOUTH);
            if (existing != null) {
                southWrap.remove(existing);
            }
            southWrap.add(console, BorderLayout.SOUTH);
            southWrap.revalidate();
            southWrap.repaint();
        }
    }

    @Override
    public void setAnimationController(Object animationController) {
        this.animationController = (AnimationPanel) animationController;
        this.timeline = this.animationController;
        if (animationSlot != null) {
            animationSlot.removeAll();
            animationSlot.add(timeline, BorderLayout.CENTER);
            animationSlot.revalidate();
            animationSlot.repaint();
        }
        if (timeline != null) {
            timeline.setVisible(true);
        }
    }

    @Override
    public BufferedImage getCanvasImage() {
        return canvasController != null ? canvasController.toImage() : null;
    }

    @Override
    public BufferedImage getCompositeImage(Color[][][] layerData) {
        if (canvasController == null)
            return null;
        int rows = canvasController.getRows();
        int cols = canvasController.getColumns();
        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color color = null;
                for (int l = layerData.length - 1; l >= 0; l--) {
                    if (layerData[l] == null)
                        continue;
                    Color cc = layerData[l][r][c];
                    if (cc != null) {
                        color = cc;
                        break;
                    }
                }
                if (color == null) {
                    img.setRGB(c, r, 0x00000000);
                } else {
                    img.setRGB(c, r, color.getRGB());
                }
            }
        }
        return img;
    }

    @Override
    public void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfoDialog(String message) {
        JOptionPane.showMessageDialog(frame, message, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean showConfirmDialog(String message) {
        return JOptionPane.showConfirmDialog(frame, message, "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private int getBrushSize() {
        return canvasController != null ? canvasController.getBrushSize() : 1;
    }
}
