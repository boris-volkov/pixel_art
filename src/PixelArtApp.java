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
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PixelArtApp {
    enum ToolMode {BRUSH, STAMP, FILL, BLUR, MOVE}

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
    private final List<FrameData> frames = new ArrayList<>();
    private int currentFrameIndex = 0;
    private Timer playTimer;
    private boolean playing = false;
    private int red = 32;
    private int green = 32;
    private int blue = 32;
    private int brushSize = 1;
    private int gridSize = 128;
    private int canvasCellSize = computeMaxCellSizeForScreen();
    private ToolMode toolMode = ToolMode.BRUSH;
    private int activeLayer = 0;
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

        canvas = new PixelCanvas(gridSize, gridSize, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getActiveLayer, 3, this::isLayerVisible, null);
        canvas.setCurrentColor(currentBrushColor());
        canvas.setBrushSize(brushSize);

        int stampCellSize = 10;
        stampCanvas = new PixelCanvas(16, 16, stampCellSize, this::setBrushColor, this::setBrushSize, () -> ToolMode.BRUSH, null, () -> 0, 1, l -> true, null);
        stampCanvas.setCurrentColor(currentBrushColor());

        canvasHolder = new CanvasViewport(canvas);
        canvasHolder.setBackground(BG);

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
        StampPanel stampHolder = new StampPanel(stampCanvas);
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
        gridSize = Math.max(newCols, newRows);
        canvasCellSize = computeMaxCellSizeForScreen();
        PixelCanvas newCanvas = new PixelCanvas(newCols, newRows, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getActiveLayer, 3, this::isLayerVisible, null);
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;
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
        Color[][] old = canvas.getPixelsCopy();
        int oldRows = canvas.getRows();
        int oldCols = canvas.getColumns();
        int newRows = oldRows * factor;
        int newCols = oldCols * factor;
        gridSize = newRows;
        canvasCellSize = Math.min(canvasCellSize, MAX_CELL_SIZE);
        PixelCanvas newCanvas = new PixelCanvas(newCols, newRows, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getActiveLayer, 3, this::isLayerVisible, null);
        for (int r = 0; r < newRows; r++) {
            int srcR = Math.min(oldRows - 1, r / factor);
            for (int c = 0; c < newCols; c++) {
                int srcC = Math.min(oldCols - 1, c / factor);
                Color color = old[srcR][srcC];
                newCanvas.setPixelDirect(r, c, color);
            }
        }
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;

        canvasHolder.setCanvas(newCanvas);
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
                console.setStatus("Resolution: " + gridSize + "x" + gridSize);
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
                console.setStatus("Commands: save <file.png> | load <file.png> | resolution | new <size> | flip h | flip v | blur gaussian <r> | blur motion <angle> <amt> | dither floyd | dither ordered | resample <factor> | calc | animate | exit");
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
                if (!timeline.isVisible()) {
                    timeline.setVisible(true);
                    if (frames.isEmpty()) {
                        addFrameFromCurrent();
                    }
                    console.setStatus("Animation panel opened. Frames: " + frames.size());
                    southWrap.revalidate();
                } else {
                    console.setStatus("Animation panel already open");
                }
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

    private void loadImage(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("Unsupported image");
        int w = img.getWidth();
        int h = img.getHeight();
        if (w != h) {
            throw new IOException("Image must be square");
        }
        gridSize = w;
        canvasCellSize = Math.min(canvasCellSize, MAX_CELL_SIZE);
        PixelCanvas newCanvas = new PixelCanvas(w, h, canvasCellSize, this::setBrushColor, this::setBrushSize, this::getToolMode, this::getStampPixels, this::getActiveLayer, 3, this::isLayerVisible, null);
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
    void setActiveLayer(int layer) { activeLayer = Math.max(0, Math.min(2, layer)); }
    boolean isLayerVisible(int layer) { return layerVisible[Math.max(0, Math.min(2, layer))]; }
    void toggleLayerVisibility(int layer) {
        int idx = Math.max(0, Math.min(2, layer));
        layerVisible[idx] = !layerVisible[idx];
        if (canvas != null) {
            canvas.repaint();
        }
    }

    // Animation helpers
    List<FrameData> getFrames() { return frames; }
    int getCurrentFrameIndex() { return currentFrameIndex; }
    boolean isPlaying() { return playing; }

    void addFrameFromCurrent() {
        saveCurrentFrame();
        FrameData data = captureFrame();
        frames.add(data);
        currentFrameIndex = frames.size() - 1;
        if (timeline != null) timeline.repaint();
    }

    void addBlankFrame() {
        saveCurrentFrame();
        FrameData data = createEmptyFrame();
        frames.add(data);
        currentFrameIndex = frames.size() - 1;
        applyFrame(data);
        if (timeline != null) timeline.repaint();
    }

    void selectFrame(int index) {
        if (index < 0 || index >= frames.size()) return;
        saveCurrentFrame();
        currentFrameIndex = index;
        applyFrame(frames.get(index));
        if (timeline != null) timeline.repaint();
    }

    void togglePlayback() {
        if (frames.isEmpty()) {
            console.setStatus("No frames to play");
            return;
        }
        playing = !playing;
        if (playing) {
            if (playTimer == null) {
                playTimer = new Timer(180, e -> advanceFrame());
            }
            playTimer.start();
        } else {
            if (playTimer != null) playTimer.stop();
        }
        if (timeline != null) timeline.repaint();
    }

    private void advanceFrame() {
        if (frames.isEmpty()) {
            playing = false;
            if (playTimer != null) playTimer.stop();
            return;
        }
        currentFrameIndex = (currentFrameIndex + 1) % frames.size();
        applyFrame(frames.get(currentFrameIndex));
        if (timeline != null) timeline.repaint();
    }

    private FrameData captureFrame() {
        Color[][][] layersCopy = canvas.getLayersCopy();
        boolean[] visCopy = layerVisible.clone();
        return new FrameData(layersCopy, visCopy);
    }

    private void saveCurrentFrame() {
        if (frames.isEmpty()) return;
        frames.set(currentFrameIndex, captureFrame());
    }

    private FrameData createEmptyFrame() {
        int layerCount = canvas.getLayerCount();
        int rows = canvas.getRows();
        int cols = canvas.getColumns();
        Color[][][] emptyLayers = new Color[layerCount][rows][cols];
        boolean[] visCopy = layerVisible.clone();
        return new FrameData(emptyLayers, visCopy);
    }

    private void applyFrame(FrameData data) {
        if (data == null) return;
        System.arraycopy(data.visibility, 0, layerVisible, 0, layerVisible.length);
        canvas.setLayers(data.layers);
        if (controlBar != null) controlBar.repaint();
        canvas.repaint();
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

    static class FrameData {
        final Color[][][] layers;
        final boolean[] visibility;
        FrameData(Color[][][] layers, boolean[] visibility) {
            this.layers = layers;
            this.visibility = visibility;
        }
    }
}
