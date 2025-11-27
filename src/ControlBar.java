import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

class ControlBar extends JComponent {
    private final PixelArtApp app;
    private final SliderControl redSlider;
    private final SliderControl greenSlider;
    private final SliderControl blueSlider;
    private final SliderControl satSlider;
    private final SliderControl valSlider;
    private final SliderControl brushSlider;
    private final SliderControl zoomSlider;
    private final ActionButton toolBrush;
    private final ActionButton toolStamp;
    private final ActionButton toolFill;
    private final ActionButton toolBlur;
    private final ActionButton toolMove;
    private final ActionButton[] layerButtons;
    private final ActionButton[] visButtons;
    private final List<ActionButton> buttons;
    private SliderControl activeSlider;
    private ActionButton activeButton;
    private final Timer repeatTimer;

    ControlBar(PixelArtApp app) {
        this.app = app;
        redSlider = new SliderControl("R", 0, 255, app.getRed(), v -> {
            app.setRed(v);
            repaint();
        });
        greenSlider = new SliderControl("G", 0, 255, app.getGreen(), v -> {
            app.setGreen(v);
            repaint();
        });
        blueSlider = new SliderControl("B", 0, 255, app.getBlue(), v -> {
            app.setBlue(v);
            repaint();
        });
        satSlider = new SliderControl("Sat", 0, 100, app.getSaturationPercent(), v -> {
            app.setSaturationPercent(v);
            repaint();
        });
        valSlider = new SliderControl("Brt", 0, 100, app.getBrightnessPercent(), v -> {
            app.setBrightnessPercent(v);
            repaint();
        });
        brushSlider = new SliderControl("Brush", 1, 64, app.getBrushSize(), v -> {
            app.setBrushSize(v);
            repaint();
        });
        int zoomCap = PixelArtApp.MAX_CELL_SIZE;
        zoomSlider = new SliderControl("Zoom", 2, Math.max(zoomCap, 2), app.getCanvasCellSize(), v -> {
            app.setCanvasCellSize(v);
            repaint();
        });
        toolBrush = new ActionButton("BR", () -> {
            app.setToolMode(PixelArtApp.ToolMode.BRUSH);
            repaint();
        }, true);
        toolStamp = new ActionButton("ST", () -> {
            app.setToolMode(PixelArtApp.ToolMode.STAMP);
            repaint();
        }, true);
        toolFill = new ActionButton("FI", () -> {
            app.setToolMode(PixelArtApp.ToolMode.FILL);
            repaint();
        }, true);
        toolBlur = new ActionButton("BL", () -> {
            app.setToolMode(PixelArtApp.ToolMode.BLUR);
            repaint();
        }, true);
        toolMove = new ActionButton("MV", () -> {
            app.setToolMode(PixelArtApp.ToolMode.MOVE);
            repaint();
        }, true);
        layerButtons = new ActionButton[]{
                new ActionButton("L1", () -> { app.setActiveLayer(0); repaint(); }, true),
                new ActionButton("L2", () -> { app.setActiveLayer(1); repaint(); }, true),
                new ActionButton("L3", () -> { app.setActiveLayer(2); repaint(); }, true)
        };
        visButtons = new ActionButton[]{
                new ActionButton("V", () -> { app.toggleLayerVisibility(0); repaint(); }, true),
                new ActionButton("V", () -> { app.toggleLayerVisibility(1); repaint(); }, true),
                new ActionButton("V", () -> { app.toggleLayerVisibility(2); repaint(); }, true)
        };
        buttons = List.of(
                new ActionButton("Fill", () -> app.getCanvas().fill(app.currentBrushColor()), true),
                new ActionButton("Clear", () -> app.getCanvas().clear(), true),
                new ActionButton("C-", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, PixelArtApp.COLOR_STEP, 0, 0)), false),
                new ActionButton("C+", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, -PixelArtApp.COLOR_STEP, 0, 0)), false),
                new ActionButton("M-", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, 0, PixelArtApp.COLOR_STEP, 0)), false),
                new ActionButton("M+", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, 0, -PixelArtApp.COLOR_STEP, 0)), false),
                new ActionButton("Y-", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, 0, 0, PixelArtApp.COLOR_STEP)), false),
                new ActionButton("Y+", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustChannel(color, 0, 0, -PixelArtApp.COLOR_STEP)), false),
                new ActionButton("B-", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustBrightness(color, -PixelArtApp.BRIGHT_STEP)), false),
                new ActionButton("B+", () -> app.getCanvas().adjustAll(color -> PixelArtApp.adjustBrightness(color, PixelArtApp.BRIGHT_STEP)), false)
        );

        setOpaque(true);
        setBackground(PixelArtApp.BG);
        setPreferredSize(new Dimension(PixelArtApp.CONTROL_BAR_WIDTH, 0));
        repeatTimer = new Timer(70, e -> {
            if (activeButton != null) {
                activeButton.action.run();
            }
        });
        repeatTimer.setInitialDelay(180);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (tryPressButton(e)) {
                    return;
                }
                activeSlider = findSlider(e.getX(), e.getY());
                if (activeSlider != null) {
                    updateSliderFromMouse(activeSlider, e.getX());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (activeSlider != null) {
                    updateSliderFromMouse(activeSlider, e.getX());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                stopRepeat();
                activeSlider = null;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void syncSliders() {
        redSlider.value = app.getRed();
        greenSlider.value = app.getGreen();
        blueSlider.value = app.getBlue();
        satSlider.value = app.getSaturationPercent();
        valSlider.value = app.getBrightnessPercent();
        brushSlider.value = app.getBrushSize();
        zoomSlider.value = app.getCanvasCellSize();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PixelArtApp.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int padding = 14;
        int y = padding;
        int availableWidth = getWidth() - padding * 2;

        // Tool row
        int btnHeightTop = 26;
        int gapTop = 6;
        ActionButton[] tools = {toolBrush, toolStamp, toolFill, toolBlur, toolMove};
        int toolWidth = (availableWidth - gapTop * (tools.length - 1)) / tools.length;
        for (int i = 0; i < tools.length; i++) {
            int x = padding + i * (toolWidth + gapTop);
            tools[i].bounds = new Rectangle(x, y, toolWidth, btnHeightTop);
            paintButton(g2, tools[i]);
        }
        y += btnHeightTop + 10;

        y = drawSlider(g2, redSlider, padding, y, availableWidth);
        y = drawSlider(g2, greenSlider, padding, y, availableWidth);
        y = drawSlider(g2, blueSlider, padding, y, availableWidth);
        y = drawSlider(g2, satSlider, padding, y, availableWidth);
        y = drawSlider(g2, valSlider, padding, y, availableWidth);
        y = drawSlider(g2, brushSlider, padding, y, availableWidth);
        y = drawSlider(g2, zoomSlider, padding, y, availableWidth);

        y += 8;
        drawButtons(g2, padding, y, availableWidth);

        g2.dispose();
    }

    private int drawSlider(Graphics2D g2, SliderControl slider, int padding, int y, int width) {
        int scale = 2;
        int trackHeight = 6 * scale;
        int thumbWidth = 4 * scale;
        int thumbHeight = 7 * scale;
        int labelWidth = 90;
        int valueWidth = 0;
        int btnWidth = 24;
        int gap = 4;
        int trackWidth = width - labelWidth - valueWidth - (btnWidth * 2) - (gap * 3);
        trackWidth = Math.max(30, trackWidth);
        int trackX = padding + labelWidth + btnWidth + gap;
        int trackY = y + 12;

        slider.track = new Rectangle(trackX, trackY, trackWidth, trackHeight);
        int valueX = trackX + (int) Math.round(slider.ratio() * trackWidth);
        int thumbY = trackY + (trackHeight / 2) - (thumbHeight / 2);
        slider.thumb = new Rectangle(valueX - thumbWidth / 2, thumbY, thumbWidth, thumbHeight);

        g2.setColor(PixelArtApp.TEXT);
        String valueText = String.valueOf(slider.value);
        g2.drawString(slider.label + " : " + valueText, padding, trackY + 8);

        g2.setColor(PixelArtApp.BUTTON_BG);
        g2.fillRect(trackX, trackY, trackWidth, trackHeight);
        g2.setColor(PixelArtApp.BUTTON_BORDER);
        g2.drawRect(trackX, trackY, trackWidth, trackHeight);

        double fillRatio = slider.ratio();
        int fillWidth = (int) (trackWidth * fillRatio);
        g2.setColor(PixelArtApp.ACCENT);
        g2.fillRect(trackX, trackY, fillWidth, trackHeight);

        Color thumbColor = new Color(
                Math.min(255, PixelArtApp.ACCENT.getRed() + 40),
                Math.min(255, PixelArtApp.ACCENT.getGreen() + 40),
                Math.min(255, PixelArtApp.ACCENT.getBlue() + 40));
        g2.setColor(thumbColor);
        g2.fillRect(slider.thumb.x, slider.thumb.y, slider.thumb.width, slider.thumb.height);
        g2.setColor(PixelArtApp.ACCENT);
        g2.drawRect(slider.thumb.x, slider.thumb.y, slider.thumb.width, slider.thumb.height);

        slider.minus.bounds = new Rectangle(trackX - btnWidth - gap, trackY - 6, btnWidth, 22);
        slider.plus.bounds = new Rectangle(trackX + trackWidth + gap, trackY - 6, btnWidth, 22);
        paintButton(g2, slider.minus);
        paintButton(g2, slider.plus);

        return y + 30;
    }

    private void drawButtons(Graphics2D g2, int padding, int y, int width) {
        int btnHeight = 32;
        int spacing = 6;

        int cols = 4;
        int gap = 6;
        int colWidth = (width - (cols - 1) * gap) / cols;
        int rowHeight = btnHeight;

        ActionButton[][] grid = {
                {buttons.get(3), buttons.get(5), buttons.get(7), buttons.get(9)}, // top: C+, M+, Y+, B+
                {buttons.get(2), buttons.get(4), buttons.get(6), buttons.get(8)}  // bottom: C-, M-, Y-, B-
        };

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < cols; col++) {
                int x = padding + col * (colWidth + gap);
                int currentY = y + row * (rowHeight + gap);
                ActionButton button = grid[row][col];
                button.bounds = new Rectangle(x, currentY, colWidth, rowHeight);
                paintButton(g2, button);
            }
        }

        y += rowHeight * 2 + gap + 8;

        int btnWidthFull = (width - spacing) / 2;
        ActionButton fill = buttons.get(0);
        ActionButton clear = buttons.get(1);
        fill.bounds = new Rectangle(padding, y, btnWidthFull, btnHeight);
        paintButton(g2, fill);
        clear.bounds = new Rectangle(padding + btnWidthFull + spacing, y, btnWidthFull, btnHeight);
        paintButton(g2, clear);
        y += btnHeight + spacing + 4;

        int rowH = btnHeight;
        int rowGap = 6;
        for (int i = 0; i < layerButtons.length; i++) {
            ActionButton b = layerButtons[i];
            ActionButton v = visButtons[i];
            int rowY = y + i * (rowH + rowGap);
            int visWidth = 32;
            int labelWidth = width - visWidth - 6;
            b.bounds = new Rectangle(padding, rowY, labelWidth, rowH);
            v.bounds = new Rectangle(padding + labelWidth + 6, rowY, visWidth, rowH);
            paintButton(g2, b);
            paintButton(g2, v);
        }
    }

    private void paintButton(Graphics2D g2, ActionButton button) {
        Color themed = themedButtonColor(button, button.label, button.accent);
        Color fill = themed;
        if (button.pressed) {
            fill = PixelArtApp.BUTTON_ACTIVE;
        } else if (button.hover) {
            fill = PixelArtApp.BUTTON_HOVER;
        }
        g2.setColor(fill);
        g2.fillRect(button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height);
        g2.setColor(PixelArtApp.TEXT);
        PixelFont.draw(g2, button.label.toUpperCase(), button.bounds, 2, PixelArtApp.TEXT);
    }

    private boolean tryPressButton(MouseEvent e) {
        ActionButton target = findButtonAt(e.getX(), e.getY());
        if (target != null) {
            pressButton(target);
            return true;
        }
        return false;
    }

    private SliderControl findSlider(int x, int y) {
        for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, satSlider, valSlider, brushSlider, zoomSlider)) {
            if (s.track != null && s.track.contains(x, y)) {
                return s;
            }
            if (s.thumb != null && s.thumb.contains(x, y)) {
                return s;
            }
        }
        return null;
    }

    private void updateSliderFromMouse(SliderControl slider, int mouseX) {
        if (slider.track == null) return;
        int relative = mouseX - slider.track.x;
        int clamped = Math.max(0, Math.min(slider.track.width, relative));
        double ratio = slider.track.width == 0 ? 0 : (double) clamped / slider.track.width;
        int newValue = slider.min + (int) Math.round(ratio * (slider.max - slider.min));
        slider.setValue(newValue);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PixelArtApp.CONTROL_BAR_WIDTH, 0);
    }

    private ActionButton findButtonAt(int x, int y) {
        for (ActionButton tb : List.of(toolBrush, toolStamp, toolFill, toolBlur, toolMove)) {
            if (tb.bounds != null && tb.bounds.contains(x, y)) {
                return tb;
            }
        }
        for (ActionButton lb : layerButtons) {
            if (lb.bounds != null && lb.bounds.contains(x, y)) {
                return lb;
            }
        }
        for (ActionButton vb : visButtons) {
            if (vb.bounds != null && vb.bounds.contains(x, y)) {
                return vb;
            }
        }
        for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, satSlider, valSlider, brushSlider, zoomSlider)) {
            for (ActionButton b : List.of(s.minus, s.plus)) {
                if (b.bounds != null && b.bounds.contains(x, y)) {
                    return b;
                }
            }
        }
        for (ActionButton button : buttons) {
            if (button.bounds != null && button.bounds.contains(x, y)) {
                return button;
            }
        }
        return null;
    }

    private void pressButton(ActionButton button) {
        activeButton = button;
        button.pressed = true;
        repaint(button.bounds);
        button.action.run();
        repeatTimer.restart();
    }

    private void stopRepeat() {
        if (activeButton != null) {
            activeButton.pressed = false;
            repaint(activeButton.bounds);
            activeButton = null;
        }
        repeatTimer.stop();
    }

    private Color themedButtonColor(ActionButton button, String label, boolean accent) {
        if (isActiveTool(button) || isActiveLayer(button)) {
            return PixelArtApp.ACCENT;
        }
        if (isVisButton(button)) {
            int idx = visIndex(button);
            boolean visible = idx >= 0 && app.isLayerVisible(idx);
            return visible ? PixelArtApp.BUTTON_BG : new Color(180, 60, 60);
        }
        if (label.startsWith("C")) {
            if (label.contains("-")) return new Color(220, 90, 80);
            return PixelArtApp.CYAN_BTN;
        }
        if (label.startsWith("M")) {
            if (label.contains("-")) return new Color(90, 180, 90);
            return PixelArtApp.MAGENTA_BTN;
        }
        if (label.startsWith("Y")) {
            if (label.contains("-")) return new Color(80, 120, 200);
            return PixelArtApp.YELLOW_BTN;
        }
        return accent ? PixelArtApp.ACCENT.darker() : PixelArtApp.BUTTON_BG;
    }

    private boolean isActiveTool(ActionButton button) {
        PixelArtApp.ToolMode mode = app.getToolMode();
        if (button == toolBrush) return mode == PixelArtApp.ToolMode.BRUSH;
        if (button == toolStamp) return mode == PixelArtApp.ToolMode.STAMP;
        if (button == toolFill) return mode == PixelArtApp.ToolMode.FILL;
        if (button == toolBlur) return mode == PixelArtApp.ToolMode.BLUR;
        if (button == toolMove) return mode == PixelArtApp.ToolMode.MOVE;
        return false;
    }

    private boolean isActiveLayer(ActionButton button) {
        int active = app.getActiveLayer();
        if (button == layerButtons[0]) return active == 0;
        if (button == layerButtons[1]) return active == 1;
        if (button == layerButtons[2]) return active == 2;
        return false;
    }

    private boolean isVisButton(ActionButton button) {
        return button == visButtons[0] || button == visButtons[1] || button == visButtons[2];
    }

    private int visIndex(ActionButton button) {
        if (button == visButtons[0]) return 0;
        if (button == visButtons[1]) return 1;
        if (button == visButtons[2]) return 2;
        return -1;
    }
}
