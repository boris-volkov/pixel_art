import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

class PixelCanvas extends javax.swing.JPanel {
    private static final int DITHER_LEVELS = 4;
    private final int columns;
    private final int rows;
    private final int layerCount;
    private int cellSize;
    private final Color[][][] layers;
    private final Deque<Color[][][]> undoStack = new ArrayDeque<>();
    private final int undoLimit = 30;
    private final java.util.function.Consumer<Color> pickCallback;
    private final IntConsumer brushChangeCallback;
    private final Supplier<PixelArtApp.ToolMode> modeSupplier;
    private final Supplier<Color[][]> stampSupplier;
    private final Supplier<Color[][][]> onionSupplier;
    private final IntSupplier activeLayerSupplier;
    private final IntPredicate layerVisiblePredicate;
    private final java.util.function.BooleanSupplier panBlocker;
    private Color currentColor = Color.BLACK;
    private int brushSize = 1;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private boolean strokeActive = false;
    private boolean constrainStroke = false;
    private int anchorCol = -1;
    private int anchorRow = -1;
    private Color[][] moveSnapshot = null;
    private int moveStartCol = 0;
    private int moveStartRow = 0;

    PixelCanvas(int columns, int rows, int cellSize, java.util.function.Consumer<Color> pickCallback,
                IntConsumer brushChangeCallback, Supplier<PixelArtApp.ToolMode> modeSupplier,
                Supplier<Color[][]> stampSupplier, Supplier<Color[][][]> onionSupplier,
                IntSupplier activeLayerSupplier, int layerCount,
                IntPredicate layerVisiblePredicate, java.util.function.BooleanSupplier panBlocker) {
        this.columns = columns;
        this.rows = rows;
        this.cellSize = cellSize;
        this.layerCount = Math.max(1, layerCount);
        this.layers = new Color[this.layerCount][rows][columns];
        this.pickCallback = pickCallback;
        this.brushChangeCallback = brushChangeCallback;
        this.modeSupplier = modeSupplier;
        this.stampSupplier = stampSupplier;
        this.onionSupplier = onionSupplier;
        this.activeLayerSupplier = activeLayerSupplier;
        this.layerVisiblePredicate = layerVisiblePredicate != null ? layerVisiblePredicate : (l -> true);
        this.panBlocker = panBlocker;
        setPreferredSize(new Dimension(columns * cellSize, rows * cellSize));
        setBackground(PixelArtApp.CANVAS_BG);
        setFocusable(true);
        enablePainting();
    }

    private int activeLayer() {
        return activeLayerSupplier != null ? Math.max(0, Math.min(layerCount - 1, activeLayerSupplier.getAsInt())) : 0;
    }

    void setCurrentColor(Color color) {
        this.currentColor = color;
    }

    Color getCurrentColor() {
        return currentColor;
    }

    int getBrushSize() {
        return brushSize;
    }

    void setBrushSize(int size) {
        int newSize = Math.max(1, size);
        if (newSize != this.brushSize) {
            this.brushSize = newSize;
            if (brushChangeCallback != null) brushChangeCallback.accept(this.brushSize);
            if (hoverCol >= 0 && hoverRow >= 0) {
                repaint();
            }
        }
    }

    int getCellSize() {
        return cellSize;
    }

    void setCellSize(int size) {
        int newSize = Math.max(1, size);
        if (newSize != this.cellSize) {
            this.cellSize = newSize;
            setPreferredSize(new Dimension(columns * cellSize, rows * cellSize));
            revalidate();
            repaint();
        }
    }

