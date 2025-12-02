import java.awt.Color;
import java.util.ArrayDeque;

/**
 * Pixel-level operations that mutate a single layer in-place.
 * These are UI-agnostic helpers shared by canvas/controller.
 */
class PixelOps {
    private static final int DITHER_LEVELS = 4;
    static final class MoveState {
        final Color[][] snapshot;
        final int startCol;
        final int startRow;

        MoveState(Color[][] snapshot, int startCol, int startRow) {
            this.snapshot = snapshot;
            this.startCol = startCol;
            this.startRow = startRow;
        }
    }

    static final class RotateState {
        final Color[][] snapshot;
        final int centerCol;
        final int centerRow;
        final double startAngle;

        RotateState(Color[][] snapshot, int centerCol, int centerRow, double startAngle) {
            this.snapshot = snapshot;
            this.centerCol = centerCol;
            this.centerRow = centerRow;
            this.startAngle = startAngle;
        }
    }

    static void flipHorizontal(Color[][] layer) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols / 2; c++) {
                int mirror = cols - 1 - c;
                Color tmp = layer[r][c];
                layer[r][c] = layer[r][mirror];
                layer[r][mirror] = tmp;
            }
        }
    }

    static void flipVertical(Color[][] layer) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows / 2; r++) {
                int mirror = rows - 1 - r;
                Color tmp = layer[r][c];
                layer[r][c] = layer[mirror][c];
                layer[mirror][c] = tmp;
            }
        }
    }

    static void floodFill(Color[][] layer, int row, int col, Color replacement) {
        if (layer == null || replacement == null) return;
        int rows = layer.length;
        if (rows == 0) return;
        int cols = layer[0].length;
        if (col < 0 || col >= cols || row < 0 || row >= rows) return;
        Color target = layer[row][col];
        if (sameColor(target, replacement)) return;
        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { row, col });
        visited[row][col] = true;
        while (!q.isEmpty()) {
            int[] pos = q.removeFirst();
            int r = pos[0];
            int c = pos[1];
            layer[r][c] = replacement;
            if (c > 0 && !visited[r][c - 1] && sameColor(target, layer[r][c - 1])) {
                visited[r][c - 1] = true;
                q.add(new int[] { r, c - 1 });
            }
            if (c < cols - 1 && !visited[r][c + 1] && sameColor(target, layer[r][c + 1])) {
                visited[r][c + 1] = true;
                q.add(new int[] { r, c + 1 });
            }
            if (r > 0 && !visited[r - 1][c] && sameColor(target, layer[r - 1][c])) {
                visited[r - 1][c] = true;
                q.add(new int[] { r - 1, c });
            }
            if (r < rows - 1 && !visited[r + 1][c] && sameColor(target, layer[r + 1][c])) {
                visited[r + 1][c] = true;
                q.add(new int[] { r + 1, c });
            }
        }
    }

    static void blurGaussian(Color[][] layer, int radius) {
        if (layer == null || layer.length == 0) return;
        int r = Math.max(1, radius);
        int rows = layer.length;
        int cols = layer[0].length;
        int size = r * 2 + 1;
        double sigma = r / 2.0;
        double twoSigmaSq = 2 * sigma * sigma;
        double[][] kernel = new double[size][size];
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                double weight = Math.exp(-(x * x + y * y) / twoSigmaSq);
                kernel[y + r][x + r] = weight;
            }
        }
        Color[][] next = new Color[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double accR = 0, accG = 0, accB = 0, weightSum = 0;
                for (int ky = -r; ky <= r; ky++) {
                    int rr = row + ky;
                    if (rr < 0 || rr >= rows) continue;
                    for (int kx = -r; kx <= r; kx++) {
                        int cc = col + kx;
                        if (cc < 0 || cc >= cols) continue;
                        double w = kernel[ky + r][kx + r];
                        Color c = layer[rr][cc];
                        if (c == null) continue;
                        accR += c.getRed() * w;
                        accG += c.getGreen() * w;
                        accB += c.getBlue() * w;
                        weightSum += w;
                    }
                }
                if (weightSum > 0) {
                    int nr = PixelConstants.clamp((int) Math.round(accR / weightSum));
                    int ng = PixelConstants.clamp((int) Math.round(accG / weightSum));
                    int nb = PixelConstants.clamp((int) Math.round(accB / weightSum));
                    next[row][col] = new Color(nr, ng, nb);
                } else {
                    next[row][col] = null;
                }
            }
        }
        for (int row = 0; row < rows; row++) {
            System.arraycopy(next[row], 0, layer[row], 0, cols);
        }
    }

    static void blurMotion(Color[][] layer, double angleDegrees, int amount) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        int len = Math.max(1, amount);
        double theta = Math.toRadians(angleDegrees);
        double dx = Math.cos(theta);
        double dy = -Math.sin(theta); // screen Y grows down
        Color[][] snapshot = new Color[rows][cols];
        for (int r = 0; r < rows; r++) {
            snapshot[r] = java.util.Arrays.copyOf(layer[r], cols);
        }
        Color[][] out = new Color[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double accR = 0, accG = 0, accB = 0;
                int samples = 0;
                for (int i = -len; i <= len; i++) {
                    int sx = c + (int) Math.round(i * dx);
                    int sy = r + (int) Math.round(i * dy);
                    if (sx < 0 || sx >= cols || sy < 0 || sy >= rows) continue;
                    Color src = snapshot[sy][sx];
                    if (src == null) continue;
                    accR += src.getRed();
                    accG += src.getGreen();
                    accB += src.getBlue();
                    samples++;
                }
                if (samples > 0) {
                    int nr = PixelConstants.clamp((int) Math.round(accR / samples));
                    int ng = PixelConstants.clamp((int) Math.round(accG / samples));
                    int nb = PixelConstants.clamp((int) Math.round(accB / samples));
                    out[r][c] = new Color(nr, ng, nb);
                }
            }
        }
        for (int r = 0; r < rows; r++) {
            System.arraycopy(out[r], 0, layer[r], 0, cols);
        }
    }

    static void ditherFloydSteinberg(Color[][] layer, Color background) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        double[][] errR = new double[rows][cols];
        double[][] errG = new double[rows][cols];
        double[][] errB = new double[rows][cols];
        Color bg = background != null ? background : PixelConstants.CANVAS_BG;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Color src = layer[y][x];
                if (src == null) src = bg;
                double r = clampDouble(src.getRed() + errR[y][x]);
                double g = clampDouble(src.getGreen() + errG[y][x]);
                double b = clampDouble(src.getBlue() + errB[y][x]);
                int qr = quantizeChannel(r);
                int qg = quantizeChannel(g);
                int qb = quantizeChannel(b);
                layer[y][x] = new Color(qr, qg, qb);

                double dr = r - qr;
                double dg = g - qg;
                double db = b - qb;
                diffuse(errR, y, x, dr, rows, cols);
                diffuse(errG, y, x, dg, rows, cols);
                diffuse(errB, y, x, db, rows, cols);
            }
        }
    }

    static void ditherOrdered(Color[][] layer, Color background) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        int[][] bayer4 = {
                { 0, 8, 2, 10 },
                { 12, 4, 14, 6 },
                { 3, 11, 1, 9 },
                { 15, 7, 13, 5 }
        };
        int step = 255 / (DITHER_LEVELS - 1);
        Color bg = background != null ? background : PixelConstants.CANVAS_BG;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Color src = layer[y][x];
                if (src == null) src = bg;
                int threshold = bayer4[y & 3][x & 3];
                double t = (threshold + 0.5) / 16.0;
                int r = orderedChannel(src.getRed(), step, t);
                int g = orderedChannel(src.getGreen(), step, t);
                int b = orderedChannel(src.getBlue(), step, t);
                layer[y][x] = new Color(r, g, b);
            }
        }
    }

    private static boolean sameColor(Color a, Color b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static void diffuse(double[][] grid, int y, int x, double error, int rows, int cols) {
        if (x + 1 < cols) grid[y][x + 1] += error * 7 / 16.0;
        if (y + 1 < rows) {
            if (x > 0) grid[y + 1][x - 1] += error * 3 / 16.0;
            grid[y + 1][x] += error * 5 / 16.0;
            if (x + 1 < cols) grid[y + 1][x + 1] += error * 1 / 16.0;
        }
    }

    private static double clampDouble(double v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private static int quantizeChannel(double value) {
        double step = 255.0 / (DITHER_LEVELS - 1);
        int idx = (int) Math.round(value / step);
        idx = Math.max(0, Math.min(DITHER_LEVELS - 1, idx));
        return (int) Math.round(idx * step);
    }

    private static int orderedChannel(int value, int step, double threshold) {
        int baseIdx = value / step;
        int base = baseIdx * step;
        int next = Math.min(255, base + step);
        double frac = (value - base) / (double) step;
        return frac > threshold ? next : base;
    }

    static void blurBrush(Color[][] layer, int row, int col, int radius) {
        if (layer == null || layer.length == 0) return;
        int rows = layer.length;
        int cols = layer[0].length;
        int r = Math.max(1, radius);
        int startCol = Math.max(0, col - r);
        int endCol = Math.min(cols - 1, col + r);
        int startRow = Math.max(0, row - r);
        int endRow = Math.min(rows - 1, row + r);
        double sigma = Math.max(1.0, r / 1.5);
        double twoSigmaSq = 2 * sigma * sigma;
        Color[][] snapshot = new Color[rows][cols];
        for (int rr = 0; rr < rows; rr++) {
            snapshot[rr] = java.util.Arrays.copyOf(layer[rr], cols);
        }
        for (int rr = startRow; rr <= endRow; rr++) {
            for (int cc = startCol; cc <= endCol; cc++) {
                double accR = 0, accG = 0, accB = 0, colorWeight = 0, totalWeight = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int y = rr + dy;
                    if (y < 0 || y >= rows) continue;
                    for (int dx = -r; dx <= r; dx++) {
                        int x = cc + dx;
                        if (x < 0 || x >= cols) continue;
                        if (dx * dx + dy * dy > r * r) continue;
                        double w = Math.exp(-(dx * dx + dy * dy) / twoSigmaSq);
                        totalWeight += w;
                        Color src = snapshot[y][x];
                        if (src == null) continue;
                        double alpha = src.getAlpha() / 255.0;
                        double aw = w * alpha;
                        accR += src.getRed() * aw;
                        accG += src.getGreen() * aw;
                        accB += src.getBlue() * aw;
                        colorWeight += aw;
                    }
                }
                if (colorWeight > 0 && totalWeight > 0) {
                    int nr = PixelConstants.clamp((int) Math.round(accR / colorWeight));
                    int ng = PixelConstants.clamp((int) Math.round(accG / colorWeight));
                    int nb = PixelConstants.clamp((int) Math.round(accB / colorWeight));
                    int na = PixelConstants.clamp((int) Math.round(255 * (colorWeight / totalWeight)));
                    if (na == 0) {
                        layer[rr][cc] = null;
                    } else {
                        layer[rr][cc] = new Color(nr, ng, nb, na);
                    }
                } else {
                    layer[rr][cc] = null;
                }
            }
        }
    }

    static MoveState beginMove(Color[][] layer, int startCol, int startRow) {
        if (layer == null) return null;
        Color[][] snapshot = new Color[layer.length][];
        for (int r = 0; r < layer.length; r++) {
            snapshot[r] = java.util.Arrays.copyOf(layer[r], layer[r].length);
        }
        return new MoveState(snapshot, startCol, startRow);
    }

    static void applyMove(Color[][] layer, MoveState state, int col, int row) {
        if (layer == null || state == null || state.snapshot == null) return;
        int rows = layer.length;
        int cols = layer[0].length;
        int dx = col - state.startCol;
        int dy = row - state.startRow;
        for (int r = 0; r < rows; r++) {
            java.util.Arrays.fill(layer[r], null);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color src = state.snapshot[r][c];
                if (src == null) continue;
                int nr = r + dy;
                int nc = c + dx;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    layer[nr][nc] = src;
                }
            }
        }
    }

    static RotateState beginRotate(Color[][] layer, int centerCol, int centerRow, double startAngle) {
        if (layer == null) return null;
        Color[][] snapshot = new Color[layer.length][];
        for (int r = 0; r < layer.length; r++) {
            snapshot[r] = java.util.Arrays.copyOf(layer[r], layer[r].length);
        }
        return new RotateState(snapshot, centerCol, centerRow, startAngle);
    }

    static void applyRotate(Color[][] layer, RotateState state, int col, int row) {
        if (layer == null || state == null || state.snapshot == null) return;
        int rows = layer.length;
        int cols = layer[0].length;
        double currentAngle = Math.atan2(row - state.centerRow, col - state.centerCol);
        double delta = currentAngle - state.startAngle;
        double cos = Math.cos(-delta);
        double sin = Math.sin(-delta);
        for (int r = 0; r < rows; r++) {
            java.util.Arrays.fill(layer[r], null);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double dx = c - state.centerCol;
                double dy = r - state.centerRow;
                double srcX = cos * dx - sin * dy + state.centerCol;
                double srcY = sin * dx + cos * dy + state.centerRow;
                int sx = (int) Math.round(srcX);
                int sy = (int) Math.round(srcY);
                if (sx >= 0 && sx < cols && sy >= 0 && sy < rows) {
                    layer[r][c] = state.snapshot[sy][sx];
                } else {
                    layer[r][c] = null;
                }
            }
        }
    }
}
