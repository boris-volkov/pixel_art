import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class StampPanel extends JPanel {
    private final PixelCanvas stampCanvas;
    private final ClearButton clearButton;

    StampPanel(PixelCanvas stampCanvas) {
        this.stampCanvas = stampCanvas;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        stampCanvas.setBorder(javax.swing.BorderFactory.createLineBorder(PixelArtApp.BUTTON_BORDER));
        stampCanvas.setAlignmentX(CENTER_ALIGNMENT);
        add(stampCanvas);
        add(javax.swing.Box.createRigidArea(new Dimension(1, 6)));
        clearButton = new ClearButton(() -> this.stampCanvas.clear());
        clearButton.setAlignmentX(CENTER_ALIGNMENT);
        add(clearButton);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    private static class ClearButton extends JComponent {
        private final Runnable action;
        private boolean hover = false;
        private boolean pressed = false;

        ClearButton(Runnable action) {
            this.action = action;
            setPreferredSize(new Dimension(80, 26));
            setMaximumSize(new Dimension(120, 26));
            setMinimumSize(new Dimension(60, 26));
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (pressed && contains(e.getPoint())) {
                        action.run();
                    }
                    pressed = false;
                    repaint();
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = PixelArtApp.BUTTON_BG;
            if (pressed)
                fill = PixelArtApp.BUTTON_ACTIVE;
            else if (hover)
                fill = PixelArtApp.BUTTON_HOVER;
            g2.setColor(fill);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(PixelArtApp.TEXT);
            PixelFont.draw(g2, "CLR", new java.awt.Rectangle(0, 0, getWidth(), getHeight()), 2, PixelArtApp.TEXT);
            g2.dispose();
        }
    }
}
