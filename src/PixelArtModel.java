import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PixelArtModel implements Serializable {
    private static final long serialVersionUID = 1L;

    // Canvas and layers
    private int gridSize = 128;
    private int canvasCellSize = 16;
    private int layerCount = 3;
    private Color[][][] layers; // [layer][row][col]
    private boolean[] layerVisible = { true, true, true };
    private String[] layerNames = { "L1", "L2", "L3" };

    // Animation
    private List<FrameData>[] layerFrames;
    private int[] currentFrameIndex;
    private boolean[] animatedLayers;
    private int frameRate = 6;
    private boolean playing = false;
    private int playCursor = 0;
    private boolean onionEnabled = false;

    // Tools and state
    private ColorState colorState = new ColorState();
    private int brushSize = 1;
    private ToolMode toolMode = ToolMode.BRUSH;
    private int activeLayer = 0;
    private boolean stampUseOwnColors = true;

    // Stamp
    private Color[][] stampPixels; // 16x16

    // Viewport
    private Color viewportBg = PixelConstants.BG;

    // Serialization data
    public static class ProjectData implements Serializable {
        private static final long serialVersionUID = 1L;
        int cols, rows, cellSize, activeLayer, brushSize, red, green, blue, frameRate;
        String[] layerNames;
        boolean[] layerVisible, animatedLayers;
        int[] currentFrameIndex;
        Color viewportBg;
        List<List<Color[][]>> layerFrames;
    }

    public PixelArtModel() {
        initLayers();
        initAnimation();
        initStamp();
    }

    public PixelArtModel(int cols, int rows) {
        this.gridSize = Math.max(cols, rows);
        this.canvasCellSize = computeCellSizeForGrid(gridSize);
        this.layerCount = 3;
        initLayers();
        initAnimation();
        initStamp();
    }

    public void initLayers() {
        layers = new Color[layerCount][gridSize][gridSize];
        layerVisible = new boolean[layerCount];
        Arrays.fill(layerVisible, true);
        layerNames = new String[layerCount];
        for (int i = 0; i < layerCount; i++) {
            layerNames[i] = "L" + (i + 1);
        }
    }

    private void initAnimation() {
        layerFrames = new ArrayList[layerCount];
        currentFrameIndex = new int[layerCount];
        animatedLayers = new boolean[layerCount];
        for (int i = 0; i < layerCount; i++) {
            layerFrames[i] = new ArrayList<>();
            layerFrames[i].add(new FrameData(new Color[gridSize][gridSize]));
            currentFrameIndex[i] = 0;
            animatedLayers[i] = true;
        }
    }

    private void initStamp() {
        stampPixels = new Color[16][16];
    }

    // Getters and setters
    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int size) {
        this.gridSize = size;
    }

    public int getCanvasCellSize() {
        return canvasCellSize;
    }

    public void setCanvasCellSize(int size) {
        this.canvasCellSize = size;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public Color[][][] getLayers() {
        return layers;
    }

    public void setLayers(Color[][][] layers) {
        this.layers = layers;
    }

    public boolean[] getLayerVisible() {
        return layerVisible;
    }

    public void setLayerVisible(boolean[] visible) {
        this.layerVisible = visible;
    }

    public String[] getLayerNames() {
        return layerNames;
    }

    public void setLayerNames(String[] names) {
        this.layerNames = names;
    }

    public List<FrameData>[] getLayerFrames() {
        return layerFrames;
    }

    public void setLayerFrames(List<FrameData>[] frames) {
        this.layerFrames = frames;
    }

    public int[] getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public void setCurrentFrameIndex(int[] index) {
        this.currentFrameIndex = index;
    }

    public boolean[] getAnimatedLayers() {
        return animatedLayers;
    }

    public void setAnimatedLayers(boolean[] animated) {
        this.animatedLayers = animated;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int rate) {
        this.frameRate = rate;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getPlayCursor() {
        return playCursor;
    }

    public void setPlayCursor(int cursor) {
        this.playCursor = cursor;
    }

    public boolean isOnionEnabled() {
        return onionEnabled;
    }

    public void setOnionEnabled(boolean enabled) {
        this.onionEnabled = enabled;
    }

    public ColorState getColorState() {
        return colorState;
    }

    public void setColorState(ColorState state) {
        this.colorState = state;
    }

    public int getBrushSize() {
        return brushSize;
    }

    public void setBrushSize(int size) {
        this.brushSize = size;
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setToolMode(ToolMode mode) {
        this.toolMode = mode;
    }

    public int getActiveLayer() {
        return activeLayer;
    }

    public void setActiveLayer(int layer) {
        this.activeLayer = layer;
    }

    public boolean isStampUseOwnColors() {
        return stampUseOwnColors;
    }

    public void setStampUseOwnColors(boolean use) {
        this.stampUseOwnColors = use;
    }

    public Color[][] getStampPixels() {
        return stampPixels;
    }

    public void setStampPixels(Color[][] pixels) {
        this.stampPixels = pixels;
    }

    public Color getViewportBg() {
        return viewportBg;
    }

    public void setViewportBg(Color bg) {
        this.viewportBg = bg;
    }

    // Utility methods
    public int getRows() {
        return gridSize;
    }

    public int getColumns() {
        return gridSize;
    }

    public Color getCurrentBrushColor() {
        return colorState.getColor();
    }

    public void setPixel(int row, int col, Color color) {
        if (row >= 0 && row < gridSize && col >= 0 && col < gridSize) {
            layers[activeLayer][row][col] = color;
        }
    }

    public boolean isLayerVisible(int layer) {
        if (layer >= 0 && layer < layerCount)
            return layerVisible[layer];
        return false;
    }

    public void toggleLayerVisibility(int layer) {
        if (layer >= 0 && layer < layerCount)
            layerVisible[layer] = !layerVisible[layer];
    }

    public String getLayerName(int layer) {
        if (layer >= 0 && layer < layerCount)
            return layerNames[layer];
        return "";
    }

    public void setLayerName(int layer, String name) {
        if (layer >= 0 && layer < layerCount)
            layerNames[layer] = name;
    }

    public Color[][] getLayerCopy(int layer) {
        if (layer < 0 || layer >= layerCount)
            return null;
        Color[][] copy = new Color[gridSize][gridSize];
        for (int r = 0; r < gridSize; r++) {
            copy[r] = Arrays.copyOf(layers[layer][r], gridSize);
        }
        return copy;
    }

    public void setLayer(int layer, Color[][] data) {
        if (layer < 0 || layer >= layerCount || data == null)
            return;
        for (int r = 0; r < Math.min(gridSize, data.length); r++) {
            if (data[r] != null) {
                layers[layer][r] = Arrays.copyOf(data[r], gridSize);
            }
        }
    }

    public void swapLayers(int a, int b) {
        if (a < 0 || b < 0 || a >= layerCount || b >= layerCount || a == b)
            return;
        Color[][] tmp = layers[a];
        layers[a] = layers[b];
        layers[b] = tmp;
        boolean tmpVis = layerVisible[a];
        layerVisible[a] = layerVisible[b];
        layerVisible[b] = tmpVis;
        String tmpName = layerNames[a];
        layerNames[a] = layerNames[b];
        layerNames[b] = tmpName;
        List<FrameData> tmpFrames = layerFrames[a];
        layerFrames[a] = layerFrames[b];
        layerFrames[b] = tmpFrames;
        int tmpIdx = currentFrameIndex[a];
        currentFrameIndex[a] = currentFrameIndex[b];
        currentFrameIndex[b] = tmpIdx;
        boolean tmpAnim = animatedLayers[a];
        animatedLayers[a] = animatedLayers[b];
        animatedLayers[b] = tmpAnim;
    }

    public void ensureLayerNamesSize(int count) {
        if (layerNames.length == count)
            return;
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

    public void initLayerFrames(int count) {
        if (count <= 0)
            count = 1;
        layerFrames = new ArrayList[count];
        currentFrameIndex = new int[count];
        animatedLayers = new boolean[count];
        for (int i = 0; i < count; i++) {
            layerFrames[i] = new ArrayList<>();
            layerFrames[i].add(new FrameData(new Color[gridSize][gridSize]));
            currentFrameIndex[i] = 0;
            animatedLayers[i] = true;
        }
    }

    public void addFrameFromCurrent() {
        List<FrameData> frames = layerFrames[activeLayer];
        FrameData data = new FrameData(getLayerCopy(activeLayer));
        frames.add(data);
        currentFrameIndex[activeLayer] = frames.size() - 1;
    }

    public void addBlankFrame() {
        List<FrameData> frames = layerFrames[activeLayer];
        int insertAt = Math.min(frames.size(), currentFrameIndex[activeLayer] + 1);
        FrameData data = new FrameData(new Color[gridSize][gridSize]);
        frames.add(insertAt, data);
        currentFrameIndex[activeLayer] = insertAt;
    }

    public void duplicateCurrentFrame() {
        List<FrameData> frames = layerFrames[activeLayer];
        if (frames.isEmpty()) {
            addBlankFrame();
            return;
        }
        FrameData snapshot = new FrameData(getLayerCopy(activeLayer));
        int insertAt = Math.min(frames.size(), currentFrameIndex[activeLayer] + 1);
        frames.add(insertAt, snapshot);
        currentFrameIndex[activeLayer] = insertAt;
    }

    public void deleteCurrentFrame() {
        List<FrameData> frames = layerFrames[activeLayer];
        if (frames.isEmpty())
            return;
        frames.remove(currentFrameIndex[activeLayer]);
        if (frames.isEmpty()) {
            frames.add(new FrameData(new Color[gridSize][gridSize]));
            currentFrameIndex[activeLayer] = 0;
        } else {
            currentFrameIndex[activeLayer] = Math.max(0, Math.min(currentFrameIndex[activeLayer], frames.size() - 1));
        }
    }

    public void selectFrame(int index) {
        List<FrameData> frames = layerFrames[activeLayer];
        if (index < 0 || index >= frames.size())
            return;
        currentFrameIndex[activeLayer] = index;
        playCursor = index;
    }

    public void stepFrame(int delta) {
        List<FrameData> frames = layerFrames[activeLayer];
        if (frames == null || frames.isEmpty())
            return;
        int size = frames.size();
        int next = (currentFrameIndex[activeLayer] + delta) % size;
        if (next < 0)
            next += size;
        currentFrameIndex[activeLayer] = next;
        playCursor = next;
    }

    public void applyAllCurrentFrames() {
        for (int l = 0; l < layerCount; l++) {
            List<FrameData> frames = layerFrames[l];
            if (frames.isEmpty())
                continue;
            int idx = Math.max(0, Math.min(currentFrameIndex[l], frames.size() - 1));
            FrameData fd = frames.get(idx);
            setLayer(l, fd.layer);
        }
    }

    public void saveCurrentFrames() {
        for (int l = 0; l < layerCount; l++) {
            List<FrameData> frames = layerFrames[l];
            if (frames.isEmpty())
                continue;
            int idx = Math.max(0, Math.min(currentFrameIndex[l], frames.size() - 1));
            frames.set(idx, new FrameData(getLayerCopy(l)));
        }
    }

    public ProjectData toProjectData() {
        ProjectData data = new ProjectData();
        data.cols = gridSize;
        data.rows = gridSize;
        data.cellSize = canvasCellSize;
        data.layerNames = layerNames.clone();
        data.layerVisible = layerVisible.clone();
        data.animatedLayers = animatedLayers.clone();
        data.currentFrameIndex = currentFrameIndex.clone();
        data.activeLayer = activeLayer;
        data.brushSize = brushSize;
        data.red = colorState.getRed();
        data.green = colorState.getGreen();
        data.blue = colorState.getBlue();
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
        return data;
    }

    public void fromProjectData(ProjectData data) {
        gridSize = Math.max(data.cols, data.rows);
        canvasCellSize = Math.min(256, Math.max(2, data.cellSize));
        layerCount = data.layerFrames.size();
        initLayers();
        ensureLayerNamesSize(data.layerNames.length);
        System.arraycopy(data.layerNames, 0, layerNames, 0, Math.min(layerNames.length, data.layerNames.length));
        System.arraycopy(data.layerVisible, 0, layerVisible, 0,
                Math.min(layerVisible.length, data.layerVisible.length));
        animatedLayers = Arrays.copyOf(data.animatedLayers, layerCount);
        currentFrameIndex = Arrays.copyOf(data.currentFrameIndex, layerCount);
        brushSize = data.brushSize;
        colorState.setFromColor(new Color(data.red, data.green, data.blue));
        frameRate = data.frameRate;
        activeLayer = Math.max(0, Math.min(data.activeLayer, layerCount - 1));
        initLayerFrames(layerCount);
        for (int l = 0; l < layerCount; l++) {
            List<Color[][]> saved = data.layerFrames.get(l);
            List<FrameData> dest = layerFrames[l];
            dest.clear();
            for (Color[][] layer : saved) {
                dest.add(new FrameData(cloneLayer(layer)));
            }
            if (dest.isEmpty()) {
                dest.add(new FrameData(new Color[gridSize][gridSize]));
            }
        }
        applyAllCurrentFrames();
    }

    private Color[][] cloneLayer(Color[][] src) {
        if (src == null)
            return null;
        Color[][] copy = new Color[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = Arrays.copyOf(src[r], src[r].length);
        }
        return copy;
    }

    private int computeCellSizeForGrid(int grid) {
        int computed = 640 / grid;
        return Math.max(2, Math.min(256, computed));
    }

    public static class FrameData implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Color[][] layer;

        public FrameData(Color[][] layer) {
            this.layer = layer;
        }
    }
}