    void clear() {
        pushUndo();
        int layer = activeLayer();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                layers[layer][r][c] = null;
            }
        }
        repaint();
    }

    void fill(Color color) {
        pushUndo();
        int layer = activeLayer();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                layers[layer][r][c] = color;
            }
        }
        repaint();
    }

    void adjustAll(UnaryOperator<Color> adjuster) {
        pushUndo();
        int layer = activeLayer();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                Color color = layers[layer][r][c];
                if (color != null) {
                    layers[layer][r][c] = adjuster.apply(color);
                }
            }
        }
        repaint();
    }

    void flipHorizontal() {
        pushUndo();
        int layer = activeLayer();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns / 2; c++) {
                int mirror = columns - 1 - c;
                Color tmp = layers[layer][r][c];
                layers[layer][r][c] = layers[layer][r][mirror];
                layers[layer][r][mirror] = tmp;
            }
        }
        repaint();
    }

    void flipVertical() {
        pushUndo();
        int layer = activeLayer();
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows / 2; r++) {
                int mirror = rows - 1 - r;
                Color tmp = layers[layer][r][c];
                layers[layer][r][c] = layers[layer][mirror][c];
                layers[layer][mirror][c] = tmp;
            }
        }
        repaint();
    }

    void blurGaussian(int radius) {
        int r = Math.max(1, radius);
        pushUndo();
        int size = r * 2 + 1;
        double sigma = r / 2.0;
        double twoSigmaSq = 2 * sigma * sigma;
        double[][] kernel = new double[size][size];
        double sum = 0;
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                double weight = Math.exp(-(x * x + y * y) / twoSigmaSq);
                kernel[y + r][x + r] = weight;
                sum += weight;
            }
        }
        int layer = activeLayer();
        Color[][] next = new Color[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double accR = 0, accG = 0, accB = 0, weightSum = 0;
                for (int ky = -r; ky <= r; ky++) {
                    int rr = row + ky;
                    if (rr < 0 || rr >= rows) continue;
                    for (int kx = -r; kx <= r; kx++) {
                        int cc = col + kx;
                        if (cc < 0 || cc >= columns) continue;
                        double w = kernel[ky + r][kx + r];
                        Color c = layers[layer][rr][cc];
                        if (c == null) c = PixelArtApp.CANVAS_BG;
                        accR += c.getRed() * w;
                        accG += c.getGreen() * w;
                        accB += c.getBlue() * w;
                        weightSum += w;
                    }
                }
                if (weightSum > 0) {
                    int nr = PixelArtApp.clamp((int) Math.round(accR / weightSum));
                    int ng = PixelArtApp.clamp((int) Math.round(accG / weightSum));
                    int nb = PixelArtApp.clamp((int) Math.round(accB / weightSum));
                    next[row][col] = new Color(nr, ng, nb);
                }
            }
        }
        for (int row = 0; row < rows; row++) {
            System.arraycopy(next[row], 0, layers[layer][row], 0, columns);
        }
        repaint();
    }

    void ditherFloydSteinberg() {
        pushUndo();
        int layer = activeLayer();
        double[][] errR = new double[rows][columns];
        double[][] errG = new double[rows][columns];
        double[][] errB = new double[rows][columns];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Color src = layers[layer][y][x];
                if (src == null) src = PixelArtApp.CANVAS_BG;
                double r = clampDouble(src.getRed() + errR[y][x]);
                double g = clampDouble(src.getGreen() + errG[y][x]);
                double b = clampDouble(src.getBlue() + errB[y][x]);
                int qr = quantizeChannel(r);
                int qg = quantizeChannel(g);
                int qb = quantizeChannel(b);
                layers[layer][y][x] = new Color(qr, qg, qb);

                double dr = r - qr;
                double dg = g - qg;
                double db = b - qb;
                diffuse(errR, y, x, dr);
                diffuse(errG, y, x, dg);
                diffuse(errB, y, x, db);
            }
        }
        repaint();
    }

    private void diffuse(double[][] grid, int y, int x, double error) {
        if (x + 1 < columns) grid[y][x + 1] += error * 7 / 16.0;
        if (y + 1 < rows) {
            if (x > 0) grid[y + 1][x - 1] += error * 3 / 16.0;
            grid[y + 1][x] += error * 5 / 16.0;
            if (x + 1 < columns) grid[y + 1][x + 1] += error * 1 / 16.0;
        }
    }

    private double clampDouble(double v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    void ditherOrdered() {
        pushUndo();
        int[][] bayer4 = {
                {0, 8, 2, 10},
                {12, 4, 14, 6},
                {3, 11, 1, 9},
                {15, 7, 13, 5}
        };
        int layer = activeLayer();
        int step = 255 / (DITHER_LEVELS - 1);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Color src = layers[layer][y][x];
                if (src == null) src = PixelArtApp.CANVAS_BG;
                int threshold = bayer4[y & 3][x & 3];
                double t = (threshold + 0.5) / 16.0;
                layers[layer][y][x] = new Color(
                        orderedChannel(src.getRed(), step, t),
                        orderedChannel(src.getGreen(), step, t),
                        orderedChannel(src.getBlue(), step, t)
                );
            }
        }
        repaint();
    }

    private int quantizeChannel(double value) {
        double step = 255.0 / (DITHER_LEVELS - 1);
        int idx = (int) Math.round(value / step);
        idx = Math.max(0, Math.min(DITHER_LEVELS - 1, idx));
        return (int) Math.round(idx * step);
    }

    private int orderedChannel(int value, int step, double threshold) {
        int baseIdx = value / step;
        int base = baseIdx * step;
        int next = Math.min(255, base + step);
        double frac = (value - base) / (double) step;
        return frac > threshold ? next : base;
    }

    private void enablePainting() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (panBlocker != null && panBlocker.getAsBoolean()) {
                    return;
                }
                requestFocusInWindow();
                constrainStroke = e.isShiftDown();
                anchorCol = e.getX() / cellSize;
                anchorRow = e.getY() / cellSize;
                if (modeSupplier != null && modeSupplier.get() == PixelArtApp.ToolMode.MOVE) {
                    beginMove(anchorCol, anchorRow);
                    return;
                }
                if (e.isAltDown()) {
                    pickColor(e.getX(), e.getY());
                } else {
                    beginStroke();
                    paintAt(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panBlocker != null && panBlocker.getAsBoolean()) {
                    return;
                }
                if (modeSupplier != null && modeSupplier.get() == PixelArtApp.ToolMode.MOVE) {
                    applyMove(e.getX() / cellSize, e.getY() / cellSize);
                    return;
                }
                paintAt(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                endStroke();
                endMove();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverCol = -1;
                hoverRow = -1;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int rotation = e.getWheelRotation();
                int delta = rotation > 0 ? -1 : 1;
                setBrushSize(brushSize + delta);
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    private void paintAt(int x, int y) {
        int column = x / cellSize;
        int row = y / cellSize;
        if (constrainStroke && anchorCol >= 0 && anchorRow >= 0) {
            int dx = column - anchorCol;
            int dy = row - anchorRow;
            if (Math.abs(dx) >= Math.abs(dy)) {
                row = anchorRow;
            } else {
                column = anchorCol;
            }
        }
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return;
        }
        if (!strokeActive) {
            pushUndo();
            strokeActive = true;
        }
        applyBrush(column, row);
        updateHover(column * cellSize, row * cellSize);
    }

    private void applyBrush(int column, int row) {
        PixelArtApp.ToolMode mode = modeSupplier != null ? modeSupplier.get() : PixelArtApp.ToolMode.BRUSH;
        switch (mode) {
            case STAMP:
                if (stampSupplier != null) applyStamp(column, row);
                return;
            case FILL:
                floodFill(column, row);
                return;
            case BLUR:
                blurAt(column, row);
                return;
            default:
                break;
        }

        int half = brushSize / 2;
        int startCol = column - half;
        int startRow = row - half;
        int endCol = startCol + brushSize - 1;
        int endRow = startRow + brushSize - 1;

        startCol = Math.max(0, startCol);
        startRow = Math.max(0, startRow);
        endCol = Math.min(columns - 1, endCol);
        endRow = Math.min(rows - 1, endRow);

        int layer = activeLayer();
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                layers[layer][r][c] = currentColor;
            }
        }

        int x = startCol * cellSize;
        int y = startRow * cellSize;
        int w = (endCol - startCol + 1) * cellSize;
        int h = (endRow - startRow + 1) * cellSize;
        repaint(new Rectangle(x, y, w, h));
    }

    private void applyStamp(int column, int row) {
        Color[][] stamp = stampSupplier.get();
        if (stamp == null) {
            return;
        }
        int stampRows = stamp.length;
        int stampCols = stamp[0].length;
        int scale = computeStampScale(stampCols, stampRows);
        int stampWidth = stampCols * scale;
        int stampHeight = stampRows * scale;

        int startCol = column - stampWidth / 2;
        int startRow = row - stampHeight / 2;
        int endCol = Math.min(columns - 1, startCol + stampWidth - 1);
        int endRow = Math.min(rows - 1, startRow + stampHeight - 1);

        if (startCol < 0) startCol = 0;
        if (startRow < 0) startRow = 0;

        int layer = activeLayer();
        for (int sr = 0; sr < stampRows; sr++) {
            for (int sc = 0; sc < stampCols; sc++) {
                Color s = stamp[sr][sc];
                if (s == null) continue;
                int destCol = startCol + sc * scale;
                int destRow = startRow + sr * scale;
                for (int r = 0; r < scale; r++) {
                    int rr = destRow + r;
                    if (rr > endRow) break;
                    for (int c = 0; c < scale; c++) {
                        int cc = destCol + c;
                        if (cc > endCol) break;
                        layers[layer][rr][cc] = s;
                    }
                }
            }
        }
        int x = startCol * cellSize;
        int y = startRow * cellSize;
        int w = (endCol - startCol + 1) * cellSize;
        int h = (endRow - startRow + 1) * cellSize;
        repaint(new Rectangle(x, y, w, h));
    }

    private void updateHover(int x, int y) {
        int column = x / cellSize;
        int row = y / cellSize;
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            if (hoverCol != -1 || hoverRow != -1) {
                hoverCol = -1;
                hoverRow = -1;
                repaint();
            }
            return;
        }
        if (column != hoverCol || row != hoverRow) {
            hoverCol = column;
            hoverRow = row;
            repaint();
        }
    }

    private void pickColor(int x, int y) {
        int column = x / cellSize;
        int row = y / cellSize;
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return;
        }
        Color color = compositeAt(row, column);
        if (pickCallback != null) {
            pickCallback.accept(color);
        }
    }

    private void beginStroke() {
        if (!strokeActive) {
            pushUndo();
            strokeActive = true;
        }
    }

    private void endStroke() {
        strokeActive = false;
        constrainStroke = false;
        anchorCol = -1;
        anchorRow = -1;
    }

    private void beginMove(int col, int row) {
        pushUndo();
        int layer = activeLayer();
        moveSnapshot = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            moveSnapshot[r] = Arrays.copyOf(layers[layer][r], columns);
        }
        moveStartCol = col;
        moveStartRow = row;
    }

    private void applyMove(int col, int row) {
        if (moveSnapshot == null) return;
        int dx = col - moveStartCol;
        int dy = row - moveStartRow;
        int layer = activeLayer();
        for (int r = 0; r < rows; r++) {
            Arrays.fill(layers[layer][r], null);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                Color src = moveSnapshot[r][c];
                if (src == null) continue;
                int nr = r + dy;
                int nc = c + dx;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < columns) {
                    layers[layer][nr][nc] = src;
                }
            }
        }
        repaint();
    }

    private void endMove() {
        moveSnapshot = null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                int x = c * cellSize;
                int y = r * cellSize;
                Color color = compositeAt(r, c);
                if (color != null) {
                    g2.setColor(color);
                    g2.fillRect(x, y, cellSize, cellSize);
                }
            }
        }

        if (hoverCol >= 0 && hoverRow >= 0) {
            if (isStampMode()) {
                Color[][] stamp = stampSupplier.get();
                if (stamp != null) {
                    int stampRows = stamp.length;
                    int stampCols = stamp[0].length;
                    int scale = computeStampScale(stampCols, stampRows);
                    int stampWidth = stampCols * scale;
                    int stampHeight = stampRows * scale;
                    int startCol = hoverCol - stampWidth / 2;
                    int startRow = hoverRow - stampHeight / 2;
                    int endCol = Math.min(columns - 1, startCol + stampWidth - 1);
                    int endRow = Math.min(rows - 1, startRow + stampHeight - 1);
                    if (startCol < 0) startCol = 0;
                    if (startRow < 0) startRow = 0;

                    for (int sr = 0; sr < stampRows; sr++) {
                        for (int sc = 0; sc < stampCols; sc++) {
                            Color s = stamp[sr][sc];
                            if (s == null) continue;
                            int destCol = startCol + sc * scale;
                            int destRow = startRow + sr * scale;
                            for (int r = 0; r < scale; r++) {
                                int rr = destRow + r;
                                if (rr > endRow) break;
                                for (int c = 0; c < scale; c++) {
                                    int cc = destCol + c;
                                    if (cc > endCol) break;
                                    g2.setColor(new Color(s.getRed(), s.getGreen(), s.getBlue(), 120));
                                    g2.fillRect(cc * cellSize, rr * cellSize, cellSize, cellSize);
                                }
                            }
                        }
                    }
                    int x = startCol * cellSize;
                    int y = startRow * cellSize;
                    int w = (endCol - startCol + 1) * cellSize;
                    int h = (endRow - startRow + 1) * cellSize;
                    g2.setColor(new Color(PixelArtApp.ACCENT.getRed(), PixelArtApp.ACCENT.getGreen(), PixelArtApp.ACCENT.getBlue(), 180));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x, y, w - 1, h - 1);
                }
            } else {
                int half = brushSize / 2;
                int startCol = Math.max(0, hoverCol - half);
                int startRow = Math.max(0, hoverRow - half);
                int endCol = Math.min(columns - 1, startCol + brushSize - 1);
                int endRow = Math.min(rows - 1, startRow + brushSize - 1);

                int x = startCol * cellSize;
                int y = startRow * cellSize;
                int w = (endCol - startCol + 1) * cellSize;
                int h = (endRow - startRow + 1) * cellSize;

                g2.setColor(new Color(PixelArtApp.ACCENT.getRed(), PixelArtApp.ACCENT.getGreen(), PixelArtApp.ACCENT.getBlue(), 140));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x, y, w - 1, h - 1);
            }
        }

        // Onion skin overlay above current layers so it is visible
        if (onionSupplier != null) {
            Color[][][] onions = onionSupplier.get();
            if (onions != null) {
                int alpha1 = 160;
                int alpha2 = 100;
                for (int idx = 0; idx < onions.length; idx++) {
                    Color[][] onion = onions[idx];
                    if (onion == null) continue;
                    int useAlpha = (idx == 0) ? alpha1 : alpha2;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < columns; c++) {
                            Color oc = onion[r][c];
                            if (oc != null) {
                                g2.setColor(new Color(oc.getRed(), oc.getGreen(), oc.getBlue(), useAlpha));
                                g2.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
                            }
                        }
                    }
                }
            }
        }

        g2.dispose();
    }

    private Color compositeAt(int row, int col) {
        for (int l = layerCount - 1; l >= 0; l--) {
            if (layerVisiblePredicate.test(l)) {
                Color c = layers[l][row][col];
                if (c != null) return c;
            }
        }
        return PixelArtApp.CANVAS_BG;
    }

    BufferedImage toImage() {
        BufferedImage img = new BufferedImage(columns, rows, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                Color color = compositeAt(r, c);
                img.setRGB(c, r, color.getRGB());
            }
        }
        return img;
    }

    Color[][] getPixelsCopy() {
        return getLayerCopy(activeLayer());
    }

    Color[][] getLayerCopy(int layer) {
        int idx = Math.max(0, Math.min(layerCount - 1, layer));
        Color[][] copy = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            copy[r] = Arrays.copyOf(layers[idx][r], columns);
        }
        return copy;
    }

    Color[][][] getLayersCopy() {
        Color[][][] copy = new Color[layerCount][rows][columns];
        for (int l = 0; l < layerCount; l++) {
            for (int r = 0; r < rows; r++) {
                copy[l][r] = Arrays.copyOf(layers[l][r], columns);
            }
        }
        return copy;
    }

    void setLayers(Color[][][] data) {
        if (data == null || data.length != layerCount) return;
        for (int l = 0; l < layerCount; l++) {
            if (data[l].length != rows) continue;
            for (int r = 0; r < rows; r++) {
                if (data[l][r].length != columns) continue;
                layers[l][r] = Arrays.copyOf(data[l][r], columns);
            }
        }
        repaint();
    }

    void setPixelDirect(int row, int col, Color color) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return;
        layers[0][row][col] = color;
    }

    void setLayer(int layer, Color[][] data) {
        if (data == null) return;
        int idx = Math.max(0, Math.min(layerCount - 1, layer));
        if (data.length != rows) return;
        for (int r = 0; r < rows; r++) {
            if (data[r].length != columns) return;
        }
        for (int r = 0; r < rows; r++) {
            layers[idx][r] = Arrays.copyOf(data[r], columns);
        }
        repaint();
    }

    int getRows() { return rows; }
    int getColumns() { return columns; }
    int getLayerCount() { return layerCount; }

    void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Color[][][] prev = undoStack.pop();
        moveSnapshot = null;
        for (int l = 0; l < layerCount; l++) {
            for (int r = 0; r < rows; r++) {
                System.arraycopy(prev[l][r], 0, layers[l][r], 0, columns);
            }
        }
        repaint();
    }

    private void pushUndo() {
        Color[][][] snapshot = new Color[layerCount][rows][columns];
        for (int l = 0; l < layerCount; l++) {
            for (int r = 0; r < rows; r++) {
                snapshot[l][r] = Arrays.copyOf(layers[l][r], columns);
            }
        }
        undoStack.push(snapshot);
        while (undoStack.size() > undoLimit) {
            undoStack.removeLast();
        }
    }

    private boolean isStampMode() {
        return modeSupplier != null && modeSupplier.get() == PixelArtApp.ToolMode.STAMP;
    }

    private int computeStampScale(int stampCols, int stampRows) {
        int base = Math.max(stampCols, stampRows);
        int scale = (int) Math.round((double) brushSize / (double) base);
        return Math.max(1, scale);
    }

    private void floodFill(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) return;
        int layer = activeLayer();
        Color target = layers[layer][row][column];
        Color replacement = currentColor;
        if (sameColor(target, replacement)) return;
        boolean[][] visited = new boolean[rows][columns];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{row, column});
        visited[row][column] = true;
        while (!q.isEmpty()) {
            int[] pos = q.removeFirst();
            int r = pos[0];
            int c = pos[1];
            layers[layer][r][c] = replacement;
            if (c > 0 && !visited[r][c - 1] && sameColor(target, layers[layer][r][c - 1])) { visited[r][c - 1] = true; q.add(new int[]{r, c - 1}); }
            if (c < columns - 1 && !visited[r][c + 1] && sameColor(target, layers[layer][r][c + 1])) { visited[r][c + 1] = true; q.add(new int[]{r, c + 1}); }
            if (r > 0 && !visited[r - 1][c] && sameColor(target, layers[layer][r - 1][c])) { visited[r - 1][c] = true; q.add(new int[]{r - 1, c}); }
            if (r < rows - 1 && !visited[r + 1][c] && sameColor(target, layers[layer][r + 1][c])) { visited[r + 1][c] = true; q.add(new int[]{r + 1, c}); }
        }
        repaint();
    }

    private boolean sameColor(Color a, Color b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void blurAt(int column, int row) {
        int radius = Math.max(1, brushSize / 2);
        int startCol = Math.max(0, column - radius);
        int endCol = Math.min(columns - 1, column + radius);
        int startRow = Math.max(0, row - radius);
        int endRow = Math.min(rows - 1, row + radius);
        pushUndo();
        double sigma = Math.max(1.0, radius / 1.5);
        double twoSigmaSq = 2 * sigma * sigma;
        int layer = activeLayer();
        Color[][] snapshot = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            snapshot[r] = Arrays.copyOf(layers[layer][r], columns);
        }
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                double accR = 0, accG = 0, accB = 0, wSum = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int rr = r + dy;
                    if (rr < 0 || rr >= rows) continue;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int cc = c + dx;
                        if (cc < 0 || cc >= columns) continue;
                        if (dx * dx + dy * dy > radius * radius) continue;
                        double w = Math.exp(-(dx * dx + dy * dy) / twoSigmaSq);
                        Color src = snapshot[rr][cc];
                        if (src == null) src = PixelArtApp.CANVAS_BG;
                        accR += src.getRed() * w;
                        accG += src.getGreen() * w;
                        accB += src.getBlue() * w;
                        wSum += w;
                    }
                }
                if (wSum > 0) {
                    int nr = PixelArtApp.clamp((int) Math.round(accR / wSum));
                    int ng = PixelArtApp.clamp((int) Math.round(accG / wSum));
                    int nb = PixelArtApp.clamp((int) Math.round(accB / wSum));
                    layers[layer][r][c] = new Color(nr, ng, nb);
                }
            }
        }
        int x = startCol * cellSize;
        int y = startRow * cellSize;
        int w = (endCol - startCol + 1) * cellSize;
        int h = (endRow - startRow + 1) * cellSize;
        repaint(new Rectangle(x, y, w, h));
    }

    void blurMotion(double angleDegrees, int amount) {
        int len = Math.max(1, amount);
        pushUndo();
        double theta = Math.toRadians(angleDegrees);
        double dx = Math.cos(theta);
        double dy = -Math.sin(theta); // screen Y grows down, so invert for standard trig orientation
        int layer = activeLayer();
        Color[][] snapshot = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            snapshot[r] = Arrays.copyOf(layers[layer][r], columns);
        }
        Color[][] out = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                double accR = 0, accG = 0, accB = 0;
                int samples = 0;
                for (int i = -len; i <= len; i++) {
                    int sx = c + (int) Math.round(i * dx);
                    int sy = r + (int) Math.round(i * dy);
                    if (sx < 0 || sx >= columns || sy < 0 || sy >= rows) continue;
                    Color src = snapshot[sy][sx];
                    if (src == null) src = PixelArtApp.CANVAS_BG;
                    accR += src.getRed();
                    accG += src.getGreen();
                    accB += src.getBlue();
                    samples++;
                }
                if (samples > 0) {
                    int nr = PixelArtApp.clamp((int) Math.round(accR / samples));
                    int ng = PixelArtApp.clamp((int) Math.round(accG / samples));
                    int nb = PixelArtApp.clamp((int) Math.round(accB / samples));
                    out[r][c] = new Color(nr, ng, nb);
                }
            }
        }
        for (int r = 0; r < rows; r++) {
            System.arraycopy(out[r], 0, layers[layer][r], 0, columns);
        }
        repaint();
    }
}
