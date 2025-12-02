import java.awt.Color;

public final class PixelConstants {
    private PixelConstants() {}

    public static final Color BG = new Color(96, 96, 96);
    public static final Color TEXT = new Color(226, 231, 240);
    public static final Color MUTED_TEXT = new Color(160, 170, 186);
    public static final Color ACCENT = new Color(126, 170, 220);
    public static final Color CANVAS_BG = new Color(86, 86, 86);
    public static final Color BUTTON_BG = new Color(60, 64, 70);
    public static final Color BUTTON_BORDER = new Color(110, 120, 130);
    public static final Color BUTTON_HOVER = new Color(72, 76, 86);
    public static final Color BUTTON_ACTIVE = new Color(88, 92, 104);
    public static final Color CYAN_BTN = new Color(56, 180, 220);
    public static final Color MAGENTA_BTN = new Color(200, 90, 200);
    public static final Color YELLOW_BTN = new Color(210, 200, 90);
    public static final int CONTROL_BAR_WIDTH = 260;
    public static final int COLOR_STEP = 1;
    public static final int BRIGHT_STEP = 1;
    public static final int BRUSH_BRIGHT_STEP = 1;
    public static final int TARGET_CANVAS_SIZE = 640;
    public static final int MIN_CELL_SIZE = 2;
    public static final int MAX_CELL_SIZE = 256;

    public static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static Color adjustChannel(Color color, int redDelta, int greenDelta, int blueDelta) {
        int r = clamp(color.getRed() + redDelta);
        int g = clamp(color.getGreen() + greenDelta);
        int b = clamp(color.getBlue() + blueDelta);
        return new Color(r, g, b);
    }

    public static Color adjustBrightness(Color color, int delta) {
        int r = clamp(color.getRed() + delta);
        int g = clamp(color.getGreen() + delta);
        int b = clamp(color.getBlue() + delta);
        return new Color(r, g, b);
    }
}
