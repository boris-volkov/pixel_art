import java.awt.Color;

/**
 * Encapsulates brush color state with synchronized RGB and HSB representations.
 * Keeps hue stable even when saturation reaches zero.
 */
class ColorState {
    private int red = 32;
    private int green = 32;
    private int blue = 32;
    private int hueDegrees = 0;
    private int satPercent = 0;
    private int briPercent = 0;

    Color getColor() {
        return new Color(red, green, blue);
    }

    void setFromColor(Color c) {
        if (c == null) return;
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        if (hsb[1] > 1e-4f) {
            hueDegrees = normalizeHue(Math.round(hsb[0] * 360f));
        }
        satPercent = clampPercent(Math.round(hsb[1] * 100f));
        briPercent = clampPercent(Math.round(hsb[2] * 100f));
        red = clamp8(c.getRed());
        green = clamp8(c.getGreen());
        blue = clamp8(c.getBlue());
    }

    int getRed() { return red; }
    int getGreen() { return green; }
    int getBlue() { return blue; }
    int getHue() { return normalizeHue(hueDegrees); }
    int getSaturation() { return clampPercent(satPercent); }
    int getBrightness() { return clampPercent(briPercent); }

    void setRed(int v) { red = clamp8(v); syncHSBFromRGB(); }
    void setGreen(int v) { green = clamp8(v); syncHSBFromRGB(); }
    void setBlue(int v) { blue = clamp8(v); syncHSBFromRGB(); }
    void setHue(int deg) { hueDegrees = normalizeHue(deg); applyHSB(); }
    void setSaturation(int pct) { satPercent = clampPercent(pct); applyHSB(); }
    void setBrightness(int pct) { briPercent = clampPercent(pct); applyHSB(); }

    private void applyHSB() {
        float h = normalizeHue(hueDegrees) / 360f;
        float s = clampPercent(satPercent) / 100f;
        float b = clampPercent(briPercent) / 100f;
        Color c = Color.getHSBColor(h, s, b);
        red = clamp8(c.getRed());
        green = clamp8(c.getGreen());
        blue = clamp8(c.getBlue());
    }

    private void syncHSBFromRGB() {
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        hueDegrees = normalizeHue(Math.round(hsb[0] * 360f));
        satPercent = clampPercent(Math.round(hsb[1] * 100f));
        briPercent = clampPercent(Math.round(hsb[2] * 100f));
    }

    private int normalizeHue(int deg) {
        int h = deg % 360;
        return h < 0 ? h + 360 : h;
    }

    private int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
