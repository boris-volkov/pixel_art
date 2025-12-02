import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class TopBar extends JComponent {
    interface Host {
        Color currentBrushColor();
        void pickBrushColor(Color color);
    }

    private final Host app;
    private final Rectangle[] swatchRects = new Rectangle[9];

    TopBar(PixelArtApp app) {
        this(new PixelArtAppHost(app));
    }

    TopBar(Host app) {
        this.app = app;
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(140, 140));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Color[] palette = buildPalette();
                for (int i = 0; i < swatchRects.length; i++) {
                    Rectangle r = swatchRects[i];
                    if (r != null && r.contains(e.getPoint())) {
                        app.pickBrushColor(palette[i]);
                        repaint();
                        break;
                    }
                }
            }
        });
    }

    private static class PixelArtAppHost implements Host {
        private final PixelArtApp app;

        PixelArtAppHost(PixelArtApp app) {
            this.app = app;
        }

        @Override
        public Color currentBrushColor() {
            return app.currentBrushColor();
        }

        @Override
        public void pickBrushColor(Color color) {
            app.pickBrushColor(color);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] palette = buildPalette();
        Color base = app.currentBrushColor();

        int mainSize = 80;
        int mainX = (getWidth() - mainSize) / 2;
        int mainY = 8;

        g2.setColor(base);
        g2.fillRect(mainX, mainY, mainSize, mainSize);
        g2.setColor(PixelArtApp.BUTTON_BORDER);
        g2.drawRect(mainX, mainY, mainSize, mainSize);

        int smallSize = 26;
        int gap = 6;
        int totalWidth = smallSize * 3 + gap * 2;
        int startX = (getWidth() - totalWidth) / 2;
        int yTop = mainY + mainSize + 10;     // most saturated
        int yMid = yTop + smallSize + gap;    // base
        int yBottom = yMid + smallSize + gap; // desaturated

        for (int i = 0; i < 3; i++) {
            int x = startX + i * (smallSize + gap);

            int idxTop = i + 6;     // saturated
            int idxMid = i;         // base
            int idxBottom = i + 3;  // desaturated

            Rectangle rTop = new Rectangle(x, yTop, smallSize, smallSize);
            swatchRects[idxTop] = rTop;
            g2.setColor(palette[idxTop]);
            g2.fillRect(rTop.x, rTop.y, rTop.width, rTop.height);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(rTop.x, rTop.y, rTop.width, rTop.height);

            Rectangle rMid = new Rectangle(x, yMid, smallSize, smallSize);
            swatchRects[idxMid] = rMid;
            g2.setColor(palette[idxMid]);
            g2.fillRect(rMid.x, rMid.y, rMid.width, rMid.height);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(rMid.x, rMid.y, rMid.width, rMid.height);

            Rectangle rBot = new Rectangle(x, yBottom, smallSize, smallSize);
            swatchRects[idxBottom] = rBot;
            g2.setColor(palette[idxBottom]);
            g2.fillRect(rBot.x, rBot.y, rBot.width, rBot.height);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(rBot.x, rBot.y, rBot.width, rBot.height);
        }

        g2.dispose();
    }

    private Color[] buildPalette() {
        Color base = app.currentBrushColor();
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float h = hsb[0];
        float s = hsb[1];
        float v = hsb[2];

        // Hue offsets
        float analog = 0.07f; // ~25°
        float split = 0.08f;  // ~30° around complement

        // Base band (mid row): analogous trio at medium intensity
        Color midLeft = Color.getHSBColor(wrap(h - analog), s, v);
        Color midMid = Color.getHSBColor(h, s, v);
        Color midRight = Color.getHSBColor(wrap(h + analog), s, v);

        // Shadows / muted (bottom row): cooler, lower sat/value neutrals
        float shadowSat = clamp01(s * 0.35f);
        float shadowVal = clamp01(v * 0.65f);
        Color botLeft = Color.getHSBColor(wrap(h - analog - 0.03f), shadowSat, shadowVal);
        Color botMid = Color.getHSBColor(h, shadowSat * 0.8f, shadowVal * 0.9f);
        Color botRight = Color.getHSBColor(wrap(h + analog + 0.03f), shadowSat, shadowVal);

        // Accents / highlights (top row): split complements and a bright highlight
        float accentSat = clamp01(s * 0.85f + 0.15f);
        float accentVal = clamp01(v * 0.2f + 0.8f);
        Color topLeft = Color.getHSBColor(wrap(h + 0.5f - split), accentSat, accentVal);
        Color topMid = Color.getHSBColor(wrap(h - 0.025f), clamp01(s * 0.7f + 0.2f), clamp01(Math.max(v, 0.85f)));
        Color topRight = Color.getHSBColor(wrap(h + 0.5f + split), accentSat, accentVal);

        return new Color[]{
                midLeft, midMid, midRight,   // middle row
                botLeft, botMid, botRight,   // bottom row
                topLeft, topMid, topRight    // top row
        };
    }

    private float wrap(float hue) {
        hue = hue % 1f;
        return hue < 0f ? hue + 1f : hue;
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
