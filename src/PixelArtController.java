import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import javax.swing.Timer;

public class PixelArtController {
    private final PixelArtModel model;
    private final PixelArtView view;
    private PixelArtFileHandler fileHandler;
    private PixelCanvas canvas;
    private final java.util.Deque<Color[][][]> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<Color[][][]> redoStack = new java.util.ArrayDeque<>();
    private static final int UNDO_LIMIT = 30;
    private ConsolePanel console;
    private ControlBar controlBar;
    private TopBar topBar;
    private StampPanel stampPanel;
    private PixelCanvas stampCanvas;
    private AnimationPanel animationPanel;
    private Timer playTimer;
    private int playCursor = 0;

    public PixelArtController(PixelArtModel model, PixelArtView view) {
        this.model = model;
        this.view = view;
        // this.fileHandler = new PixelArtFileHandler(model, view);
        setupViewCallbacks();
        normalizeColorState();
        model.setCanvasCellSize(computeMaxCellSizeForScreen());
        buildCanvas();
        buildConsole();
        buildControlBar();
        buildTopBar();
        buildStampPanel();
        buildAnimationPanel();
        view.setViewportBackground(model.getViewportBg());
        view.setCanvasCellSize(model.getCanvasCellSize());
        view.recenterViewport();
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    private void setupViewCallbacks() {
        view.setCanvasPickCallback(this::pickBrushColor);
        view.setBrushSizeCallback(this::setBrushSize);
        view.setToolModeCallback(model::getToolMode);
        view.setStampCallback(model::getStampPixels);
        view.setOnionCallback(this::getOnionComposite);
        view.setActiveLayerCallback(model::getActiveLayer);
        view.setLayerVisibleCallback(model::isLayerVisible);
        view.setPanBlockCallback(() -> false); // Implement if needed
        view.setUndoCallback(this::performUndo);
        view.setRedoCallback(this::performRedo);
    }

    private void buildCanvas() {
        canvas = new PixelCanvas(
                model.getColumns(),
                model.getRows(),
                model.getCanvasCellSize(),
                this::pickBrushColor,
                this::setBrushSize,
                model::getToolMode,
                model::getStampPixels,
                this::getOnionComposite,
                model::getActiveLayer,
                model.getLayerCount(),
                model::isLayerVisible,
                () -> false,
                false,
                this::recordUndoSnapshot,
                model.getLayers());
        canvas.setCurrentColor(model.getCurrentBrushColor());
        canvas.setBrushSize(model.getBrushSize());
        view.setCanvasController(canvas);
    }

    private void buildConsole() {
        console = new ConsolePanel(this::handleCommand);
        view.setConsoleController(console);
    }

    private void buildControlBar() {
        ControlBar.Host host = new ControlBar.Host() {
            @Override
            public int getRed() { return model.getColorState().getRed(); }
            @Override
            public int getGreen() { return model.getColorState().getGreen(); }
            @Override
            public int getBlue() { return model.getColorState().getBlue(); }
            @Override
            public int getHueDegrees() { return model.getColorState().getHue(); }
            @Override
            public int getSaturationPercent() { return model.getColorState().getSaturation(); }
            @Override
            public int getBrightnessPercent() { return model.getColorState().getBrightness(); }
            @Override
            public int getBrushSize() { return model.getBrushSize(); }
            @Override
            public int getActiveLayer() { return model.getActiveLayer(); }
            @Override
            public int computeMaxCellSizeForScreen() { return PixelArtController.this.computeMaxCellSizeForScreen(); }
            @Override
            public int getCanvasCellSize() { return model.getCanvasCellSize(); }
            @Override
            public void setRed(int v) { setRedChannel(v); }
            @Override
            public void setGreen(int v) { setGreenChannel(v); }
            @Override
            public void setBlue(int v) { setBlueChannel(v); }
            @Override
            public void setHueDegrees(int v) { setHue(v); }
            @Override
            public void setSaturationPercent(int v) { setSaturation(v); }
            @Override
            public void setBrightnessPercent(int v) { setBrightness(v); }
            @Override
            public void setBrushSize(int v) { PixelArtController.this.setBrushSize(v); }
            @Override
            public void setCanvasCellSize(int v) { setCanvasCellSizeValue(v); }
            @Override
            public void setToolMode(PixelArtApp.ToolMode mode) { model.setToolMode(mode); }
            @Override
            public PixelArtApp.ToolMode getToolMode() { return model.getToolMode(); }
            @Override
            public void setActiveLayer(int idx) { PixelArtController.this.setActiveLayer(idx); }
            @Override
            public void toggleLayerVisibility(int idx) { PixelArtController.this.toggleLayerVisibility(idx); }
            @Override
            public void swapLayerUp(int idx) { PixelArtController.this.swapLayerUp(idx); }
            @Override
            public PixelCanvas getCanvas() { return canvas; }
            @Override
            public Color currentBrushColor() { return model.getCurrentBrushColor(); }
            @Override
            public String getLayerName(int idx) { return model.getLayerName(idx); }
            @Override
            public boolean isLayerVisible(int idx) { return model.isLayerVisible(idx); }
        };
        controlBar = new ControlBar(host);
        view.setControlBarController(controlBar);
    }

    private void buildTopBar() {
        TopBar.Host host = new TopBar.Host() {
            @Override
            public Color currentBrushColor() {
                return model.getCurrentBrushColor();
            }

            @Override
            public void pickBrushColor(Color color) {
                PixelArtController.this.pickBrushColor(color);
            }
        };
        topBar = new TopBar(host);
        view.setTopBarController(topBar);
    }

    private void buildStampPanel() {
        Color[][] stampBackingLayer = model.getStampPixels();
        Color[][][] backing = new Color[][][] { stampBackingLayer };
        int stampCellSize = 10;
        stampCanvas = new PixelCanvas(
                16,
                16,
                stampCellSize,
                this::pickBrushColor,
                this::setBrushSize,
                () -> model.getToolMode() == PixelArtApp.ToolMode.ERASER ? PixelArtApp.ToolMode.ERASER
                        : PixelArtApp.ToolMode.BRUSH,
                null,
                null,
                () -> 0,
                1,
                l -> true,
                null,
                true,
                null,
                backing);
        stampCanvas.setCurrentColor(model.getCurrentBrushColor());
        stampCanvas.setBrushSize(model.getBrushSize());
        stampCanvas.setStampUsesOwnColors(model.isStampUseOwnColors());
        StampPanel.Host host = new StampPanel.Host() {
            @Override
            public boolean isStampUsingOwnColors() {
                return model.isStampUseOwnColors();
            }

            @Override
            public void setStampUseOwnColors(boolean useOwn) {
                model.setStampUseOwnColors(useOwn);
                if (canvas != null) {
                    canvas.setStampUsesOwnColors(useOwn);
                }
                stampCanvas.setStampUsesOwnColors(useOwn);
            }
        };
        stampPanel = new StampPanel(stampCanvas, host);
        view.setStampController(stampPanel);
    }

    private void buildAnimationPanel() {
        AnimationPanel.Host host = new AnimationPanel.Host() {
            @Override
            public boolean isPlaying() { return model.isPlaying(); }
            @Override
            public boolean isOnionEnabled() { return model.isOnionEnabled(); }
            @Override
            public int frameCount() { return model.getLayerFrames()[model.getActiveLayer()].size(); }
            @Override
            public int currentFrameIndex() { return model.getCurrentFrameIndex()[model.getActiveLayer()]; }
            @Override
            public void togglePlayback() { PixelArtController.this.togglePlayback(); }
            @Override
            public void toggleOnion() { PixelArtController.this.toggleOnion(); }
            @Override
            public void addBlankFrame() { PixelArtController.this.addBlankFrame(); repaintTimeline(); }
            @Override
            public void deleteCurrentFrame() { PixelArtController.this.deleteCurrentFrame(); repaintTimeline(); }
            @Override
            public void duplicateCurrentFrame() { PixelArtController.this.duplicateCurrentFrame(); repaintTimeline(); }
            @Override
            public void selectFrame(int index) { PixelArtController.this.selectFrame(index); repaintTimeline(); }
        };
        animationPanel = new AnimationPanel(host);
        view.setAnimationController(animationPanel);
        view.showAnimationPanel(true);
    }

    private void handleCommand(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        String[] parts = input.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();
        try {
            switch (cmd) {
                case "new" -> {
                    if (parts.length == 2) {
                        int size = Integer.parseInt(parts[1]);
                        rebuildCanvas(size, size);
                        view.setConsoleStatus("New canvas " + size + "x" + size);
                    } else if (parts.length == 3) {
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        rebuildCanvas(w, h);
                        view.setConsoleStatus("New canvas " + w + "x" + h);
                    } else {
                        view.setConsoleStatus("Usage: new <size> or new <w> <h>");
                    }
                }
                case "resolution" -> view.setConsoleStatus(model.getColumns() + "x" + model.getRows());
                case "brush" -> {
                    if (parts.length < 2) {
                        view.setConsoleStatus("Usage: brush <size>");
                        return;
                    }
                    int size = Integer.parseInt(parts[1]);
                    setBrushSize(size);
                    view.setConsoleStatus("Brush " + size);
                }
                case "animate" -> {
                    view.showAnimationPanel(true);
                    repaintTimeline();
                    view.setConsoleStatus("Animation panel open");
                }
                case "background" -> {
                    if (parts.length < 4) {
                        view.setConsoleStatus("Usage: background <r> <g> <b>");
                        return;
                    }
                    int r = clamp(Integer.parseInt(parts[1]));
                    int g = clamp(Integer.parseInt(parts[2]));
                    int b = clamp(Integer.parseInt(parts[3]));
                    setViewportBackground(new Color(r, g, b));
                    view.setConsoleStatus("Background set");
                }
                case "exit" -> System.exit(0);
                default -> view.setConsoleStatus("Unknown command: " + cmd);
            }
        } catch (NumberFormatException ex) {
            view.setConsoleStatus("Invalid number");
        }
    }

    // Canvas operations
    public void rebuildCanvas(int newCols, int newRows) {
        model.setGridSize(Math.max(newCols, newRows));
        model.setCanvasCellSize(computeMaxCellSizeForScreen());
        model.initLayers();
        model.initLayerFrames(model.getLayerCount());
        model.applyAllCurrentFrames();
        buildCanvas();
        view.setCanvasCellSize(model.getCanvasCellSize());
        view.recenterViewport();
        view.repaintControls();
    }

    public void loadImage(String path) throws IOException {
        fileHandler.loadImage(path);
    }

    public void saveImage(String path) throws IOException {
        fileHandler.saveImage(path);
    }

    public void saveSequence(String basePath) throws IOException {
        fileHandler.saveSequence(basePath);
    }

    public void saveGif(String path) throws IOException {
        fileHandler.saveGif(path);
    }

    public void saveProject(String path) throws IOException {
        fileHandler.saveProject(path);
    }

    public void loadProject(String path) throws IOException, ClassNotFoundException {
        fileHandler.loadProject(path);
    }

    // Tool operations
    public void flipHorizontal() {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.flipHorizontal(layer);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void flipVertical() {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.flipVertical(layer);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void blurGaussian(int radius) {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.blurGaussian(layer, radius);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void blurMotion(double angle, int amount) {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.blurMotion(layer, angle, amount);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void ditherFloydSteinberg() {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.ditherFloydSteinberg(layer, PixelArtApp.CANVAS_BG);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void ditherOrdered() {
        recordUndoSnapshot();
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        PixelOps.ditherOrdered(layer, PixelArtApp.CANVAS_BG);
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void resampleCanvas(int factor) {
        // Implement resampling
        view.repaintCanvas();
    }

    // Color and brush
    public void pickBrushColor(Color color) {
        model.getColorState().setFromColor(color);
        view.updateBrushTargets(color);
        syncColorControls();
    }

    public void setBrushSize(int size) {
        model.setBrushSize(size);
        view.repaintCanvas();
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    // Color channel helpers for control bar
    public void setRedChannel(int v) {
        model.getColorState().setRed(clamp(v));
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setGreenChannel(int v) {
        model.getColorState().setGreen(clamp(v));
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setBlueChannel(int v) {
        model.getColorState().setBlue(clamp(v));
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setHue(int deg) {
        model.getColorState().setHue(deg);
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setSaturation(int pct) {
        model.getColorState().setSaturation(pct);
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setBrightness(int pct) {
        model.getColorState().setBrightness(pct);
        view.updateBrushTargets(model.getCurrentBrushColor());
        syncColorControls();
    }

    public void setBrushColor(Color color) {
        model.getColorState().setFromColor(color);
        view.updateBrushTargets(color);
        syncColorControls();
    }

    private void normalizeColorState() {
        // Ensure HSB mirrors the initial RGB so sliders start in sync
        model.getColorState().setFromColor(model.getColorState().getColor());
    }

    private void syncColorControls() {
        if (controlBar != null) {
            controlBar.syncSliders();
        }
        view.repaintControls();
    }

    // Layers
    public void setActiveLayer(int layer) {
        int clamped = Math.max(0, Math.min(model.getLayerCount() - 1, layer));
        model.setActiveLayer(clamped);
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public boolean isLayerVisible(int layer) {
        return model.isLayerVisible(layer);
    }

    public void toggleLayerVisibility(int layer) {
        model.toggleLayerVisibility(layer);
        view.repaintCanvas();
        view.repaintControls();
    }

    public void swapLayerUp(int idx) {
        if (idx <= 0 || idx >= model.getLayerCount()) {
            return;
        }
        model.swapLayers(idx, idx - 1);
        if (model.getActiveLayer() == idx)
            model.setActiveLayer(idx - 1);
        else if (model.getActiveLayer() == idx - 1)
            model.setActiveLayer(idx);
        view.repaintControls();
        view.repaintCanvas();
    }

    public String getLayerName(int idx) {
        return model.getLayerName(idx);
    }

    public void setLayerName(int idx, String name) {
        model.setLayerName(idx, name);
    }

    // Animation
    public void togglePlayback() {
        model.saveCurrentFrames();
        boolean anyFrames = false;
        for (int i = 0; i < model.getLayerCount(); i++) {
            if (model.getLayerFrames()[i].size() > 1) {
                anyFrames = true;
                break;
            }
        }
        if (!anyFrames) {
            view.setConsoleStatus("No frames to play");
            return;
        }
        model.setPlaying(!model.isPlaying());
        if (model.isPlaying()) {
            playCursor = model.getCurrentFrameIndex()[model.getActiveLayer()];
            startPlayback();
        } else {
            stopPlayback();
        }
        view.repaintControls();
        repaintTimeline();
    }

    public void stepFrame(int delta) {
        model.saveCurrentFrames();
        model.stepFrame(delta);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void selectFrame(int index) {
        model.saveCurrentFrames();
        model.selectFrame(index);
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void addFrameFromCurrent() {
        model.saveCurrentFrames();
        model.addFrameFromCurrent();
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void addBlankFrame() {
        model.saveCurrentFrames();
        model.addBlankFrame();
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void duplicateCurrentFrame() {
        model.saveCurrentFrames();
        model.duplicateCurrentFrame();
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void deleteCurrentFrame() {
        model.deleteCurrentFrame();
        syncOtherLayersToActive(model.getCurrentFrameIndex()[model.getActiveLayer()]);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    public void setFrameRate(int fps) {
        model.setFrameRate(fps);
        if (playTimer != null) {
            playTimer.setDelay(delayFromFPS());
            playTimer.setInitialDelay(0);
        }
    }

    private void repaintTimeline() {
        if (animationPanel != null) {
            animationPanel.repaint();
        }
    }

    private void startPlayback() {
        if (playTimer == null) {
            playTimer = new Timer(delayFromFPS(), e -> advancePlayback());
            playTimer.setInitialDelay(0);
        }
        playTimer.setDelay(delayFromFPS());
        playTimer.start();
    }

    private void stopPlayback() {
        if (playTimer != null) {
            playTimer.stop();
        }
    }

    private void advancePlayback() {
        int maxLen = 0;
        for (int l = 0; l < model.getLayerCount(); l++) {
            int len = model.getLayerFrames()[l].size();
            if (len > 0) {
                maxLen = Math.max(maxLen, len);
            }
        }
        if (maxLen <= 0) {
            stopPlayback();
            model.setPlaying(false);
            view.repaintControls();
            repaintTimeline();
            return;
        }
        playCursor = (playCursor + 1) % maxLen;
        int[] idx = model.getCurrentFrameIndex().clone();
        boolean[] animated = model.getAnimatedLayers();
        for (int l = 0; l < model.getLayerCount(); l++) {
            int len = model.getLayerFrames()[l].size();
            if (len == 0) continue;
            if (animated != null && l < animated.length && !animated[l]) {
                continue;
            }
            idx[l] = playCursor % len;
        }
        model.setCurrentFrameIndex(idx);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
        view.repaintControls();
        repaintTimeline();
    }

    private void syncOtherLayersToActive(int activeIndex) {
        int[] idx = model.getCurrentFrameIndex();
        for (int l = 0; l < model.getLayerCount(); l++) {
            if (l == model.getActiveLayer()) continue;
            int len = model.getLayerFrames()[l].size();
            if (len == 0) continue;
            idx[l] = activeIndex % len;
        }
        model.setCurrentFrameIndex(idx);
    }

    private int delayFromFPS() {
        return (int) Math.max(1, Math.round(1000.0 / Math.max(1, model.getFrameRate())));
    }

    public void toggleOnion() {
        model.setOnionEnabled(!model.isOnionEnabled());
        view.repaintCanvas();
        repaintTimeline();
    }

    // Viewport
    public void setViewportBackground(Color color) {
        model.setViewportBg(color);
        view.setViewportBackground(color);
    }

    public void setCanvasCellSizeValue(int size) {
        int capped = Math.max(2, Math.min(256, size));
        model.setCanvasCellSize(capped);
        if (canvas != null) {
            canvas.setCellSize(capped);
            view.recenterViewport();
        }
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    // Utility
    public Color[][][] getOnionComposite() {
        if (!model.isOnionEnabled())
            return null;
        // Implement onion composite logic
        return null;
    }

    public void performUndo() {
        if (undoStack.isEmpty())
            return;
        pushRedoSnapshot();
        Color[][][] prev = undoStack.pop();
        copyInto(model.getLayers(), prev);
        view.repaintCanvas();
        view.repaintControls();
    }

    public void performRedo() {
        if (redoStack.isEmpty())
            return;
        pushUndoSnapshot();
        Color[][][] next = redoStack.pop();
        copyInto(model.getLayers(), next);
        view.repaintCanvas();
        view.repaintControls();
    }

    private int computeCellSizeForGrid(int grid) {
        int computed = 640 / grid;
        return Math.max(2, Math.min(256, computed));
    }

    private int computeMaxCellSizeForScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int usableWidth = Math.max(200, (int) screen.getWidth() - PixelArtApp.CONTROL_BAR_WIDTH - 200);
        int usableHeight = Math.max(200, (int) screen.getHeight() - 200);
        int cols = Math.max(1, model.getColumns());
        int rows = Math.max(1, model.getRows());
        int maxByWidth = Math.max(2, usableWidth / cols);
        int maxByHeight = Math.max(2, usableHeight / rows);
        return Math.min(256, Math.min(maxByWidth, maxByHeight));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void recordUndoSnapshot() {
        pushUndoSnapshot();
        redoStack.clear();
    }

    private void pushUndoSnapshot() {
        undoStack.push(cloneLayers(model.getLayers()));
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    private void pushRedoSnapshot() {
        redoStack.push(cloneLayers(model.getLayers()));
        while (redoStack.size() > UNDO_LIMIT) {
            redoStack.removeLast();
        }
    }

    private Color[][][] cloneLayers(Color[][][] src) {
        Color[][][] copy = new Color[src.length][][];
        for (int l = 0; l < src.length; l++) {
            copy[l] = new Color[src[l].length][];
            for (int r = 0; r < src[l].length; r++) {
                copy[l][r] = java.util.Arrays.copyOf(src[l][r], src[l][r].length);
            }
        }
        return copy;
    }

    private void copyInto(Color[][][] dest, Color[][][] src) {
        for (int l = 0; l < Math.min(dest.length, src.length); l++) {
            for (int r = 0; r < Math.min(dest[l].length, src[l].length); r++) {
                System.arraycopy(src[l][r], 0, dest[l][r], 0, Math.min(dest[l][r].length, src[l][r].length));
            }
        }
    }
}
