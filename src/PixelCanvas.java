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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

class PixelCanvas extends javax.swing.JPanel {
    private static final int DITHER_LEVELS = 4;
    private final int columns;
    private final int rows;
    private int cellSize;
    private final Color[][] pixels;
    private final Deque<Color[][]> undoStack = new ArrayDeque<>();
    private final int undoLimit = 30;
    private final java.util.function.Consumer<Color> pickCallback;
    private final IntConsumer brushChangeCallback;
    private final Supplier<PixelArtApp.ToolMode> modeSupplier;
    private final Supplier<Color[][]> stampSupplier;
    private final java.util.function.BooleanSupplier panBlocker;
    private Color currentColor = Color.BLACK;
    private int brushSize = 1;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private boolean strokeActive = false;
    private boolean constrainStroke = false;
    private int anchorCol = -1;
    private int anchorRow = -1;

    PixelCanvas(int columns, int rows, int cellSize, java.util.function.Consumer<Color> pickCallback,
                IntConsumer brushChangeCallback, Supplier<PixelArtApp.ToolMode> modeSupplier,
                Supplier<Color[][]> stampSupplier, java.util.function.BooleanSupplier panBlocker) {
        this.columns = columns;
        this.rows = rows;
        this.cellSize = cellSize;
        this.pixels = new Color[rows][columns];
        this.pickCallback = pickCallback;
        this.brushChangeCallback = brushChangeCallback;
        this.modeSupplier = modeSupplier;
        this.stampSupplier = stampSupplier;
        this.panBlocker = panBlocker;
        setPreferredSize(new Dimension(columns * cellSize, rows * cellSize));
        setBackground(PixelArtApp.CANVAS_BG);
        enablePainting();
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
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                pixels[r][c] = null;
            }
        }
        repaint();
    }

    void fill(Color color) {
        pushUndo();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                pixels[r][c] = color;
            }
        }
        repaint();
    }

    void adjustAll(UnaryOperator<Color> adjuster) {
        pushUndo();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                Color color = pixels[r][c];
                if (color != null) {
                    pixels[r][c] = adjuster.apply(color);
                }
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
                        Color c = pixels[rr][cc];
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
            System.arraycopy(next[row], 0, pixels[row], 0, columns);
        }
        repaint();
    }

    void flipHorizontal() {
        pushUndo();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns / 2; c++) {
                int mirror = columns - 1 - c;
                Color tmp = pixels[r][c];
                pixels[r][c] = pixels[r][mirror];
                pixels[r][mirror] = tmp;
            }
        }
        repaint();
    }

    void flipVertical() {
        pushUndo();
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows / 2; r++) {
                int mirror = rows - 1 - r;
                Color tmp = pixels[r][c];
                pixels[r][c] = pixels[mirror][c];
                pixels[mirror][c] = tmp;
            }
        }
        repaint();
    }

    void ditherFloydSteinberg() {
        pushUndo();
        double[][] errR = new double[rows][columns];
        double[][] errG = new double[rows][columns];
        double[][] errB = new double[rows][columns];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Color src = pixels[y][x];
                if (src == null) src = PixelArtApp.CANVAS_BG;
                double r = clampDouble(src.getRed() + errR[y][x]);
                double g = clampDouble(src.getGreen() + errG[y][x]);
                double b = clampDouble(src.getBlue() + errB[y][x]);
                int qr = quantizeChannel(r);
                int qg = quantizeChannel(g);
                int qb = quantizeChannel(b);
                pixels[y][x] = new Color(qr, qg, qb);

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
        // Floyd–Steinberg weights
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
        int step = 255 / (DITHER_LEVELS - 1);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Color src = pixels[y][x];
                if (src == null) src = PixelArtApp.CANVAS_BG;
                int threshold = bayer4[y & 3][x & 3]; // 0..15
                double t = (threshold + 0.5) / 16.0;   // 0..1
                pixels[y][x] = new Color(
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
                constrainStroke = e.isShiftDown();
                anchorCol = e.getX() / cellSize;
                anchorRow = e.getY() / cellSize;
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
                paintAt(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                endStroke();
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
        if (mode == PixelArtApp.ToolMode.STAMP && stampSupplier != null) {
            applyStamp(column, row);
            return;
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

        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                pixels[r][c] = currentColor;
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
                        pixels[rr][cc] = s;
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
        Color color = pixels[row][column];
        if (color == null) {
            color = PixelArtApp.CANVAS_BG;
        }
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
                Color color = pixels[r][c];
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

        g2.dispose();
    }

    BufferedImage toImage() {
        BufferedImage img = new BufferedImage(columns, rows, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                Color color = pixels[r][c];
                if (color == null) {
                    color = PixelArtApp.CANVAS_BG;
                }
                img.setRGB(c, r, color.getRGB());
            }
        }
        return img;
    }

    Color[][] getPixelsCopy() {
        Color[][] copy = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            copy[r] = Arrays.copyOf(pixels[r], columns);
        }
        return copy;
    }

    void setPixelDirect(int row, int col, Color color) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return;
        pixels[row][col] = color;
    }

    int getRows() { return rows; }
    int getColumns() { return columns; }

    void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Color[][] prev = undoStack.pop();
        for (int r = 0; r < rows; r++) {
            System.arraycopy(prev[r], 0, pixels[r], 0, columns);
        }
        repaint();
    }

    private void pushUndo() {
        Color[][] snapshot = new Color[rows][columns];
        for (int r = 0; r < rows; r++) {
            snapshot[r] = Arrays.copyOf(pixels[r], columns);
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
}
