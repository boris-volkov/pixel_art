import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PixelArtApp {
    enum ToolMode {BRUSH, STAMP}

    static final Color BG = new Color(96, 96, 96);
    static final Color TEXT = new Color(226, 231, 240);
    static final Color MUTED_TEXT = new Color(160, 170, 186);
    static final Color ACCENT = new Color(126, 170, 220);
    static final Color CANVAS_BG = new Color(86, 86, 86);
    static final Color BUTTON_BG = new Color(60, 64, 70);
    static final Color BUTTON_BORDER = new Color(110, 120, 130);
    static final Color BUTTON_HOVER = new Color(72, 76, 86);
    static final Color BUTTON_ACTIVE = new Color(88, 92, 104);
    static final Color CYAN_BTN = new Color(56, 180, 220);
    static final Color MAGENTA_BTN = new Color(200, 90, 200);
    static final Color YELLOW_BTN = new Color(210, 200, 90);
    static final int CONTROL_BAR_WIDTH = 260;
    static final int COLOR_STEP = 1;
    static final int BRIGHT_STEP = 1;
    static final int BRUSH_BRIGHT_STEP = 1;
    static final int TARGET_CANVAS_SIZE = 640;
    static final int MIN_CELL_SIZE = 2;
    static final int MAX_CELL_SIZE = 20;

    private PixelCanvas canvas;
    private PixelCanvas stampCanvas;
    private JPanel canvasHolder;
    private ControlBar controlBar;
    private TopBar topBar;
    private ConsolePanel console;
    private int red = 32;
    private int green = 32;
    private int blue = 32;
    private int brushSize = 1;
    private int gridSize = 128;
    private int canvasCellSize = computeCellSizeForGrid(gridSize);
    private ToolMode toolMode = ToolMode.BRUSH;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            tryInstallNimbus();
            new PixelArtApp().start();
        });
    }

    private void start() {
        JFrame frame = new JFrame("Pixel Art");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);

        canvas = new PixelCanvas(gridSize, gridSize, canvasCellSize, this::setBrushColor, this::onBrushSizeChanged, this::getToolMode, this::getStampPixels, null);
        canvas.setCurrentColor(currentBrushColor());
        canvas.setBrushSize(brushSize);

        stampCanvas = new PixelCanvas(16, 16, 10, this::setBrushColor, size -> {
        }, () -> ToolMode.BRUSH, null, () -> false);
        stampCanvas.setCurrentColor(currentBrushColor());

        canvasHolder = new JPanel(new GridBagLayout());
        canvasHolder.setBackground(BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        canvasHolder.add(canvas, gbc);

        controlBar = new ControlBar(this);
        console = new ConsolePanel(this::handleCommand);
        topBar = new TopBar(this);

        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);

        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));
        topRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel stampHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        stampHolder.setOpaque(false);
        stampCanvas.setBorder(BorderFactory.createLineBorder(BUTTON_BORDER));
        stampHolder.add(stampCanvas);
        topRow.add(topBar);
        topRow.add(Box.createRigidArea(new Dimension(6, 1)));
        topRow.add(stampHolder);
        JPanel topWrap = new JPanel();
        topWrap.setOpaque(false);
        topWrap.setLayout(new BoxLayout(topWrap, BoxLayout.Y_AXIS));
        topWrap.add(topRow);
        topWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        east.add(topWrap, BorderLayout.NORTH);
        east.add(controlBar, BorderLayout.CENTER);

        frame.add(canvasHolder, BorderLayout.CENTER);
        frame.add(east, BorderLayout.EAST);
        frame.add(console, BorderLayout.SOUTH);

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Z"), "undo");
        frame.getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.undo();
                controlBar.syncSliders();
            }
        });

        frame.pack();
        enterFullScreen(frame);
    }

    private static void tryInstallNimbus() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
                    // keep default
                }
                break;
            }
        }
    }

    private void enterFullScreen(JFrame frame) {
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

    int computeCellSizeForGrid(int grid) {
        int computed = TARGET_CANVAS_SIZE / grid;
        return Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, computed));
    }

    Color currentBrushColor() {
        return new Color(red, green, blue);
    }

    void updateBrushTargets() {
        updateBrushTargets(currentBrushColor());
    }

    void updateBrushTargets(Color color) {
        if (canvas != null) {
            canvas.setCurrentColor(color);
        }
        if (stampCanvas != null) {
            stampCanvas.setCurrentColor(color);
        }
        if (topBar != null) {
            topBar.repaint();
        }
    }

    void setBrushColor(Color color) {
        if (color == null) return;
        red = clamp(color.getRed());
        green = clamp(color.getGreen());
        blue = clamp(color.getBlue());
        updateBrushTargets(color);
        if (controlBar != null) controlBar.syncSliders();
    }

    void onBrushSizeChanged(int size) {
        brushSize = size;
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    Color[][] getStampPixels() {
        return stampCanvas != null ? stampCanvas.getPixelsCopy() : null;
    }

    void rebuildCanvas(int newGridSize) {
        gridSize = newGridSize;
        canvasCellSize = computeCellSizeForGrid(gridSize);
        PixelCanvas newCanvas = new PixelCanvas(gridSize, gridSize, canvasCellSize, this::setBrushColor, this::onBrushSizeChanged, this::getToolMode, this::getStampPixels, null);
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;

        canvasHolder.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        canvasHolder.add(newCanvas, gbc);
        canvasHolder.revalidate();
        canvasHolder.repaint();
    }

    void adjustBrushBrightnessGlobal(int delta) {
        red = clamp(red + delta);
        green = clamp(green + delta);
        blue = clamp(blue + delta);
        updateBrushTargets();
        if (controlBar != null) controlBar.syncSliders();
    }

    void setCanvasCellSize(int size) {
        int cap = computeMaxCellSizeForScreen();
        canvasCellSize = Math.max(2, Math.min(cap, size));
        if (canvas != null) {
            canvas.setCellSize(canvasCellSize);
            canvasHolder.revalidate();
            canvasHolder.repaint();
        }
        if (controlBar != null) controlBar.syncSliders();
    }

    int getCanvasCellSize() {
        return canvasCellSize;
    }

    int computeMaxCellSizeForScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int usableWidth = (int) screen.getWidth() - CONTROL_BAR_WIDTH - 200;
        int usableHeight = (int) screen.getHeight() - 200;
        int maxByWidth = Math.max(2, usableWidth / gridSize);
        int maxByHeight = Math.max(2, usableHeight / gridSize);
        return Math.min(maxByWidth, maxByHeight);
    }

    PixelCanvas getStampCanvas() {
        return stampCanvas;
    }

    void handleCommand(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        String[] parts = input.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "save":
                if (parts.length < 2) {
                    console.setStatus("Usage: save <file.png>");
                    return;
                }
                try {
                    saveImage(parts[1]);
                    console.setStatus("Saved to " + parts[1]);
                } catch (IOException ex) {
                    console.setStatus("Save failed: " + ex.getMessage());
                }
                break;
            case "new":
                if (parts.length < 2) {
                    console.setStatus("Usage: new <size>");
                    return;
                }
                try {
                    int size = Integer.parseInt(parts[1]);
                    if (size <= 0) throw new NumberFormatException();
                    rebuildCanvas(size);
                    console.setStatus("Created new " + size + "x" + size + " canvas");
                } catch (NumberFormatException ex) {
                    console.setStatus("Size must be a positive number");
                }
                break;
            case "flip":
                if (parts.length < 2) {
                    console.setStatus("Usage: flip h|v");
                    return;
                }
                if ("h".equalsIgnoreCase(parts[1])) {
                    canvas.flipHorizontal();
                    console.setStatus("Flipped horizontally");
                } else if ("v".equalsIgnoreCase(parts[1])) {
                    canvas.flipVertical();
                    console.setStatus("Flipped vertically");
                } else {
                    console.setStatus("Usage: flip h|v");
                }
                break;
            case "help":
                console.setStatus("Commands: save <file.png> | new <size> | flip h | flip v | exit");
                break;
            case "exit":
                System.exit(0);
            default:
                console.setStatus("Unknown. Try: save <file.png> | new <size> | flip h | flip v | exit");
        }
    }

    private void saveImage(String path) throws IOException {
        BufferedImage img = canvas.toImage();
        File file = new File(path);
        String format = "png";
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            format = path.substring(dot + 1);
        }
        ImageIO.write(img, format, file);
    }

    static Color adjustChannel(Color color, int redDelta, int greenDelta, int blueDelta) {
        int r = clamp(color.getRed() + redDelta);
        int g = clamp(color.getGreen() + greenDelta);
        int b = clamp(color.getBlue() + blueDelta);
        return new Color(r, g, b);
    }

    static Color adjustBrightness(Color color, int delta) {
        int r = clamp(color.getRed() + delta);
        int g = clamp(color.getGreen() + delta);
        int b = clamp(color.getBlue() + delta);
        return new Color(r, g, b);
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    int getRed() { return red; }
    int getGreen() { return green; }
    int getBlue() { return blue; }
    void setRed(int v) { red = clamp(v); updateBrushTargets(); }
    void setGreen(int v) { green = clamp(v); updateBrushTargets(); }
    void setBlue(int v) { blue = clamp(v); updateBrushTargets(); }
    int getBrushSize() { return brushSize; }
    void setBrushSize(int size) { onBrushSizeChanged(size); if (canvas != null) canvas.setBrushSize(size); }
    ToolMode getToolMode() { return toolMode; }
    void toggleToolMode() { toolMode = (toolMode == ToolMode.BRUSH) ? ToolMode.STAMP : ToolMode.BRUSH; }
    PixelCanvas getCanvas() { return canvas; }
}
