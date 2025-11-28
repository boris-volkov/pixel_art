import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class AnimationPanel extends JComponent {
    private final PixelArtApp app;
    private Rectangle playBounds;
    private Rectangle onionBounds;
    private Rectangle addBounds;
    private Rectangle deleteBounds;
    private Rectangle dupBounds;
    private final List<Rectangle> frameRects = new ArrayList<>();

    AnimationPanel(PixelArtApp app) {
        this.app = app;
        setOpaque(true);
        setBackground(PixelArtApp.BG);
        setPreferredSize(new java.awt.Dimension(0, 110));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        };
        addMouseListener(mouse);
    }

    private void handleClick(int x, int y) {
        if (playBounds != null && playBounds.contains(x, y)) {
            app.togglePlayback();
            repaint();
            return;
        }
        if (onionBounds != null && onionBounds.contains(x, y)) {
            app.toggleOnion();
            repaint();
            return;
        }
        if (addBounds != null && addBounds.contains(x, y)) {
            app.addBlankFrame();
            repaint();
            return;
        }
        if (deleteBounds != null && deleteBounds.contains(x, y)) {
            app.deleteCurrentFrame();
            repaint();
            return;
        }
        if (dupBounds != null && dupBounds.contains(x, y)) {
            app.duplicateCurrentFrame();
            repaint();
            return;
        }
        for (int i = 0; i < frameRects.size(); i++) {
            Rectangle r = frameRects.get(i);
            if (r.contains(x, y)) {
                app.selectFrame(i);
                repaint();
                break;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PixelArtApp.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int padding = 8;
        int btnWidth = 60;
        int btnHeight = 26;
        playBounds = new Rectangle(padding, padding, btnWidth, btnHeight);
        onionBounds = new Rectangle(padding + btnWidth + 6, padding, btnWidth, btnHeight);
        addBounds = new Rectangle(padding, padding + btnHeight + 6, btnWidth, btnHeight);
        deleteBounds = new Rectangle(padding + btnWidth + 6, padding + btnHeight + 6, btnWidth, btnHeight);
        dupBounds = new Rectangle(padding, padding + (btnHeight + 6) * 2, btnWidth, btnHeight);

        drawButton(g2, playBounds, app.isPlaying() ? "STOP" : "PLAY", true);
        drawButton(g2, onionBounds, "ONION", app.isOnionEnabled());
        drawButton(g2, addBounds, "+", true);
        drawButton(g2, deleteBounds, "-", true);
        drawButton(g2, dupBounds, "DUP", true);

        int framesStartX = padding + btnWidth * 2 + 12 + 6;
        int frameSize = 32;
        int frameGap = 8;
        frameRects.clear();
        List<PixelArtApp.FrameData> frames = app.getFrames();
        for (int i = 0; i < frames.size(); i++) {
            int x = framesStartX + i * (frameSize + frameGap);
            int y = padding;
            Rectangle r = new Rectangle(x, y, frameSize, frameSize);
            frameRects.add(r);
            boolean active = (i == app.getCurrentFrameIndex());
            g2.setColor(active ? PixelArtApp.ACCENT : PixelArtApp.BUTTON_BG);
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.setColor(PixelArtApp.BUTTON_BORDER);
            g2.drawRect(r.x, r.y, r.width, r.height);
            PixelFont.drawCentered(g2, String.valueOf(i + 1), r, 2, PixelArtApp.TEXT);
        }

        g2.dispose();
    }

    private void drawButton(Graphics2D g2, Rectangle bounds, String label, boolean accent) {
        g2.setColor(accent ? PixelArtApp.BUTTON_ACTIVE : PixelArtApp.BUTTON_BG);
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g2.setColor(PixelArtApp.BUTTON_BORDER);
        g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        Color text = accent ? new Color(120, 220, 120) : PixelArtApp.TEXT;
        PixelFont.drawCentered(g2, label, bounds, 2, text);
    }
}
