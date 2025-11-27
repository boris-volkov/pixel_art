import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class TopBar extends JComponent {
    private final PixelArtApp app;
    private final Rectangle[] swatchRects = new Rectangle[9];

    TopBar(PixelArtApp app) {
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
                        app.setBrushColor(palette[i]);
                        repaint();
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] palette = buildPalette();
        Color base = app.currentBrushColor();

        int mainSize = (int) Math.round(56 * 1.25);
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
        Color comp = new Color(255 - base.getRed(), 255 - base.getGreen(), 255 - base.getBlue());

        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float h1 = (hsb[0] + 1f / 3f) % 1f;
        float h2 = (hsb[0] + 2f / 3f) % 1f;
        Color triad1 = Color.getHSBColor(h1, hsb[1], hsb[2]);
        Color triad2 = Color.getHSBColor(h2, hsb[1], hsb[2]);

        float baseSat = hsb[1];
        float mutedSat = clampSat(baseSat * 0.25f);
        float boostSat = clampSat(Math.max(baseSat, 1f - baseSat));

        Color compMuted = setSaturation(comp, mutedSat);
        Color triad1Muted = setSaturation(triad1, mutedSat);
        Color triad2Muted = setSaturation(triad2, mutedSat);

        Color compBoost = setSaturation(comp, boostSat);
        Color triad1Boost = setSaturation(triad1, boostSat);
        Color triad2Boost = setSaturation(triad2, boostSat);

        return new Color[]{
                comp, triad1, triad2,
                compMuted, triad1Muted, triad2Muted,
                compBoost, triad1Boost, triad2Boost
        };
    }

    private Color setSaturation(Color c, float sat) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float s = clampSat(sat);
        return Color.getHSBColor(hsb[0], s, hsb[2]);
    }

    private float clampSat(float s) {
        return Math.max(0f, Math.min(1f, s));
    }
}
