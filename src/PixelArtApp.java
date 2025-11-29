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
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public class PixelArtApp {
    enum ToolMode {BRUSH, STAMP, FILL, BLUR, MOVE, ERASER}

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
    private CanvasViewport canvasHolder;
    private ControlBar controlBar;
    private TopBar topBar;
    private ConsolePanel console;
    private AnimationPanel timeline;
    private JPanel southWrap;
    private List<FrameData>[] layerFrames;
    private int[] currentFrameIndex;
    private Timer playTimer;
    private boolean playing = false;
    private int playCursor = 0;
    private boolean onionEnabled = false;
    private boolean[] animatedLayers;
    private String[] layerNames = new String[]{"L1", "L2", "L3"};
    private int frameRate = 6; // fps
    private Color viewportBg = BG;
    private int red = 32;
    private int green = 32;
    private int blue = 32;
    private int brushSize = 1;
    private int gridSize = 128;
    private int canvasCellSize = computeMaxCellSizeForScreen();
    private ToolMode toolMode = ToolMode.BRUSH;
    private int activeLayer = 0;
    private boolean stampUseOwnColors = true;
    private final boolean[] layerVisible = new boolean[]{true, true, true};

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PixelArtApp().start();
        });
    }

    private void start() {
        JFrame frame = new JFrame("Pixel Art");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);

        canvas = new PixelCanvas(gridSize, gridSize, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getOnionComposite, this::getActiveLayer, 3, this::isLayerVisible, null, false);
        canvas.setCurrentColor(currentBrushColor());
        canvas.setBrushSize(brushSize);
        canvas.setStampUsesOwnColors(stampUseOwnColors);
        ensureLayerNamesSize(canvas.getLayerCount());
        initLayerFrames(canvas.getLayerCount());

        int stampCellSize = 10;
        stampCanvas = new PixelCanvas(16, 16, stampCellSize, this::setBrushColor, this::setBrushSize, () -> {
            ToolMode mode = getToolMode();
            return mode == ToolMode.ERASER ? ToolMode.ERASER : ToolMode.BRUSH;
        }, null, null, () -> 0, 1, l -> true, null, true);
        stampCanvas.setCurrentColor(currentBrushColor());

        canvasHolder = new CanvasViewport(canvas);
        canvasHolder.setBackground(viewportBg);

        controlBar = new ControlBar(this);
        console = new ConsolePanel(this::handleCommand);
        topBar = new TopBar(this);
        setCanvasCellSize(computeMaxCellSizeForScreen());

        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);

        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));
        topRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        StampPanel stampHolder = new StampPanel(stampCanvas, this);
        FocusWrap topWrapLeft = new FocusWrap(topBar);
        FocusWrap topWrapRight = new FocusWrap(stampHolder);
        topRow.add(topWrapLeft);
        topRow.add(Box.createRigidArea(new Dimension(6, 1)));
        topRow.add(topWrapRight);
        JPanel topWrap = new JPanel();
        topWrap.setOpaque(false);
        topWrap.setLayout(new BoxLayout(topWrap, BoxLayout.Y_AXIS));
        topWrap.add(topRow);
        topWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        east.add(topWrap, BorderLayout.NORTH);
        FocusWrap controlWrap = new FocusWrap(controlBar);
        east.add(controlWrap, BorderLayout.CENTER);

        southWrap = new JPanel(new BorderLayout());
        southWrap.setBackground(BG);
        timeline = new AnimationPanel(this);
        timeline.setVisible(false);
        southWrap.add(timeline, BorderLayout.NORTH);
        southWrap.add(console, BorderLayout.SOUTH);

        frame.add(canvasHolder, BorderLayout.CENTER);
        frame.add(east, BorderLayout.EAST);
        frame.add(southWrap, BorderLayout.SOUTH);

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Z"), "undo");
        frame.getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.undo();
                controlBar.syncSliders();
            }
        });
        installConsoleToggle(frame);
        installPanKeys(frame);

        frame.pack();
        enterFullScreen(frame);

        java.awt.Cursor cursor = createCursor();
        frame.setCursor(cursor);
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

    private java.awt.Cursor createCursor() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, size, size);

        Rectangle bounds = new Rectangle(0, 0, size, size);
        PixelFont.draw(g2, "+", bounds, 3, TEXT);
        g2.dispose();
        Point hotspot = new Point(size / 2, size / 2);
        return Toolkit.getDefaultToolkit().createCustomCursor(img, hotspot, "pixel-plus");
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

    void rebuildCanvas(int newCols, int newRows) {
        resetAnimationState();
        gridSize = Math.max(newCols, newRows);
        canvasCellSize = computeMaxCellSizeForScreen();
        PixelCanvas newCanvas = new PixelCanvas(newCols, newRows, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getOnionComposite, this::getActiveLayer, 3, this::isLayerVisible, null, false);
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;
        ensureLayerNamesSize(newCanvas.getLayerCount());
        initLayerFrames(newCanvas.getLayerCount());
        canvasHolder.setCanvas(newCanvas);
        canvasHolder.recenter();
    }

    void adjustBrushBrightnessGlobal(int delta) {
        red = clamp(red + delta);
        green = clamp(green + delta);
        blue = clamp(blue + delta);
        updateBrushTargets();
        if (controlBar != null) controlBar.syncSliders();
    }

    void setCanvasCellSize(int size) {
        int cap = MAX_CELL_SIZE;
        canvasCellSize = Math.max(2, Math.min(cap, size));
        if (canvas != null) {
            canvas.setCellSize(canvasCellSize);
            canvasHolder.refreshLayout();
        }
        if (controlBar != null) controlBar.syncSliders();
    }

    int getCanvasCellSize() {
        return canvasCellSize;
    }

    private void resampleCanvas(int factor) {
        if (factor <= 1) return;
        ensureFrameCapacity();
        saveCurrentFrames();
        int oldRows = canvas.getRows();
        int oldCols = canvas.getColumns();
        int newRows = oldRows * factor;
        int newCols = oldCols * factor;
        gridSize = Math.max(newRows, newCols);
        canvasCellSize = Math.min(canvasCellSize, MAX_CELL_SIZE);

        // Scale all stored frames per layer
        List<List<FrameData>> scaledFrames = new ArrayList<>();
        for (List<FrameData> lf : layerFrames) {
            List<FrameData> dest = new ArrayList<>();
            for (FrameData fd : lf) {
                dest.add(new FrameData(scaleLayer(fd.layer, factor)));
            }
            if (dest.isEmpty()) dest.add(new FrameData(new Color[newRows][newCols]));
            scaledFrames.add(dest);
        }
        // Rebuild canvas
        PixelCanvas newCanvas = new PixelCanvas(newCols, newRows, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getOnionComposite, this::getActiveLayer, scaledFrames.size(), this::isLayerVisible, null, false);
        this.canvas = newCanvas;
        canvasHolder.setCanvas(newCanvas);
        ensureLayerNamesSize(newCanvas.getLayerCount());

        // Restore frame data
        initLayerFrames(newCanvas.getLayerCount());
        for (int l = 0; l < scaledFrames.size(); l++) {
            layerFrames[l].clear();
            layerFrames[l].addAll(scaledFrames.get(l));
            currentFrameIndex[l] = Math.min(currentFrameIndex[l], layerFrames[l].size() - 1);
        }
        applyAllCurrentFrames();
        canvas.setCurrentColor(currentBrushColor());
        canvas.setBrushSize(brushSize);
        canvasHolder.recenter();
        if (controlBar != null) controlBar.syncSliders();
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
            case "save-sequence":
                if (parts.length < 2) {
                    console.setStatus("Usage: save-sequence <base.png>");
                    return;
                }
                try {
                    saveSequence(parts[1]);
                    console.setStatus("Saved sequence for " + parts[1]);
                } catch (IOException ex) {
                    console.setStatus("Save-seq failed: " + ex.getMessage());
                }
                break;
            case "save-project":
                if (parts.length < 2) {
                    console.setStatus("Usage: save-project <file>");
                    return;
                }
                try {
                    saveProject(parts[1]);
                    console.setStatus("Project saved to " + parts[1]);
                } catch (IOException ex) {
                    console.setStatus("Save-project failed: " + ex.getMessage());
                }
                break;
            case "save-gif":
                if (parts.length < 2) {
                    console.setStatus("Usage: save-gif <file.gif>");
                    return;
                }
                try {
                    saveGif(parts[1]);
                    console.setStatus("GIF saved to " + parts[1]);
                } catch (IOException ex) {
                    console.setStatus("Save-gif failed: " + ex.getMessage());
                }
                break;
            case "load-project":
                if (parts.length < 2) {
                    console.setStatus("Usage: load-project <file>");
                    return;
                }
                try {
                    loadProject(parts[1]);
                    console.setStatus("Project loaded from " + parts[1]);
                } catch (IOException | ClassNotFoundException ex) {
                    console.setStatus("Load-project failed: " + ex.getMessage());
                }
                break;
            case "load":
                if (parts.length < 2) {
                    console.setStatus("Usage: load <file.png>");
                    return;
                }
                try {
                    loadImage(parts[1]);
                    console.setStatus("Loaded " + parts[1]);
                } catch (IOException ex) {
                    console.setStatus("Load failed: " + ex.getMessage());
                }
                break;
            case "resolution":
                if (canvas != null) {
                    console.setStatus("Resolution: " + canvas.getColumns() + "x" + canvas.getRows());
                } else {
                    console.setStatus("Resolution: " + gridSize + "x" + gridSize);
                }
                break;
            case "new":
                if (parts.length < 2) {
                    console.setStatus("Usage: new <size> or new <width> <height>");
                    return;
                }
                try {
                    if (parts.length == 2) {
                        int size = Integer.parseInt(parts[1]);
                        if (size <= 0) throw new NumberFormatException();
                        rebuildCanvas(size, size);
                        console.setStatus("Created new " + size + "x" + size + " canvas");
                    } else {
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        if (w <= 0 || h <= 0) throw new NumberFormatException();
                        rebuildCanvas(w, h);
                        console.setStatus("Created new " + w + "x" + h + " canvas");
                    }
                } catch (NumberFormatException ex) {
                    console.setStatus("Size must be positive numbers");
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
            case "dither":
                if (parts.length < 2) {
                    console.setStatus("Usage: dither floyd | dither ordered");
                    break;
                }
                if ("floyd".equalsIgnoreCase(parts[1])) {
                    canvas.ditherFloydSteinberg();
                    console.setStatus("Applied Floyd-Steinberg dither");
                } else if ("ordered".equalsIgnoreCase(parts[1])) {
                    canvas.ditherOrdered();
                    console.setStatus("Applied ordered dither (4x4)");
                } else {
                    console.setStatus("Usage: dither floyd | dither ordered");
                }
                break;
            case "help":
                console.setStatus("Commands: save <file.png> | save-sequence <base.png> | save-gif <file.gif> | save-project <file> | load-project <file> | load <file.png> | resolution | new <size> | flip h|v | blur gaussian <r> | blur motion <angle> <amt> | dither floyd|ordered | resample <factor> | calc <expr> | animate [layer] | framerate <fps> | duplicate | rename L# <name> | exit");
                break;
            case "blur":
                if (parts.length < 3) {
                    console.setStatus("Usage: blur gaussian <radius> | blur motion <angle> <amount>");
                    break;
                }
                if ("gaussian".equalsIgnoreCase(parts[1])) {
                    try {
                        int radius = Integer.parseInt(parts[2]);
                        if (radius <= 0) {
                            console.setStatus("Radius must be > 0");
                            break;
                        }
                        canvas.blurGaussian(radius);
                        console.setStatus("Applied gaussian blur r=" + radius);
                    } catch (NumberFormatException ex) {
                        console.setStatus("Radius must be a number");
                    }
                } else if ("motion".equalsIgnoreCase(parts[1])) {
                    if (parts.length < 4) {
                        console.setStatus("Usage: blur motion <angleDeg> <amount>");
                        break;
                    }
                    try {
                        double angle = Double.parseDouble(parts[2]);
                        int amt = Integer.parseInt(parts[3]);
                        if (amt <= 0) {
                            console.setStatus("Amount must be > 0");
                            break;
                        }
                        canvas.blurMotion(angle, amt);
                        console.setStatus("Applied motion blur angle=" + angle + " amt=" + amt);
                    } catch (NumberFormatException ex) {
                        console.setStatus("Need numeric angle and amount");
                    }
                } else {
                    console.setStatus("Usage: blur gaussian <radius> | blur motion <angle> <amount>");
                }
                break;
            case "animate":
                ensureFrameCapacity();
                saveCurrentFrames();
                if (parts.length >= 2) {
                    String target = parts[1].toLowerCase();
                    Integer layerIdx = parseLayerToken(target);
                    if (layerIdx != null) {
                        setActiveLayer(layerIdx);
                    } else {
                        // match by layer name
                        for (int idx = 0; idx < layerNames.length; idx++) {
                            if (layerNames[idx].equalsIgnoreCase(parts[1])) {
                                setActiveLayer(idx);
                                break;
                            }
                        }
                    }
                }
                if (!timeline.isVisible()) {
                    timeline.setVisible(true);
                    southWrap.revalidate();
                }
                console.setStatus("Animation panel open for " + getLayerName(activeLayer));
                break;
            case "framerate":
                if (parts.length < 2) {
                    console.setStatus("Usage: framerate <fps>");
                    break;
                }
                try {
                    int fps = Integer.parseInt(parts[1]);
                    if (fps <= 0) {
                        console.setStatus("FPS must be > 0");
                        break;
                    }
                    setFrameRate(fps);
                    console.setStatus("Framerate set to " + fps + " fps");
                } catch (NumberFormatException ex) {
                    console.setStatus("FPS must be a number");
                }
                break;
            case "rename":
                if (parts.length < 3 || !parts[1].toLowerCase().startsWith("l")) {
                    console.setStatus("Usage: rename L1 <name>");
                    break;
                }
                try {
                    int idx = Integer.parseInt(parts[1].substring(1)) - 1;
                    if (idx < 0 || idx >= layerNames.length) {
                        console.setStatus("Layer out of range");
                        break;
                    }
                    String newName = input.substring(input.indexOf(parts[2]));
                    setLayerName(idx, newName.trim());
                    console.setStatus("Layer " + (idx + 1) + " renamed to " + newName);
                    if (controlBar != null) controlBar.repaint();
                } catch (NumberFormatException ex) {
                    console.setStatus("Usage: rename L1 <name>");
                }
                break;
            case "duplicate":
                duplicateCurrentFrame();
                console.setStatus("Duplicated frame " + (getCurrentFrameIndexForActiveLayer() + 1) + " on " + getLayerName(activeLayer));
                break;
            case "background":
                if (parts.length < 4) {
                    console.setStatus("Usage: background R G B (0-255)");
                    break;
                }
                try {
                    int r = Integer.parseInt(parts[1]);
                    int g = Integer.parseInt(parts[2]);
                    int b = Integer.parseInt(parts[3]);
                    viewportBg = new Color(clamp(r), clamp(g), clamp(b));
                    if (canvasHolder != null) {
                        canvasHolder.setBackground(viewportBg);
                        canvasHolder.repaint();
                    }
                    console.setStatus("Background set");
                } catch (NumberFormatException ex) {
                    console.setStatus("Usage: background R G B (0-255)");
                }
                break;
            case "onion":
                toggleOnion();
                console.setStatus("Onion " + (onionEnabled ? "ON" : "OFF"));
                if (canvas != null) canvas.repaint();
                break;
            case "resample":
                if (parts.length < 2) {
                    console.setStatus("Usage: resample <factor>");
                    break;
                }
                try {
                    int factor = Integer.parseInt(parts[1]);
                    if (factor <= 1) {
                        console.setStatus("Factor must be > 1");
                        break;
                    }
                    resampleCanvas(factor);
                    console.setStatus("Resampled x" + factor);
                } catch (NumberFormatException ex) {
                    console.setStatus("Factor must be a number");
                }
                break;
            case "calc":
                if (parts.length < 2) {
                    console.setStatus("Usage: calc (<op> <a> <b>) e.g. calc (+ 1 2) or calc (* (+ 1 2) 3)");
                    break;
                }
                try {
                    String expr = input.substring(input.indexOf(' ') + 1).trim();
                    double val = evalLisp(expr);
                    if (Math.abs(val - Math.round(val)) < 1e-9) {
                        console.setStatus("= " + (long) Math.round(val));
                    } else {
                        console.setStatus("= " + val);
                    }
                } catch (IllegalArgumentException ex) {
                    console.setStatus("Calc error: " + ex.getMessage());
                }
                break;
            case "exit":
                System.exit(0);
            default:
                console.setStatus("Unknown. Try: save <file.png> | load <file.png> | resolution | new <size> | flip h | flip v | blur gaussian <r> | dither floyd | dither ordered | resample <factor> | calc | exit");
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

    private BufferedImage toImage(Color[][][] layerData) {
        int rows = canvas.getRows();
        int cols = canvas.getColumns();
        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color color = null;
                for (int l = layerData.length - 1; l >= 0; l--) {
                    if (layerData[l] == null) continue;
                    Color cc = layerData[l][r][c];
                    if (cc != null) { color = cc; break; }
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

    private void writeGif(List<BufferedImage> framesOut, int delayCs, String path) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").hasNext() ? ImageIO.getImageWritersBySuffix("gif").next() : null;
        if (writer == null) throw new IOException("No GIF writer available");
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File(path))) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            ImageWriteParam param = writer.getDefaultWriteParam();
            for (int i = 0; i < framesOut.size(); i++) {
                BufferedImage bi = framesOut.get(i);
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, param);
                String metaFormat = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);
                IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                gce.setAttribute("transparentColorIndex", "0");

                if (i == 0) {
                    IIOMetadataNode aes = getNode(root, "ApplicationExtensions");
                    IIOMetadataNode ae = new IIOMetadataNode("ApplicationExtension");
                    ae.setAttribute("applicationID", "NETSCAPE");
                    ae.setAttribute("authenticationCode", "2.0");
                    byte[] loop = new byte[]{1, 0, 0};
                    ae.setUserObject(loop);
                    aes.appendChild(ae);
                }

                metadata.setFromTree(metaFormat, root);
                writer.writeToSequence(new javax.imageio.IIOImage(bi, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }

    private void saveSequence(String basePath) throws IOException {
        ensureFrameCapacity();
        int layerCount = canvas.getLayerCount();
        int maxFrames = 0;
        for (List<FrameData> lf : layerFrames) {
            if (lf != null) {
                maxFrames = Math.max(maxFrames, lf.size());
            }
        }
        if (maxFrames <= 0) {
            console.setStatus("No frames to save");
            return;
        }
        String format = "png";
        int dot = basePath.lastIndexOf('.');
        String prefix = basePath;
        if (dot > 0 && dot < basePath.length() - 1) {
            format = basePath.substring(dot + 1);
            prefix = basePath.substring(0, dot);
        }
        File baseFile = new File(prefix);
        File parentDir = baseFile.getParentFile();
        if (parentDir == null) parentDir = new File(".");
        String dirName = baseFile.getName();
        File outDir = new File(parentDir, dirName);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create directory " + outDir.getAbsolutePath());
        }
        saveCurrentFrames();
        int digits = Math.max(3, String.valueOf(maxFrames).length());
        for (int i = 0; i < maxFrames; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<FrameData> lf = layerFrames[l];
                if (lf.isEmpty()) continue;
                FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            BufferedImage img = toImage(snapshot);
            String idx = String.format("%0" + digits + "d", i + 1);
            String outName = new File(outDir, prefixFileName(dirName, idx, format)).getPath();
            ImageIO.write(img, format, new File(outName));
        }
        applyAllCurrentFrames();
    }

    private void saveGif(String path) throws IOException {
        ensureFrameCapacity();
        int layerCount = canvas.getLayerCount();
        int lcm = 1;
        for (List<FrameData> lf : layerFrames) {
            int len = lf.size();
            if (len > 0) {
                lcm = lcm(lcm, len);
            }
        }
        if (lcm <= 0) {
            console.setStatus("No frames to save");
            return;
        }
        saveCurrentFrames();
        int delayCs = Math.max(1, (int) Math.round(100.0 / Math.max(1, frameRate))); // hundredths of a second
        List<BufferedImage> framesOut = new ArrayList<>();
        for (int i = 0; i < lcm; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<FrameData> lf = layerFrames[l];
                if (lf.isEmpty()) continue;
                FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            framesOut.add(toImage(snapshot));
        }
        writeGif(framesOut, delayCs, path);
        applyAllCurrentFrames();
    }

    private void saveProject(String path) throws IOException {
        ensureFrameCapacity();
        ProjectData data = new ProjectData();
        data.cols = canvas.getColumns();
        data.rows = canvas.getRows();
        data.cellSize = canvasCellSize;
        data.layerNames = layerNames.clone();
        data.layerVisible = layerVisible.clone();
        data.animatedLayers = animatedLayers.clone();
        data.currentFrameIndex = currentFrameIndex.clone();
        data.activeLayer = activeLayer;
        data.brushSize = brushSize;
        data.red = red;
        data.green = green;
        data.blue = blue;
        data.frameRate = frameRate;
        data.viewportBg = viewportBg;
        data.layerFrames = new ArrayList<>();
        for (List<FrameData> lf : layerFrames) {
            List<Color[][]> saved = new ArrayList<>();
            for (FrameData fd : lf) {
                saved.add(cloneLayer(fd.layer));
            }
            data.layerFrames.add(saved);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(data);
        }
    }

    private void loadProject(String path) throws IOException, ClassNotFoundException {
        if (playTimer != null) playTimer.stop();
        playing = false;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            ProjectData data = (ProjectData) ois.readObject();
            gridSize = Math.max(data.cols, data.rows);
            canvasCellSize = Math.min(MAX_CELL_SIZE, Math.max(2, data.cellSize));
            PixelCanvas newCanvas = new PixelCanvas(data.cols, data.rows, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getOnionComposite, this::getActiveLayer, data.layerFrames.size(), this::isLayerVisible, null, false);
            canvas = newCanvas;
            canvasHolder.setCanvas(newCanvas);
            viewportBg = data.viewportBg != null ? data.viewportBg : BG;
            canvasHolder.setBackground(viewportBg);
            ensureLayerNamesSize(data.layerNames.length);
            System.arraycopy(data.layerNames, 0, layerNames, 0, Math.min(layerNames.length, data.layerNames.length));
            for (int i = 0; i < Math.min(layerVisible.length, data.layerVisible.length); i++) {
                layerVisible[i] = data.layerVisible[i];
            }
            animatedLayers = Arrays.copyOf(data.animatedLayers, data.layerFrames.size());
            currentFrameIndex = Arrays.copyOf(data.currentFrameIndex, data.layerFrames.size());
            brushSize = data.brushSize;
            red = data.red;
            green = data.green;
            blue = data.blue;
            frameRate = data.frameRate;
            activeLayer = Math.max(0, Math.min(data.activeLayer, data.layerFrames.size() - 1));
            initLayerFrames(data.layerFrames.size());
            for (int l = 0; l < data.layerFrames.size(); l++) {
                List<Color[][]> saved = data.layerFrames.get(l);
                List<FrameData> dest = layerFrames[l];
                dest.clear();
                for (Color[][] layer : saved) {
                    dest.add(new FrameData(cloneLayer(layer)));
                }
                if (dest.isEmpty()) {
                    dest.add(createEmptyFrameForLayer(l));
                }
            }
            applyAllCurrentFrames();
            updateBrushTargets(currentBrushColor());
            canvas.setBrushSize(brushSize);
            if (controlBar != null) controlBar.syncSliders();
            if (timeline != null) timeline.repaint();
            if (topBar != null) topBar.repaint();
        }
    }

    private String prefixFileName(String base, String idx, String format) {
        return base + idx + "." + format;
    }

    private void loadImage(String path) throws IOException {
        resetAnimationState();
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("Unsupported image");
        int w = img.getWidth();
        int h = img.getHeight();
        if (w != h) {
            throw new IOException("Image must be square");
        }
        gridSize = w;
        canvasCellSize = Math.min(canvasCellSize, MAX_CELL_SIZE);
        PixelCanvas newCanvas = new PixelCanvas(w, h, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getOnionComposite, this::getActiveLayer, 3, this::isLayerVisible, null, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                Color c = new Color(argb, true);
                newCanvas.setPixelDirect(y, x, c);
            }
        }
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;

        ensureLayerNamesSize(newCanvas.getLayerCount());
        initLayerFrames(newCanvas.getLayerCount());
        canvasHolder.setCanvas(newCanvas);
        if (controlBar != null) controlBar.syncSliders();
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

    private double evalLisp(String expr) {
        expr = expr.trim();
        if (expr.startsWith("(")) {
            java.util.ArrayDeque<String> tokens = tokenize(expr);
            double val = parseList(tokens);
            if (!tokens.isEmpty()) throw new IllegalArgumentException("Extra tokens");
            return val;
        } else {
            String[] parts = expr.split("\\s+");
            if (parts.length == 1) {
                return parseNumber(parts[0]);
            }
            String op = parts[0];
            java.util.List<Double> vals = new java.util.ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                vals.add(parseNumber(parts[i]));
            }
            return applyOp(op, vals);
        }
    }

    private java.util.ArrayDeque<String> tokenize(String expr) {
        java.util.ArrayDeque<String> out = new java.util.ArrayDeque<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char ch = expr.charAt(i);
            if (ch == '(' || ch == ')' || Character.isWhitespace(ch)) {
                if (sb.length() > 0) {
                    out.addLast(sb.toString());
                    sb.setLength(0);
                }
                if (ch == '(' || ch == ')') {
                    out.addLast(String.valueOf(ch));
                }
            } else {
                sb.append(ch);
            }
        }
        if (sb.length() > 0) out.addLast(sb.toString());
        return out;
    }

    private double parseList(java.util.ArrayDeque<String> tokens) {
        if (tokens.isEmpty()) throw new IllegalArgumentException("Incomplete expression");
        String t = tokens.removeFirst();
        if (!"(".equals(t)) throw new IllegalArgumentException("Expected '('");
        if (tokens.isEmpty()) throw new IllegalArgumentException("Missing operator");
        String op = tokens.removeFirst();
        java.util.List<Double> vals = new java.util.ArrayList<>();
        while (!tokens.isEmpty() && !" )".contains(tokens.peekFirst()) && !")".equals(tokens.peekFirst())) {
            vals.add(parseAny(tokens));
        }
        while (!tokens.isEmpty() && ")".equals(tokens.peekFirst())) {
            tokens.removeFirst();
            break;
        }
        if (vals.isEmpty()) throw new IllegalArgumentException("Missing operands");
        return applyOp(op, vals);
    }

    private long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return Math.max(1, a);
    }

    private int lcm(int a, int b) {
        long g = gcd(a, b);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, (a / g) * (long) b));
    }

    private double parseAny(java.util.ArrayDeque<String> tokens) {
        if (tokens.isEmpty()) throw new IllegalArgumentException("Incomplete");
        String next = tokens.peekFirst();
        if ("(".equals(next)) {
            return parseList(tokens);
        }
        tokens.removeFirst();
        return parseNumber(next);
    }

    private double parseNumber(String t) {
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bad token: " + t);
        }
    }

    private double applyOp(String op, java.util.List<Double> vals) {
        if (vals.isEmpty()) throw new IllegalArgumentException("Missing operands");
        switch (op) {
            case "+":
                double sum = 0;
                for (double v : vals) sum += v;
                return sum;
            case "*":
                double prod = 1;
                for (double v : vals) prod *= v;
                return prod;
            case "-":
                if (vals.size() == 1) return -vals.get(0);
                double res = vals.get(0);
                for (int i = 1; i < vals.size(); i++) res -= vals.get(i);
                return res;
            case "/":
                if (vals.size() == 1) return 1.0 / vals.get(0);
                double div = vals.get(0);
                for (int i = 1; i < vals.size(); i++) div /= vals.get(i);
                return div;
            default:
                throw new IllegalArgumentException("Unknown op: " + op);
        }
    }

    int getRed() { return red; }
    int getGreen() { return green; }
    int getBlue() { return blue; }
    void setRed(int v) { red = clamp(v); updateBrushTargets(); if (controlBar != null) controlBar.syncSliders(); }
    void setGreen(int v) { green = clamp(v); updateBrushTargets(); if (controlBar != null) controlBar.syncSliders(); }
    void setBlue(int v) { blue = clamp(v); updateBrushTargets(); if (controlBar != null) controlBar.syncSliders(); }
    int getSaturationPercent() {
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return Math.round(hsb[1] * 100f);
    }
    int getBrightnessPercent() {
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        return Math.round(hsb[2] * 100f);
    }
    void setSaturationPercent(int percent) {
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        float s = Math.max(0f, Math.min(1f, percent / 100f));
        Color c = Color.getHSBColor(hsb[0], s, hsb[2]);
        setBrushColor(c);
    }
    void setBrightnessPercent(int percent) {
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        float bVal = Math.max(0f, Math.min(1f, percent / 100f));
        Color c = Color.getHSBColor(hsb[0], hsb[1], bVal);
        setBrushColor(c);
    }
    int getBrushSize() { return brushSize; }
    void setBrushSize(int size) { onBrushSizeChanged(size); if (canvas != null) canvas.setBrushSize(size); }
    ToolMode getToolMode() { return toolMode; }
    void setToolMode(ToolMode mode) { toolMode = mode; }
    PixelCanvas getCanvas() { return canvas; }
    int getActiveLayer() { return activeLayer; }
    void setActiveLayer(int layer) {
        ensureFrameCapacity();
        int max = canvas != null ? canvas.getLayerCount() - 1 : 2;
        activeLayer = Math.max(0, Math.min(max, layer));
        if (timeline != null) timeline.repaint();
    }
    boolean isLayerVisible(int layer) { return layerVisible[Math.max(0, Math.min(layerVisible.length - 1, layer))]; }
    void toggleLayerVisibility(int layer) {
        int idx = Math.max(0, Math.min(layerVisible.length - 1, layer));
        layerVisible[idx] = !layerVisible[idx];
        if (canvas != null) {
            canvas.repaint();
        }
    }
    void swapLayerUp(int idx) {
        ensureFrameCapacity();
        if (idx <= 0 || idx >= canvas.getLayerCount()) return;
        swapArray(layerFrames, idx, idx - 1);
        swapInt(currentFrameIndex, idx, idx - 1);
        swapBoolean(layerVisible, idx, idx - 1);
        swapBoolean(animatedLayers, idx, idx - 1);
        swapString(layerNames, idx, idx - 1);
        canvas.swapLayers(idx, idx - 1);
        if (activeLayer == idx) activeLayer = idx - 1;
        else if (activeLayer == idx - 1) activeLayer = idx;
        if (controlBar != null) controlBar.repaint();
        if (topBar != null) topBar.repaint();
        canvas.repaint();
    }
    boolean isOnionEnabled() { return onionEnabled; }
    void toggleOnion() { onionEnabled = !onionEnabled; }

    // Animation helpers
    List<FrameData> getFramesForActiveLayer() { ensureFrameCapacity(); return layerFrames[activeLayer]; }
    int getCurrentFrameIndexForActiveLayer() { ensureFrameCapacity(); return currentFrameIndex[activeLayer]; }
    boolean isPlaying() { return playing; }
    boolean isLayerAnimated(int layer) {
        ensureFrameCapacity();
        return animatedLayers[Math.max(0, Math.min(animatedLayers.length - 1, layer))];
    }
    void toggleAnimatedLayer(int layer) {
        ensureFrameCapacity();
        int idx = Math.max(0, Math.min(animatedLayers.length - 1, layer));
        animatedLayers[idx] = !animatedLayers[idx];
        if (timeline != null) timeline.repaint();
    }
    String getLayerName(int idx) {
        int i = Math.max(0, Math.min(layerNames.length - 1, idx));
        return layerNames[i];
    }
    void setLayerName(int idx, String name) {
        int i = Math.max(0, Math.min(layerNames.length - 1, idx));
        layerNames[i] = name;
    }

    boolean isStampUsingOwnColors() { return stampUseOwnColors; }
    void setStampUseOwnColors(boolean own) {
        stampUseOwnColors = own;
        if (canvas != null) canvas.setStampUsesOwnColors(own);
    }

    private Integer parseLayerToken(String token) {
        if (token == null) return null;
        String lower = token.toLowerCase();
        if (lower.startsWith("l")) {
            try {
                int idx = Integer.parseInt(lower.substring(1)) - 1;
                if (idx >= 0 && idx < canvas.getLayerCount()) {
                    return idx;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    void setFrameRate(int fps) {
        frameRate = Math.max(1, fps);
        if (playTimer != null) {
            playTimer.setDelay(delayFromFPS());
            playTimer.setInitialDelay(0);
        }
    }

    private void ensureLayerNamesSize(int count) {
        if (layerNames.length == count) return;
        String[] prev = layerNames;
        layerNames = new String[count];
        for (int i = 0; i < count; i++) {
            if (i < prev.length) {
                layerNames[i] = prev[i];
            } else {
                layerNames[i] = "L" + (i + 1);
            }
        }
    }

    private void initLayerFrames(int count) {
        if (count <= 0) count = 1;
        layerFrames = new ArrayList[count];
        currentFrameIndex = new int[count];
        animatedLayers = new boolean[count];
        for (int i = 0; i < count; i++) {
            layerFrames[i] = new ArrayList<>();
            layerFrames[i].add(createEmptyFrameForLayer(i));
            currentFrameIndex[i] = 0;
            animatedLayers[i] = true;
        }
    }

    private void ensureFrameCapacity() {
        int expected = canvas != null ? canvas.getLayerCount() : (layerFrames == null ? 0 : layerFrames.length);
        if (layerFrames == null || layerFrames.length != expected) {
            initLayerFrames(expected);
        }
    }

    void addFrameFromCurrent() {
        ensureFrameCapacity();
        saveCurrentFrames();
        int layer = activeLayer;
        FrameData data = captureFrameForLayer(layer);
        List<FrameData> frames = layerFrames[layer];
        frames.add(data);
        currentFrameIndex[layer] = frames.size() - 1;
        applyFrameForLayer(layer, data);
        if (timeline != null) timeline.repaint();
    }

    void addBlankFrame() {
        ensureFrameCapacity();
        saveCurrentFrames();
        int layer = activeLayer;
        List<FrameData> frames = layerFrames[layer];
        int insertAt = Math.min(frames.size(), currentFrameIndex[layer] + 1);
        FrameData data = createEmptyFrameForLayer(layer);
        frames.add(insertAt, data);
        currentFrameIndex[layer] = insertAt;
        syncOtherLayersToActive(currentFrameIndex[layer]);
        applyAllCurrentFrames();
        if (timeline != null) timeline.repaint();
    }

    void duplicateCurrentFrame() {
        ensureFrameCapacity();
        int layer = activeLayer;
        List<FrameData> frames = layerFrames[layer];
        if (frames.isEmpty()) {
            addBlankFrame();
            return;
        }
        saveCurrentFrames();
        FrameData snapshot = captureFrameForLayer(layer);
        int insertAt = Math.min(frames.size(), currentFrameIndex[layer] + 1);
        frames.add(insertAt, snapshot);
        currentFrameIndex[layer] = insertAt;
        syncOtherLayersToActive(currentFrameIndex[layer]);
        applyAllCurrentFrames();
        if (timeline != null) timeline.repaint();
    }

    void deleteCurrentFrame() {
        ensureFrameCapacity();
        int layer = activeLayer;
        List<FrameData> frames = layerFrames[layer];
        if (frames.isEmpty()) return;
        frames.remove(currentFrameIndex[layer]);
        if (frames.isEmpty()) {
            FrameData blank = createEmptyFrameForLayer(layer);
            frames.add(blank);
            currentFrameIndex[layer] = 0;
            applyFrameForLayer(layer, blank);
        } else {
            currentFrameIndex[layer] = Math.max(0, Math.min(currentFrameIndex[layer], frames.size() - 1));
            applyFrameForLayer(layer, frames.get(currentFrameIndex[layer]));
        }
        if (timeline != null) timeline.repaint();
    }

    void selectFrame(int index) {
        ensureFrameCapacity();
        int layer = activeLayer;
        List<FrameData> frames = layerFrames[layer];
        if (index < 0 || index >= frames.size()) return;
        saveCurrentFrames();
        currentFrameIndex[layer] = index;
        playCursor = index;
        syncOtherLayersToActive(index);
        applyAllCurrentFrames();
        if (timeline != null) timeline.repaint();
    }

    void togglePlayback() {
        boolean anyFrames = false;
        for (List<FrameData> lf : layerFrames) {
            if (lf != null && lf.size() > 1) {
                anyFrames = true;
                break;
            }
        }
        if (!anyFrames) {
            console.setStatus("No frames to play");
            return;
        }
        syncOtherLayersToActive(currentFrameIndex[activeLayer]);
        playCursor = currentFrameIndex[activeLayer];
        playing = !playing;
        if (playing) {
            if (playTimer == null) {
                playTimer = new Timer(delayFromFPS(), e -> advanceFrame());
            }
            playTimer.setDelay(delayFromFPS());
            playTimer.start();
        } else {
            if (playTimer != null) playTimer.stop();
        }
        if (timeline != null) timeline.repaint();
    }

    private void advanceFrame() {
        ensureFrameCapacity();
        saveCurrentFrames();
        int maxLen = 0;
        for (int l = 0; l < layerFrames.length; l++) {
            List<FrameData> lf = layerFrames[l];
            if (lf == null) continue;
            if (!animatedLayers[l]) continue;
            maxLen = Math.max(maxLen, lf.size());
        }
        if (maxLen <= 0) {
            playing = false;
            if (playTimer != null) playTimer.stop();
            return;
        }
        playCursor = (playCursor + 1) % maxLen;
        for (int l = 0; l < layerFrames.length; l++) {
            List<FrameData> lf = layerFrames[l];
            if (lf == null || lf.isEmpty()) continue;
            if (!animatedLayers[l]) continue;
            currentFrameIndex[l] = playCursor % lf.size();
        }
        applyAllCurrentFrames();
        if (timeline != null) timeline.repaint();
    }

    private void syncOtherLayersToActive(int activeIndex) {
        for (int l = 0; l < layerFrames.length; l++) {
            if (l == activeLayer) continue;
            List<FrameData> lf = layerFrames[l];
            if (lf == null || lf.isEmpty()) continue;
            currentFrameIndex[l] = activeIndex % lf.size();
        }
    }

    private FrameData captureFrameForLayer(int layer) {
        return new FrameData(canvas.getLayerCopy(layer));
    }

    private void saveCurrentFrames() {
        if (layerFrames == null || currentFrameIndex == null) return;
        int layers = Math.min(canvas.getLayerCount(), layerFrames.length);
        for (int l = 0; l < layers; l++) {
            List<FrameData> frames = layerFrames[l];
            if (frames.isEmpty()) continue;
            int idx = Math.max(0, Math.min(currentFrameIndex[l], frames.size() - 1));
            frames.set(idx, captureFrameForLayer(l));
        }
    }

    private void resetAnimationState() {
        if (playTimer != null) playTimer.stop();
        playing = false;
        playCursor = 0;
        onionEnabled = false;
        initLayerFrames(canvas != null ? canvas.getLayerCount() : 3);
        if (layerNames == null) layerNames = new String[animatedLayers.length];
        ensureLayerNamesSize(animatedLayers.length);
        Arrays.fill(animatedLayers, true);
        if (timeline != null) {
            timeline.setVisible(false);
            timeline.repaint();
        }
        if (southWrap != null) southWrap.revalidate();
    }

    private FrameData createEmptyFrameForLayer(int layer) {
        int rows = canvas.getRows();
        int cols = canvas.getColumns();
        Color[][] data = new Color[rows][cols];
        return new FrameData(data);
    }

    private void applyFrameForLayer(int layer, FrameData data) {
        if (data == null) return;
        canvas.setLayer(layer, data.layer);
        if (controlBar != null) controlBar.repaint();
        canvas.repaint();
    }

    private void applyAllCurrentFrames() {
        for (int l = 0; l < layerFrames.length; l++) {
            List<FrameData> frames = layerFrames[l];
            if (frames.isEmpty()) continue;
            int idx = Math.max(0, Math.min(currentFrameIndex[l], frames.size() - 1));
            FrameData fd = frames.get(idx);
            applyFrameForLayer(l, fd);
        }
    }

    Color[][][] getOnionComposite() {
        ensureFrameCapacity();
        if (!onionEnabled) return null;
        List<FrameData> frames = layerFrames[activeLayer];
        if (frames.size() < 2) return null;
        int idx = Math.max(0, Math.min(currentFrameIndex[activeLayer], frames.size() - 1));
        int prevIdx = (idx - 1 + frames.size()) % frames.size();
        int nextIdx = (idx + 1) % frames.size();
        FrameData prev = frames.get(prevIdx);
        FrameData next = frames.get(nextIdx);
        int rows = canvas.getRows();
        int cols = canvas.getColumns();
        Color[][] prevComposite = new Color[rows][cols];
        Color[][] nextComposite = new Color[rows][cols];
        compositeFrame(prev.layer, prevComposite);
        compositeFrame(next.layer, nextComposite);
        return new Color[][][]{prevComposite, nextComposite};
    }

    private void compositeFrame(Color[][] layerData, Color[][] out) {
        if (layerData == null || out == null) return;
        int rows = out.length;
        int cols = out[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r][c] = layerData[r][c];
            }
        }
    }

    private Color[][] cloneLayer(Color[][] src) {
        if (src == null) return null;
        Color[][] copy = new Color[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = Arrays.copyOf(src[r], src[r].length);
        }
        return copy;
    }

    private Color[][] scaleLayer(Color[][] src, int factor) {
        if (src == null) return null;
        int oldRows = src.length;
        int oldCols = src[0].length;
        int newRows = oldRows * factor;
        int newCols = oldCols * factor;
        Color[][] out = new Color[newRows][newCols];
        for (int r = 0; r < newRows; r++) {
            int srcR = Math.min(oldRows - 1, r / factor);
            for (int c = 0; c < newCols; c++) {
                int srcC = Math.min(oldCols - 1, c / factor);
                out[r][c] = src[srcR][srcC];
            }
        }
        return out;
    }

    private <T> void swapArray(T[] arr, int i, int j) {
        T tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
    private void swapInt(int[] arr, int i, int j) {
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
    private void swapBoolean(boolean[] arr, int i, int j) {
        boolean tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
    private void swapString(String[] arr, int i, int j) {
        String tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    private int delayFromFPS() {
        return (int) Math.max(1, Math.round(1000.0 / Math.max(1, frameRate)));
    }

    private void installPanKeys(JFrame frame) {
        int step = 20;
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "panLeft");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "panRight");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"), "panUp");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"), "panDown");
        root.getActionMap().put("panLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { if (console != null && console.isFocused()) return; canvasHolder.pan(-step, 0); } });
        root.getActionMap().put("panRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { if (console != null && console.isFocused()) return; canvasHolder.pan(step, 0); } });
        root.getActionMap().put("panUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { if (console != null && console.isFocused()) return; canvasHolder.pan(0, -step); } });
        root.getActionMap().put("panDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { if (console != null && console.isFocused()) return; canvasHolder.pan(0, step); } });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("OPEN_BRACKET"), "brushDec");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("CLOSE_BRACKET"), "brushInc");
        root.getActionMap().put("brushDec", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocused()) return;
                setBrushSize(Math.max(1, getBrushSize() - 1));
            }
        });
        root.getActionMap().put("brushInc", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (console != null && console.isFocused()) return;
                setBrushSize(getBrushSize() + 1);
            }
        });
    }

    private void installConsoleToggle(JFrame frame) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "toggleConsole");
        root.getActionMap().put("toggleConsole", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (console.isFocusOwner()) {
                    if (canvasHolder != null) {
                        canvasHolder.requestFocusInWindow();
                    } else {
                        frame.requestFocusInWindow();
                    }
                } else {
                    console.requestFocusInWindow();
                }
            }
        });
    }

    static class FrameData implements Serializable {
        final Color[][] layer;
        FrameData(Color[][] layer) {
            this.layer = layer;
        }
    }

    private static class ProjectData implements Serializable {
        int cols;
        int rows;
        int cellSize;
        String[] layerNames;
        boolean[] layerVisible;
        boolean[] animatedLayers;
        int[] currentFrameIndex;
        int activeLayer;
        int brushSize;
        int red;
        int green;
        int blue;
        int frameRate;
        Color viewportBg;
        List<List<Color[][]>> layerFrames;
    }
}
