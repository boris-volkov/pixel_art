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

    private void enablePainting() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (panBlocker != null && panBlocker.getAsBoolean()) {
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
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return;
        }
        if (!strokeActive) {
            pushUndo();
            strokeActive = true;
        }
        applyBrush(column, row);
        updateHover(x, y);
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
        int startCol = column - stampCols / 2;
        int startRow = row - stampRows / 2;
        int endCol = Math.min(columns - 1, startCol + stampCols - 1);
        int endRow = Math.min(rows - 1, startRow + stampRows - 1);

        if (startCol < 0) startCol = 0;
        if (startRow < 0) startRow = 0;

        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                Color s = stamp[r - startRow][c - startCol];
                if (s != null) {
                    pixels[r][c] = s;
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
                    int startCol = hoverCol - stampCols / 2;
                    int startRow = hoverRow - stampRows / 2;
                    int endCol = Math.min(columns - 1, startCol + stampCols - 1);
                    int endRow = Math.min(rows - 1, startRow + stampRows - 1);
                    if (startCol < 0) startCol = 0;
                    if (startRow < 0) startRow = 0;

                    for (int r = startRow; r <= endRow; r++) {
                        for (int c = startCol; c <= endCol; c++) {
                            Color s = stamp[r - startRow][c - startCol];
                            if (s != null) {
                                g2.setColor(new Color(s.getRed(), s.getGreen(), s.getBlue(), 120));
                                g2.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
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
}
