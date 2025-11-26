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
    private final Rectangle[] swatchRects = new Rectangle[3];

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
        int totalWidth = smallSize * palette.length + gap * (palette.length - 1);
        int startX = (getWidth() - totalWidth) / 2;
        int y = mainY + mainSize + 12;

        for (int i = 0; i < palette.length; i++) {
            int x = startX + i * (smallSize + gap);
            swatchRects[i] = new Rectangle(x, y, smallSize, smallSize);
            g2.setColor(palette[i]);
            g2.fillRect(x, y, smallSize, smallSize);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(x, y, smallSize, smallSize);
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

        return new Color[]{comp, triad1, triad2};
    }
}
