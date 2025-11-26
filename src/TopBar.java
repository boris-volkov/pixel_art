import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class TopBar extends JComponent {
    private final PixelArtApp app;
    private final ActionButton plus;
    private final ActionButton minus;
    private ActionButton active;
    private final Timer repeatTimer;

    TopBar(PixelArtApp app) {
        this.app = app;
        this.plus = new ActionButton("+", () -> app.adjustBrushBrightnessGlobal(PixelArtApp.BRUSH_BRIGHT_STEP), false);
        this.minus = new ActionButton("-", () -> app.adjustBrushBrightnessGlobal(-PixelArtApp.BRUSH_BRIGHT_STEP), false);
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(120, 90));
        repeatTimer = new Timer(120, e -> {
            if (active != null) {
                active.action.run();
            }
        });
        repeatTimer.setInitialDelay(350);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ActionButton target = hit(e.getX(), e.getY());
                if (target != null) {
                    press(target);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release();
            }
        };
        addMouseListener(mouse);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int swatchSize = 52;
        int btnW = 24;
        int btnH = 22;
        int gap = 8;

        int contentWidth = Math.max(swatchSize, btnW * 2 + gap);
        int contentHeight = swatchSize + gap + btnH;
        int startX = (getWidth() - contentWidth) / 2;
        int startY = (getHeight() - contentHeight) / 2;

        g2.setColor(app.currentBrushColor());
        g2.fillRect(startX, startY, swatchSize, swatchSize);
        g2.setColor(PixelArtApp.BUTTON_BORDER);
        g2.drawRect(startX, startY, swatchSize, swatchSize);

        int btnY = startY + swatchSize + gap;
        int btnX = startX + Math.max(0, (swatchSize - (btnW * 2 + gap)) / 2);
        minus.bounds = new Rectangle(btnX, btnY, btnW, btnH);
        plus.bounds = new Rectangle(btnX + btnW + gap, btnY, btnW, btnH);

        paintButton(g2, minus);
        paintButton(g2, plus);

        g2.dispose();
    }

    private void paintButton(Graphics2D g2, ActionButton button) {
        java.awt.Color base = button.accent ? PixelArtApp.ACCENT : PixelArtApp.BUTTON_BG;
        java.awt.Color hover = button.accent ? PixelArtApp.ACCENT.brighter() : PixelArtApp.BUTTON_HOVER;
        java.awt.Color pressed = button.accent ? PixelArtApp.ACCENT.darker() : PixelArtApp.BUTTON_ACTIVE;
        java.awt.Color fill = base;
        if (button.pressed) fill = pressed;
        else if (button.hover) fill = hover;
        g2.setColor(fill);
        g2.fillRect(button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height);
        g2.setColor(PixelArtApp.TEXT);
        PixelFont.draw(g2, button.label, button.bounds, 2, PixelArtApp.TEXT);
    }

    private ActionButton hit(int x, int y) {
        for (ActionButton b : new ActionButton[]{plus, minus}) {
            if (b.bounds != null && b.bounds.contains(x, y)) {
                return b;
            }
        }
        return null;
    }

    private void press(ActionButton button) {
        active = button;
        button.pressed = true;
        repaint(button.bounds);
        button.action.run();
        repeatTimer.restart();
    }

    private void release() {
        repeatTimer.stop();
        if (active != null) {
            active.pressed = false;
            repaint(active.bounds);
        }
        active = null;
    }
}
