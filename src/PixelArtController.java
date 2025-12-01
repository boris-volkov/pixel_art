import java.awt.Color;
import java.io.IOException;

public class PixelArtController {
    private PixelArtModel model;
    private PixelArtView view;
    private PixelArtFileHandler fileHandler;

    public PixelArtController(PixelArtModel model, PixelArtView view) {
        this.model = model;
        this.view = view;
        // this.fileHandler = new PixelArtFileHandler(model, view);
        setupViewCallbacks();
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
    }

    // Canvas operations
    public void rebuildCanvas(int newCols, int newRows) {
        model.setGridSize(Math.max(newCols, newRows));
        model.setCanvasCellSize(computeCellSizeForGrid(model.getGridSize()));
        model.initLayers();
        model.initLayerFrames(model.getLayerCount());
        model.applyAllCurrentFrames();
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
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        // Implement flip logic
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void flipVertical() {
        model.saveCurrentFrames();
        Color[][] layer = model.getLayerCopy(model.getActiveLayer());
        // Implement flip logic
        model.setLayer(model.getActiveLayer(), layer);
        model.applyAllCurrentFrames();
        view.repaintCanvas();
    }

    public void blurGaussian(int radius) {
        // Implement Gaussian blur
        view.repaintCanvas();
    }

    public void blurMotion(double angle, int amount) {
        // Implement motion blur
        view.repaintCanvas();
    }

    public void ditherFloydSteinberg() {
        // Implement dithering
        view.repaintCanvas();
    }

    public void ditherOrdered() {
        // Implement ordered dithering
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
    }

    public void setBrushSize(int size) {
        model.setBrushSize(size);
        view.repaintCanvas();
    }

    public void setBrushColor(Color color) {
        model.getColorState().setFromColor(color);
        view.updateBrushTargets(color);
    }

    // Layers
    public void setActiveLayer(int layer) {
        model.setActiveLayer(layer);
        view.repaintControls();
    }

    public boolean isLayerVisible(int layer) {
        return model.isLayerVisible(layer);
    }

    public void toggleLayerVisibility(int layer) {
        model.toggleLayerVisibility(layer);
        view.repaintCanvas();
    }

    public void swapLayerUp(int idx) {
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
        // Start/stop timer logic
        view.repaintControls();
    }

    public void stepFrame(int delta) {
        model.stepFrame(delta);
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void selectFrame(int index) {
        model.selectFrame(index);
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void addFrameFromCurrent() {
        model.addFrameFromCurrent();
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void addBlankFrame() {
        model.addBlankFrame();
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void duplicateCurrentFrame() {
        model.duplicateCurrentFrame();
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void deleteCurrentFrame() {
        model.deleteCurrentFrame();
        model.applyAllCurrentFrames();
        view.repaintControls();
    }

    public void setFrameRate(int fps) {
        model.setFrameRate(fps);
        // Update timer if needed
    }

    public void toggleOnion() {
        model.setOnionEnabled(!model.isOnionEnabled());
        view.repaintCanvas();
    }

    // Viewport
    public void setViewportBackground(Color color) {
        model.setViewportBg(color);
        view.setViewportBackground(color);
    }

    // Utility
    public Color[][][] getOnionComposite() {
        if (!model.isOnionEnabled())
            return null;
        // Implement onion composite logic
        return null;
    }

    public void performUndo() {
        // Implement undo
        view.repaintCanvas();
        view.repaintControls();
    }

    public void performRedo() {
        // Implement redo
        view.repaintCanvas();
        view.repaintControls();
    }

    private int computeCellSizeForGrid(int grid) {
        int computed = 640 / grid;
        return Math.max(2, Math.min(256, computed));
    }
}
