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
    private final Rectangle[] swatchRects = new Rectangle[6];

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

        int mainSize = 56;
        int mainX = (getWidth() - mainSize) / 2;
        int mainY = 8;

        g2.setColor(base);
        g2.fillRect(mainX, mainY, mainSize, mainSize);
        g2.setColor(PixelArtApp.BUTTON_BORDER);
        g2.drawRect(mainX, mainY, mainSize, mainSize);

        int smallSize = 28;
        int gap = 6;
        int totalWidth = smallSize * 3 + gap * 2;
        int startX = (getWidth() - totalWidth) / 2;
        int yTop = mainY + mainSize + 12;
        int yBottom = yTop + smallSize + gap;

        for (int i = 0; i < 3; i++) {
            int x = startX + i * (smallSize + gap);
            int idxTop = i;
            int idxBottom = i + 3;

            swatchRects[idxTop] = new Rectangle(x, yTop, smallSize, smallSize);
            g2.setColor(palette[idxTop]);
            g2.fillRect(x, yTop, smallSize, smallSize);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(x, yTop, smallSize, smallSize);

            swatchRects[idxBottom] = new Rectangle(x, yBottom, smallSize, smallSize);
            g2.setColor(palette[idxBottom]);
            g2.fillRect(x, yBottom, smallSize, smallSize);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(x, yBottom, smallSize, smallSize);
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

        Color compMuted = desaturate(comp, 0.35f);
        Color triad1Muted = desaturate(triad1, 0.35f);
        Color triad2Muted = desaturate(triad2, 0.35f);

        return new Color[]{comp, triad1, triad2, compMuted, triad1Muted, triad2Muted};
    }

    private Color desaturate(Color c, float strength) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float sat = Math.max(0f, Math.min(1f, hsb[1] * strength));
        return Color.getHSBColor(hsb[0], sat, hsb[2]);
    }
}
