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
    private static final int SLIDER_LABEL_OFFSET = 4; // adjust label Y relative to slider row
    private static final int SLIDER_TRACK_OFFSET = 8; // adjust track Y relative to slider row
    private final PixelArtApp app;
    private final SliderControl redSlider;
    private final SliderControl greenSlider;
    private final SliderControl blueSlider;
    private final SliderControl hueSlider;
    private final SliderControl satSlider;
    private final SliderControl valSlider;
    private final SliderControl brushSlider;
    private final SliderControl zoomSlider;
    private final ActionButton toolBrush;
    private final ActionButton toolStamp;
    private final ActionButton toolFill;
    private final ActionButton toolBlur;
    private final ActionButton toolMove;
    private final ActionButton toolRotate;
    private final ActionButton toolErase;
    private final ActionButton[] layerButtons;
    private final ActionButton[] visButtons;
    private final ActionButton[] moveUpButtons;
    private final List<ActionButton> buttons;
    private SliderControl activeSlider;
    private ActionButton activeButton;
    private final Timer repeatTimer;

    ControlBar(PixelArtApp app) {
        this.app = app;
        redSlider = new SliderControl("RED", 0, 255, app.getRed(), v -> {
            app.setRed(v);
            repaint();
        });
        greenSlider = new SliderControl("GRN", 0, 255, app.getGreen(), v -> {
            app.setGreen(v);
            repaint();
        });
        blueSlider = new SliderControl("BLU", 0, 255, app.getBlue(), v -> {
            app.setBlue(v);
            repaint();
        });
        hueSlider = new SliderControl("HUE", 0, 359, app.getHueDegrees(), v -> {
            app.setHueDegrees(v);
            repaint();
        });
        satSlider = new SliderControl("SAT", 0, 100, app.getSaturationPercent(), v -> {
            app.setSaturationPercent(v);
            repaint();
        });
        valSlider = new SliderControl("BRI", 0, 100, app.getBrightnessPercent(), v -> {
            app.setBrightnessPercent(v);
            repaint();
        });
        brushSlider = new SliderControl("SIZE", 1, 64, app.getBrushSize(), v -> {
            app.setBrushSize(v);
            repaint();
        });
        int zoomCap = Math.max(2, Math.max(app.computeMaxCellSizeForScreen(), PixelArtApp.MAX_CELL_SIZE));
        zoomSlider = new SliderControl("ZOOM", 2, zoomCap, app.getCanvasCellSize(), v -> {
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
        toolRotate = new ActionButton("RT", () -> {
            app.setToolMode(PixelArtApp.ToolMode.ROTATE);
            repaint();
        }, true);
        toolErase = new ActionButton("ER", () -> {
            app.setToolMode(PixelArtApp.ToolMode.ERASER);
            repaint();
        }, true);
        layerButtons = new ActionButton[] {
                new ActionButton("L1", () -> {
                    app.setActiveLayer(0);
                    repaint();
                }, true),
                new ActionButton("L2", () -> {
                    app.setActiveLayer(1);
                    repaint();
                }, true),
                new ActionButton("L3", () -> {
                    app.setActiveLayer(2);
                    repaint();
                }, true)
        };
        visButtons = new ActionButton[] {
                new ActionButton("V", () -> { app.toggleLayerVisibility(0); repaint(); }, true),
                new ActionButton("V", () -> { app.toggleLayerVisibility(1); repaint(); }, true),
                new ActionButton("V", () -> { app.toggleLayerVisibility(2); repaint(); }, true)
        };
        moveUpButtons = new ActionButton[] {
                new ActionButton("^", () -> { app.swapLayerUp(0); repaint(); }, true),
                new ActionButton("^", () -> { app.swapLayerUp(1); repaint(); }, true),
                new ActionButton("^", () -> { app.swapLayerUp(2); repaint(); }, true)
        };
        buttons = List.of(
                new ActionButton("Fill", () -> app.getCanvas().fill(app.currentBrushColor()), true),
                new ActionButton("Clear", () -> app.getCanvas().clear(), true),
                new ActionButton("C-",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, PixelArtApp.COLOR_STEP, 0, 0)),
                        false),
                new ActionButton("C+",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, -PixelArtApp.COLOR_STEP, 0, 0)),
                        false),
                new ActionButton("M-",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, 0, PixelArtApp.COLOR_STEP, 0)),
                        false),
                new ActionButton("M+",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, 0, -PixelArtApp.COLOR_STEP, 0)),
                        false),
                new ActionButton("Y-",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, 0, 0, PixelArtApp.COLOR_STEP)),
                        false),
                new ActionButton("Y+",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustChannel(color, 0, 0, -PixelArtApp.COLOR_STEP)),
                        false),
                new ActionButton("B-",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustBrightness(color, -PixelArtApp.BRIGHT_STEP)),
                        false),
                new ActionButton("B+",
                        () -> app.getCanvas()
                                .adjustAll(color -> PixelArtApp.adjustBrightness(color, PixelArtApp.BRIGHT_STEP)),
                        false));

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
        hueSlider.value = app.getHueDegrees();
        satSlider.value = app.getSaturationPercent();
        valSlider.value = app.getBrightnessPercent();
        brushSlider.value = app.getBrushSize();
        int newZoomMax = Math.max(2, app.computeMaxCellSizeForScreen());
        int maxZoom = Math.max(newZoomMax, PixelArtApp.MAX_CELL_SIZE);
        zoomSlider.setMax(maxZoom);
        zoomSlider.setValueSilently(Math.min(maxZoom, app.getCanvasCellSize()));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PixelArtApp.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int padding = 16;
        int y = padding;
        int availableWidth = getWidth() - padding * 2;

        // Tool rows
        int btnHeightTop = 26;
        int gapTop = 6;
        ActionButton[] toolsPrimary = { toolBrush, toolErase, toolStamp, toolFill, toolBlur };
        int toolWidthTop = (availableWidth - gapTop * (toolsPrimary.length - 1)) / toolsPrimary.length;
        for (int i = 0; i < toolsPrimary.length; i++) {
            int x = padding + i * (toolWidthTop + gapTop);
            toolsPrimary[i].bounds = new Rectangle(x, y, toolWidthTop, btnHeightTop);
            paintButton(g2, toolsPrimary[i]);
        }
        y += btnHeightTop + 6;
        ActionButton[] toolsTransform = { toolMove, toolRotate };
        int toolWidthBottom = (availableWidth - gapTop) / toolsTransform.length;
        for (int i = 0; i < toolsTransform.length; i++) {
            int x = padding + i * (toolWidthBottom + gapTop);
            toolsTransform[i].bounds = new Rectangle(x, y, toolWidthBottom, btnHeightTop);
            paintButton(g2, toolsTransform[i]);
        }
        y += btnHeightTop + 10;

        y = drawSlider(g2, redSlider, padding, y, availableWidth);
        y = drawSlider(g2, greenSlider, padding, y, availableWidth);
        y = drawSlider(g2, blueSlider, padding, y, availableWidth);
        y = drawSlider(g2, hueSlider, padding, y, availableWidth);
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
        int trackHeight = 5 * scale;
        int thumbWidth = 4 * scale;
        int thumbHeight = 6 * scale;
        int labelWidth = 120;
        int valueWidth = 0;
        int btnWidth = 24;
        int gap = 4;
        int trackWidth = width - labelWidth - valueWidth - (btnWidth * 2) - (gap * 3);
        trackWidth = Math.max(30, trackWidth);
        int trackX = padding + labelWidth + btnWidth + gap;
        int trackY = y + SLIDER_TRACK_OFFSET;

        slider.track = new Rectangle(trackX, trackY, trackWidth, trackHeight);
        int valueX = trackX + (int) Math.round(slider.ratio() * trackWidth);
        int thumbY = trackY + (trackHeight / 2) - (thumbHeight / 2);
        slider.thumb = new Rectangle(valueX - thumbWidth / 2, thumbY, thumbWidth, thumbHeight);

        g2.setColor(PixelArtApp.TEXT);
        String valueText = slider.label + " : " + slider.value;
        Rectangle labelRect = new Rectangle(padding, y + SLIDER_LABEL_OFFSET, labelWidth, 18);
        PixelFont.drawLeft(g2, valueText.toUpperCase(), labelRect, 2, PixelArtApp.TEXT);

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

        return y + 36;
    }

    private void drawButtons(Graphics2D g2, int padding, int y, int width) {
        int btnHeight = 32;
        int spacing = 10;

        int cols = 4;
        int gap = 6;
        int colWidth = (width - (cols - 1) * gap) / cols;
        int rowHeight = btnHeight;

        ActionButton[][] grid = {
                { buttons.get(3), buttons.get(5), buttons.get(7), buttons.get(9) }, // top: C+, M+, Y+, B+
                { buttons.get(2), buttons.get(4), buttons.get(6), buttons.get(8) } // bottom: C-, M-, Y-, B-
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
        int rowGap = 8;
        for (int i = 0; i < layerButtons.length; i++) {
            ActionButton b = layerButtons[i];
            ActionButton v = visButtons[i];
            ActionButton up = moveUpButtons[i];
            b.label = app.getLayerName(i);
            int rowY = y + i * (rowH + rowGap);
            int visWidth = 32;
            int upWidth = 28;
            int gapSmall = 6;
            int labelWidth = width - visWidth - upWidth - gapSmall * 2;
            b.bounds = new Rectangle(padding, rowY, labelWidth, rowH);
            v.bounds = new Rectangle(padding + labelWidth + gapSmall, rowY, visWidth, rowH);
            up.bounds = new Rectangle(padding + labelWidth + gapSmall + visWidth + gapSmall, rowY, upWidth, rowH);
            paintButton(g2, b);
            paintButton(g2, v);
            paintButton(g2, up);
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
        for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, satSlider, valSlider, brushSlider,
                hueSlider, brushSlider, zoomSlider)) {
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
        if (slider.track == null)
            return;
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
        for (ActionButton tb : List.of(toolBrush, toolStamp, toolFill, toolBlur, toolMove, toolRotate, toolErase)) {
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
        for (ActionButton ub : moveUpButtons) {
            if (ub.bounds != null && ub.bounds.contains(x, y)) {
                return ub;
            }
        }
        for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, hueSlider, satSlider, valSlider, brushSlider,
                zoomSlider)) {
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
        if (button == toolMove || button == toolRotate) {
            return PixelArtApp.MAGENTA_BTN;
        }
        if (isVisButton(button)) {
            int idx = visIndex(button);
            boolean visible = idx >= 0 && app.isLayerVisible(idx);
            return visible ? PixelArtApp.BUTTON_BG : new Color(180, 60, 60);
        }
        if (label.startsWith("C")) {
            if (label.contains("-"))
                return new Color(220, 90, 80);
            return PixelArtApp.CYAN_BTN;
        }
        if (label.startsWith("M")) {
            if (label.contains("-"))
                return new Color(90, 180, 90);
            return PixelArtApp.MAGENTA_BTN;
        }
        if (label.startsWith("Y")) {
            if (label.contains("-"))
                return new Color(80, 120, 200);
            return PixelArtApp.YELLOW_BTN;
        }
        return accent ? PixelArtApp.ACCENT.darker() : PixelArtApp.BUTTON_BG;
    }

    private boolean isActiveTool(ActionButton button) {
        PixelArtApp.ToolMode mode = app.getToolMode();
        if (button == toolBrush)
            return mode == PixelArtApp.ToolMode.BRUSH;
        if (button == toolStamp)
            return mode == PixelArtApp.ToolMode.STAMP;
        if (button == toolFill)
            return mode == PixelArtApp.ToolMode.FILL;
        if (button == toolBlur)
            return mode == PixelArtApp.ToolMode.BLUR;
        if (button == toolMove)
            return mode == PixelArtApp.ToolMode.MOVE;
        if (button == toolRotate)
            return mode == PixelArtApp.ToolMode.ROTATE;
        if (button == toolErase)
            return mode == PixelArtApp.ToolMode.ERASER;
        return false;
    }

    private boolean isActiveLayer(ActionButton button) {
        int active = app.getActiveLayer();
        if (button == layerButtons[0])
            return active == 0;
        if (button == layerButtons[1])
            return active == 1;
        if (button == layerButtons[2])
            return active == 2;
        return false;
    }

    private boolean isVisButton(ActionButton button) {
        return button == visButtons[0] || button == visButtons[1] || button == visButtons[2];
    }

    private int visIndex(ActionButton button) {
        if (button == visButtons[0])
            return 0;
        if (button == visButtons[1])
            return 1;
        if (button == visButtons[2])
            return 2;
        return -1;
    }

    private boolean isLayerButton(ActionButton button) {
        return button == layerButtons[0] || button == layerButtons[1] || button == layerButtons[2];
    }
}
