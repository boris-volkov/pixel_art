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
    interface Host {
        boolean isStampUsingOwnColors();
        void setStampUseOwnColors(boolean useOwn);
    }

    private final PixelCanvas stampCanvas;
    private final Host app;
    private final ClearButton clearButton;
    private final ToggleButton modeButton;

    StampPanel(PixelCanvas stampCanvas, Host app) {
        this.stampCanvas = stampCanvas;
        this.app = app;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        stampCanvas.setBorder(javax.swing.BorderFactory.createLineBorder(PixelConstants.BUTTON_BORDER));
        stampCanvas.setAlignmentX(CENTER_ALIGNMENT);
        add(stampCanvas);
        add(javax.swing.Box.createRigidArea(new Dimension(1, 6)));
        clearButton = new ClearButton(() -> this.stampCanvas.clear());
        clearButton.setAlignmentX(CENTER_ALIGNMENT);
        modeButton = new ToggleButton(app.isStampUsingOwnColors() ? "OWN" : "MAIN", this::toggleMode);
        modeButton.setAlignmentX(CENTER_ALIGNMENT);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(clearButton);
        buttons.add(javax.swing.Box.createRigidArea(new Dimension(6, 1)));
        buttons.add(modeButton);
        add(buttons);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    private void toggleMode() {
        boolean useOwn = !app.isStampUsingOwnColors();
        app.setStampUseOwnColors(useOwn);
        modeButton.setLabel(useOwn ? "OWN" : "MAIN");
        modeButton.repaint();
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
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
                @Override
                public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
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
            Color fill = PixelConstants.BUTTON_BG;
            if (pressed) fill = PixelConstants.BUTTON_ACTIVE;
            else if (hover) fill = PixelConstants.BUTTON_HOVER;
            g2.setColor(fill);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(PixelConstants.TEXT);
            PixelFont.draw(g2, "CLR", new java.awt.Rectangle(0, 0, getWidth(), getHeight()), 2, PixelConstants.TEXT);
            g2.dispose();
        }
    }

    private static class ToggleButton extends JComponent {
        private final Runnable action;
        private boolean hover = false;
        private boolean pressed = false;
        private String label;

        ToggleButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
            setPreferredSize(new Dimension(64, 26));
            setMaximumSize(new Dimension(80, 26));
            setMinimumSize(new Dimension(50, 26));
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
                @Override
                public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
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

        void setLabel(String text) {
            this.label = text;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = PixelConstants.BUTTON_BG;
            if (pressed) fill = PixelConstants.BUTTON_ACTIVE;
            else if (hover) fill = PixelConstants.BUTTON_HOVER;
            g2.setColor(fill);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(PixelConstants.TEXT);
            PixelFont.draw(g2, label.toUpperCase(), new java.awt.Rectangle(0, 0, getWidth(), getHeight()), 2, PixelConstants.TEXT);
            g2.dispose();
        }
    }
}
