import javax.swing.JFrame;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class PixelArtApp {
    private static final Color BG = new Color(96, 96, 96);
    private static final Color TEXT = new Color(226, 231, 240);
    private static final Color MUTED_TEXT = new Color(160, 170, 186);
    private static final Color ACCENT = new Color(126, 170, 220);
    private static final Color CANVAS_BG = new Color(86, 86, 86);
    private static final Color BUTTON_BG = new Color(60, 64, 70);
    private static final Color BUTTON_BORDER = new Color(110, 120, 130);
    private static final Color BUTTON_HOVER = new Color(72, 76, 86);
    private static final Color BUTTON_ACTIVE = new Color(88, 92, 104);
    private static final Color CYAN_BTN = new Color(56, 180, 220);
    private static final Color MAGENTA_BTN = new Color(200, 90, 200);
    private static final Color YELLOW_BTN = new Color(210, 200, 90);
    private static final int CONTROL_BAR_WIDTH = 260;
    private static final int COLOR_STEP = 1;
    private static final int BRIGHT_STEP = 1;
    private static final int BRUSH_BRIGHT_STEP = 1;
    private static final int TARGET_CANVAS_SIZE = 640;
    private static final int MIN_CELL_SIZE = 2;
    private static final int MAX_CELL_SIZE = 20;
    private enum ToolMode {BRUSH, STAMP}

    private PixelCanvas canvas;
    private PixelCanvas stampCanvas;
    private TopBar topBar;
    private JPanel canvasHolder;
    private ControlBar controlBar;
    private int red = 32;
    private int green = 32;
    private int blue = 32;
    private int brushSize = 1;
    private int gridSize = 128;
    private Console console;
    private ToolMode toolMode = ToolMode.BRUSH;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            tryInstallNimbus();
            new PixelArtApp().start();
        });
    }

    private void start() {
        JFrame frame = new JFrame("Pixel Art");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);

        canvas = new PixelCanvas(gridSize, gridSize, computeCellSizeForGrid(gridSize), this::setBrushColor, this::onBrushSizeChanged, () -> toolMode, this::getStampPixels);
        canvas.setCurrentColor(currentBrushColor());
        canvas.setBrushSize(brushSize);

        stampCanvas = new PixelCanvas(16, 16, 10, this::setBrushColor, size -> {
        }, () -> ToolMode.BRUSH, null);
        stampCanvas.setCurrentColor(currentBrushColor());

        canvasHolder = new JPanel(new GridBagLayout());
        canvasHolder.setBackground(BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        canvasHolder.add(canvas, gbc);

        controlBar = new ControlBar();
        console = new Console();

        JPanel east = new JPanel(new BorderLayout());
        east.setBackground(BG);
        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));
        topRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        topBar = new TopBar();
        JPanel stampHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        stampHolder.setOpaque(false);
        stampCanvas.setBorder(BorderFactory.createLineBorder(BUTTON_BORDER));
        stampHolder.add(stampCanvas);

        topRow.add(topBar);
        topRow.add(Box.createRigidArea(new Dimension(8, 1)));
        topRow.add(stampHolder);
        JPanel topWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 8));
        topWrap.setOpaque(false);
        topWrap.add(topRow);
        east.add(topWrap, BorderLayout.NORTH);
        east.add(controlBar, BorderLayout.CENTER);

        frame.add(canvasHolder, BorderLayout.CENTER);
        frame.add(east, BorderLayout.EAST);
        frame.add(console, BorderLayout.SOUTH);

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Z"), "undo");
        frame.getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.undo();
                controlBar.syncSliders();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void tryInstallNimbus() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
                    // Keep default if Nimbus fails.
                }
                break;
            }
        }
    }

    private int computeCellSizeForGrid(int grid) {
        int computed = TARGET_CANVAS_SIZE / grid;
        return Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, computed));
    }

    private Color currentBrushColor() {
        return new Color(red, green, blue);
    }

    private void updateBrushTargets() {
        updateBrushTargets(currentBrushColor());
    }

    private void updateBrushTargets(Color color) {
        if (canvas != null) {
            canvas.setCurrentColor(color);
        }
        if (stampCanvas != null) {
            stampCanvas.setCurrentColor(color);
        }
        if (topBar != null) {
            topBar.repaint();
        }
    }

    private void setBrushColor(Color color) {
        if (color == null) {
            return;
        }
        red = clamp(color.getRed());
        green = clamp(color.getGreen());
        blue = clamp(color.getBlue());
        updateBrushTargets(color);
        controlBar.syncSliders();
    }

    private void onBrushSizeChanged(int size) {
        brushSize = size;
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    private void adjustBrushBrightnessGlobal(int delta) {
        red = clamp(red + delta);
        green = clamp(green + delta);
        blue = clamp(blue + delta);
        updateBrushTargets();
        if (controlBar != null) {
            controlBar.syncSliders();
        }
    }

    private Color[][] getStampPixels() {
        return stampCanvas != null ? stampCanvas.getPixelsCopy() : null;
    }

    private void rebuildCanvas(int newGridSize) {
        gridSize = newGridSize;
        PixelCanvas newCanvas = new PixelCanvas(gridSize, gridSize, computeCellSizeForGrid(gridSize), this::setBrushColor, this::onBrushSizeChanged, () -> toolMode, this::getStampPixels);
        newCanvas.setCurrentColor(currentBrushColor());
        newCanvas.setBrushSize(brushSize);
        this.canvas = newCanvas;

        canvasHolder.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        canvasHolder.add(newCanvas, gbc);
        canvasHolder.revalidate();
        canvasHolder.repaint();
    }

    /**
     * Custom drawn control bar that lives on the canvas side.
     */
    private class ControlBar extends JComponent {
        private final Font labelFont = new Font("Lucida Console", Font.BOLD, 13);
        private final Font smallFont = new Font("Lucida Console", Font.PLAIN, 12);
        private final SliderControl redSlider = new SliderControl("R", 0, 255, red, v -> {
            red = v;
            updateBrushTargets();
            repaint();
        });
        private final SliderControl greenSlider = new SliderControl("G", 0, 255, green, v -> {
            green = v;
            updateBrushTargets();
            repaint();
        });
        private final SliderControl blueSlider = new SliderControl("B", 0, 255, blue, v -> {
            blue = v;
            updateBrushTargets();
            repaint();
        });
        private final SliderControl brushSlider = new SliderControl("Brush", 1, 64, brushSize, v -> {
            brushSize = v;
            canvas.setBrushSize(brushSize);
            repaint();
        });
        private final ActionButton modeButton = new ActionButton("BRUSH", this::toggleMode, true);
        private final ActionButton stampClearButton = new ActionButton("S-CLR", () -> {
            if (stampCanvas != null) {
                stampCanvas.clear();
            }
        }, true);
        private final List<ActionButton> buttons = List.of(
                new ActionButton("Fill", () -> canvas.fill(currentBrushColor()), true),
                new ActionButton("Clear", canvas::clear, true),
                new ActionButton("C-", () -> canvas.adjustAll(color -> adjustChannel(color, COLOR_STEP, 0, 0)), false),
                new ActionButton("C+", () -> canvas.adjustAll(color -> adjustChannel(color, -COLOR_STEP, 0, 0)), false),
                new ActionButton("M-", () -> canvas.adjustAll(color -> adjustChannel(color, 0, COLOR_STEP, 0)), false),
                new ActionButton("M+", () -> canvas.adjustAll(color -> adjustChannel(color, 0, -COLOR_STEP, 0)), false),
                new ActionButton("Y-", () -> canvas.adjustAll(color -> adjustChannel(color, 0, 0, COLOR_STEP)), false),
                new ActionButton("Y+", () -> canvas.adjustAll(color -> adjustChannel(color, 0, 0, -COLOR_STEP)), false),
                new ActionButton("B-", () -> canvas.adjustAll(color -> adjustBrightness(color, -BRIGHT_STEP)), false),
                new ActionButton("B+", () -> canvas.adjustAll(color -> adjustBrightness(color, BRIGHT_STEP)), false)
        );
        private SliderControl activeSlider;
        private ActionButton activeButton;
        private final Timer repeatTimer;

        ControlBar() {
            setOpaque(true);
            setBackground(BG);
            setPreferredSize(new Dimension(CONTROL_BAR_WIDTH, 0));
            repeatTimer = new Timer(120, e -> {
                if (activeButton != null) {
                    activeButton.action.run();
                }
            });
            repeatTimer.setInitialDelay(350);
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
            redSlider.value = red;
            greenSlider.value = green;
            blueSlider.value = blue;
            brushSlider.value = brushSize;
            repaint();
        }

        private void updateBrushTargets() {
            Color c = currentBrushColor();
            canvas.setCurrentColor(c);
            if (stampCanvas != null) {
                stampCanvas.setCurrentColor(c);
            }
            if (topBar != null) {
                topBar.repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            int padding = 14;
            int y = padding;
            int availableWidth = getWidth() - padding * 2;

            // Start below top padding
            y += 8;
            y = drawSlider(g2, redSlider, padding, y, availableWidth);
            y = drawSlider(g2, greenSlider, padding, y, availableWidth);
            y = drawSlider(g2, blueSlider, padding, y, availableWidth);
            y = drawSlider(g2, brushSlider, padding, y, availableWidth);

            y += 8;
            drawButtons(g2, padding, y, availableWidth);

            g2.dispose();
        }

        private int drawSlider(Graphics2D g2, SliderControl slider, int padding, int y, int width) {
            int scale = 2; // pixel font scale
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

            g2.setFont(labelFont);
            g2.setColor(TEXT);
            String valueText = String.valueOf(slider.value);
            g2.drawString(slider.label + " : " + valueText, padding, trackY + 8);

            // Pixel-style track
            g2.setColor(BUTTON_BG);
            g2.fillRect(trackX, trackY, trackWidth, trackHeight);
            g2.setColor(BUTTON_BORDER);
            g2.drawRect(trackX, trackY, trackWidth, trackHeight);

            double fillRatio = slider.ratio();
            int fillWidth = (int) (trackWidth * fillRatio);
            g2.setColor(ACCENT);
            g2.fillRect(trackX, trackY, fillWidth, trackHeight);

            // Chunky pixel thumb (just a solid rect)
            Color thumbColor = new Color(
                    Math.min(255, ACCENT.getRed() + 40),
                    Math.min(255, ACCENT.getGreen() + 40),
                    Math.min(255, ACCENT.getBlue() + 40));
            g2.setColor(thumbColor);
            g2.fillRect(slider.thumb.x, slider.thumb.y, slider.thumb.width, slider.thumb.height);
            g2.setColor(ACCENT);
            g2.drawRect(slider.thumb.x, slider.thumb.y, slider.thumb.width, slider.thumb.height);

            // minus / plus buttons
            slider.minus.bounds = new Rectangle(trackX - btnWidth - gap, trackY - 6, btnWidth, 22);
            slider.plus.bounds = new Rectangle(trackX + trackWidth + gap, trackY - 6, btnWidth, 22);
            paintButton(g2, slider.minus);
            paintButton(g2, slider.plus);

            return y + 30;
        }

        private void drawButtons(Graphics2D g2, int padding, int y, int width) {
            int btnHeight = 32;
            int spacing = 6;

            modeButton.bounds = new Rectangle(padding, y, width, btnHeight);
            paintButton(g2, modeButton);
            y += btnHeight + spacing + 4;

            // Fill / Clear side-by-side
            int btnWidthFull = (width - spacing) / 2;
            ActionButton fill = buttons.get(0);
            ActionButton clear = buttons.get(1);
            fill.bounds = new Rectangle(padding, y, btnWidthFull, btnHeight);
            paintButton(g2, fill);
            clear.bounds = new Rectangle(padding + btnWidthFull + spacing, y, btnWidthFull, btnHeight);
            paintButton(g2, clear);
            y += btnHeight + spacing + 4;

            // Clear Stamp full width
            stampClearButton.bounds = new Rectangle(padding, y, width, btnHeight);
            paintButton(g2, stampClearButton);
            y += btnHeight + spacing + 4;

            // 4x2 grid: C, M, Y, Bright (minus on top, plus bottom)
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
        }

        private void paintButton(Graphics2D g2, ActionButton button) {
            Color themed = themedButtonColor(button.label, button.accent);
            Color fill = themed;
            if (button.pressed) {
                fill = BUTTON_ACTIVE;
            } else if (button.hover) {
                fill = BUTTON_HOVER;
            }
            g2.setColor(fill);
            g2.fillRect(button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height);
            g2.setColor(TEXT);
            PixelFont.draw(g2, button.label.toUpperCase(), button.bounds, 2, TEXT);
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
            for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, brushSlider)) {
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
            return new Dimension(CONTROL_BAR_WIDTH, 0);
        }

        private ActionButton findButtonAt(int x, int y) {
            if (modeButton.bounds != null && modeButton.bounds.contains(x, y)) {
                return modeButton;
            }
            if (stampClearButton.bounds != null && stampClearButton.bounds.contains(x, y)) {
                return stampClearButton;
            }
            for (SliderControl s : List.of(redSlider, greenSlider, blueSlider, brushSlider)) {
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

        private Color themedButtonColor(String label, boolean accent) {
            if (label.startsWith("C")) {
                if (label.contains("-")) {
                    return new Color(220, 90, 80); // warm opposite of cyan
                }
                return CYAN_BTN;
            }
            if (label.startsWith("M")) {
                if (label.contains("-")) {
                    return new Color(90, 180, 90); // green-ish opposite of magenta
                }
                return MAGENTA_BTN;
            }
            if (label.startsWith("Y")) {
                if (label.contains("-")) {
                    return new Color(80, 120, 200);
                }
                return YELLOW_BTN;
            }
            return accent ? ACCENT.darker() : BUTTON_BG;
        }

        private void toggleMode() {
            toolMode = (toolMode == ToolMode.BRUSH) ? ToolMode.STAMP : ToolMode.BRUSH;
            modeButton.label = (toolMode == ToolMode.BRUSH) ? "BRUSH" : "STAMP";
            repaint();
        }
    }

    /**
     * Simple console for text commands (e.g., "save art.png", "new 128").
     */
    private class Console extends JComponent {
        private final List<String> history = new java.util.ArrayList<>();
        private int historyIndex = -1;
        private String currentInput = "";
        private String status = "Commands: save <file.png> | new <size> | help";
        private boolean caretVisible = true;
        private final Timer caretTimer = new Timer(500, e -> {
            caretVisible = !caretVisible;
            repaint();
        });

        Console() {
            setOpaque(true);
            setBackground(BG);
            setPreferredSize(new Dimension(0, 70));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyTyped(java.awt.event.KeyEvent e) {
                    char ch = e.getKeyChar();
                    if (ch >= 32 && ch <= 126) {
                        currentInput += ch;
                        repaint();
                    }
                }

                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case java.awt.event.KeyEvent.VK_BACK_SPACE:
                            if (!currentInput.isEmpty()) {
                                currentInput = currentInput.substring(0, currentInput.length() - 1);
                                repaint();
                            }
                            break;
                        case java.awt.event.KeyEvent.VK_ENTER:
                            submit();
                            break;
                        case java.awt.event.KeyEvent.VK_UP:
                            recallHistory(-1);
                            break;
                        case java.awt.event.KeyEvent.VK_DOWN:
                            recallHistory(1);
                            break;
                        default:
                            break;
                    }
                }
            });
            caretTimer.start();
        }

        private void submit() {
            String text = currentInput.trim();
            if (!text.isEmpty()) {
                history.add(text);
                historyIndex = history.size();
                handleCommand(text);
            }
            currentInput = "";
            repaint();
        }

        private void recallHistory(int direction) {
            if (history.isEmpty()) {
                return;
            }
            historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
            if (historyIndex >= 0 && historyIndex < history.size()) {
                currentInput = history.get(historyIndex);
            } else {
                currentInput = "";
            }
            repaint();
        }

        void setStatus(String message) {
            status = message;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            int padding = 10;
            int lineHeight = 12;
            Rectangle statusBounds = new Rectangle(padding, padding, getWidth() - padding * 2, lineHeight + 6);
            PixelFont.drawLeft(g2, status.toUpperCase(), statusBounds, 2, MUTED_TEXT);

            String prompt = "> " + currentInput + (caretVisible ? "_" : " ");
            Rectangle inputBounds = new Rectangle(padding, padding + 22, getWidth() - padding * 2, lineHeight + 10);
            PixelFont.drawLeft(g2, prompt, inputBounds, 2, TEXT);

            g2.dispose();
        }
    }

    private static class SliderControl {
        final String label;
        final int min;
        final int max;
        int value;
        final IntConsumer onChange;
        Rectangle track;
        Rectangle thumb;
        final ActionButton minus;
        final ActionButton plus;

        SliderControl(String label, int min, int max, int value, IntConsumer onChange) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.value = value;
            this.onChange = onChange;
            this.minus = new ActionButton("-", () -> setValue(this.value - 1), false);
            this.plus = new ActionButton("+", () -> setValue(this.value + 1), false);
        }

        void setValue(int newValue) {
            int clamped = Math.max(min, Math.min(max, newValue));
            if (clamped != value) {
                value = clamped;
                onChange.accept(value);
            } else {
                onChange.accept(value);
            }
        }

        double ratio() {
            return (double) (value - min) / (double) (max - min);
        }
    }

    private static class ActionButton {
        String label;
        final Runnable action;
        final boolean accent;
        Rectangle bounds;
        boolean hover;
        boolean pressed;

        ActionButton(String label, Runnable action, boolean accent) {
            this.label = label;
            this.action = action;
            this.accent = accent;
        }
    }

    /**
     * Small swatch + brightness control sitting above the stamp.
     */
    private class TopBar extends JComponent {
        private final ActionButton plus = new ActionButton("+", () -> adjustBrushBrightnessGlobal(BRUSH_BRIGHT_STEP), false);
        private final ActionButton minus = new ActionButton("-", () -> adjustBrushBrightnessGlobal(-BRUSH_BRIGHT_STEP), false);
        private ActionButton active;
        private final Timer repeatTimer;

        TopBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(120, 90));
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
            int padding = 8;
            int swatchSize = 52;
            int btnW = 24;
            int btnH = 22;
            int gap = 8;

            int contentWidth = Math.max(swatchSize, btnW * 2 + gap);
            int contentHeight = swatchSize + gap + btnH;
            int startX = (getWidth() - contentWidth) / 2;
            int startY = (getHeight() - contentHeight) / 2;

            // Swatch (square, no rounding)
            g2.setColor(currentBrushColor());
            g2.fillRect(startX, startY, swatchSize, swatchSize);
            g2.setColor(BUTTON_BORDER);
            g2.drawRect(startX, startY, swatchSize, swatchSize);

            int btnY = startY + swatchSize + gap;
            int btnX = startX + Math.max(0, (swatchSize - (btnW * 2 + gap)) / 2);
            minus.bounds = new Rectangle(btnX, btnY, btnW, btnH);
            plus.bounds = new Rectangle(btnX + btnW + gap, btnY, btnW, btnH);

            paintTopButton(g2, plus);
            paintTopButton(g2, minus);

            g2.dispose();
        }

        private void paintTopButton(Graphics2D g2, ActionButton button) {
            Color base = button.accent ? ACCENT : BUTTON_BG;
            Color hover = button.accent ? ACCENT.brighter() : BUTTON_HOVER;
            Color pressed = button.accent ? ACCENT.darker() : BUTTON_ACTIVE;
            Color fill = base;
            if (button.pressed) {
                fill = pressed;
            } else if (button.hover) {
                fill = hover;
            }
            g2.setColor(fill);
            g2.fillRect(button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height);
            g2.setColor(TEXT);
            PixelFont.draw(g2, button.label, button.bounds, 2, TEXT);
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

    /**
     * Simple pixel grid that supports painting by click or drag.
     */
    private static class PixelCanvas extends JPanel {
        private final int columns;
        private final int rows;
        private final int cellSize;
        private final Color[][] pixels;
        private final Deque<Color[][]> undoStack = new ArrayDeque<>();
        private final int undoLimit = 30;
        private final java.util.function.Consumer<Color> pickCallback;
        private final IntConsumer brushChangeCallback;
        private final Supplier<ToolMode> modeSupplier;
        private final Supplier<Color[][]> stampSupplier;
        private Color currentColor = Color.BLACK;
        private int brushSize = 1;
        private int hoverCol = -1;
        private int hoverRow = -1;
        private boolean strokeActive = false;

        PixelCanvas(int columns, int rows, int cellSize, java.util.function.Consumer<Color> pickCallback, IntConsumer brushChangeCallback,
                    Supplier<ToolMode> modeSupplier, Supplier<Color[][]> stampSupplier) {
            this.columns = columns;
            this.rows = rows;
            this.cellSize = cellSize;
            this.pixels = new Color[rows][columns];
            this.pickCallback = pickCallback;
            this.brushChangeCallback = brushChangeCallback;
            this.modeSupplier = modeSupplier;
            this.stampSupplier = stampSupplier;
            setPreferredSize(new Dimension(columns * cellSize, rows * cellSize));
            setBackground(CANVAS_BG);
            enablePainting();
        }

        void setCurrentColor(Color color) {
            this.currentColor = color;
        }

        Color getCurrentColor() {
            return currentColor;
        }

        int getBrushSize() {
            return brushSize;
        }

        void setBrushSize(int size) {
            int newSize = Math.max(1, size);
            if (newSize != this.brushSize) {
                this.brushSize = newSize;
                if (brushChangeCallback != null) {
                    brushChangeCallback.accept(this.brushSize);
                }
                if (hoverCol >= 0 && hoverRow >= 0) {
                    repaint();
                }
            }
        }

        void clear() {
            pushUndo();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    pixels[r][c] = null;
                }
            }
            repaint();
        }

        void fill(Color color) {
            pushUndo();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    pixels[r][c] = color;
                }
            }
            repaint();
        }

        void adjustAll(UnaryOperator<Color> adjuster) {
            pushUndo();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    Color color = pixels[r][c];
                    if (color != null) {
                        pixels[r][c] = adjuster.apply(color);
                    }
                }
            }
            repaint();
        }

        void flipHorizontal() {
            pushUndo();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns / 2; c++) {
                    int mirror = columns - 1 - c;
                    Color tmp = pixels[r][c];
                    pixels[r][c] = pixels[r][mirror];
                    pixels[r][mirror] = tmp;
                }
            }
            repaint();
        }

        void flipVertical() {
            pushUndo();
            for (int c = 0; c < columns; c++) {
                for (int r = 0; r < rows / 2; r++) {
                    int mirror = rows - 1 - r;
                    Color tmp = pixels[r][c];
                    pixels[r][c] = pixels[mirror][c];
                    pixels[mirror][c] = tmp;
                }
            }
            repaint();
        }

        private void enablePainting() {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isAltDown()) {
                        pickColor(e.getX(), e.getY());
                    } else {
                        beginStroke();
                        paintAt(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    paintAt(e.getX(), e.getY());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    endStroke();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverCol = -1;
                    hoverRow = -1;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int rotation = e.getWheelRotation();
                    int delta = rotation > 0 ? -1 : 1;
                    setBrushSize(brushSize + delta);
                    if (pickCallback != null) {
                        pickCallback.accept(currentColor);
                    }
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
            addMouseWheelListener(adapter);
        }

        private void paintAt(int x, int y) {
            int column = x / cellSize;
            int row = y / cellSize;
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                return;
            }
            applyBrush(column, row);
            updateHover(x, y);
        }

        private void applyBrush(int column, int row) {
            ToolMode mode = modeSupplier != null ? modeSupplier.get() : ToolMode.BRUSH;
            if (mode == ToolMode.STAMP && stampSupplier != null) {
                applyStamp(column, row);
                return;
            }

            int half = brushSize / 2;
            int startCol = column - half;
            int startRow = row - half;
            int endCol = startCol + brushSize - 1;
            int endRow = startRow + brushSize - 1;

            startCol = Math.max(0, startCol);
            startRow = Math.max(0, startRow);
            endCol = Math.min(columns - 1, endCol);
            endRow = Math.min(rows - 1, endRow);

            for (int r = startRow; r <= endRow; r++) {
                for (int c = startCol; c <= endCol; c++) {
                    pixels[r][c] = currentColor;
                }
            }

            int x = startCol * cellSize;
            int y = startRow * cellSize;
            int w = (endCol - startCol + 1) * cellSize;
            int h = (endRow - startRow + 1) * cellSize;
            repaint(new Rectangle(x, y, w, h));
        }

        private void applyStamp(int column, int row) {
            Color[][] stamp = stampSupplier.get();
            if (stamp == null) {
                return;
            }
            int stampRows = stamp.length;
            int stampCols = stamp[0].length;
            int startCol = column - stampCols / 2;
            int startRow = row - stampRows / 2;
            int endCol = Math.min(columns - 1, startCol + stampCols - 1);
            int endRow = Math.min(rows - 1, startRow + stampRows - 1);

            if (startCol < 0) startCol = 0;
            if (startRow < 0) startRow = 0;

            for (int r = startRow; r <= endRow; r++) {
                for (int c = startCol; c <= endCol; c++) {
                    Color s = stamp[r - startRow][c - startCol];
                    if (s != null) {
                        pixels[r][c] = s;
                    }
                }
            }
            int x = startCol * cellSize;
            int y = startRow * cellSize;
            int w = (endCol - startCol + 1) * cellSize;
            int h = (endRow - startRow + 1) * cellSize;
            repaint(new Rectangle(x, y, w, h));
        }

        private void updateHover(int x, int y) {
            int column = x / cellSize;
            int row = y / cellSize;
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                if (hoverCol != -1 || hoverRow != -1) {
                    hoverCol = -1;
                    hoverRow = -1;
                    repaint();
                }
                return;
            }
            if (column != hoverCol || row != hoverRow) {
                hoverCol = column;
                hoverRow = row;
                repaint();
            }
        }

        private void pickColor(int x, int y) {
            int column = x / cellSize;
            int row = y / cellSize;
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                return;
            }
            Color color = pixels[row][column];
            if (color == null) {
                color = CANVAS_BG;
            }
            if (pickCallback != null) {
                pickCallback.accept(color);
            }
        }

        private void beginStroke() {
            if (!strokeActive) {
                pushUndo();
                strokeActive = true;
            }
        }

        private void endStroke() {
            strokeActive = false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    int x = c * cellSize;
                    int y = r * cellSize;
                    Color color = pixels[r][c];
                    if (color != null) {
                        g2.setColor(color);
                        g2.fillRect(x, y, cellSize, cellSize);
                    }
                }
            }

            if (hoverCol >= 0 && hoverRow >= 0) {
                if (isStampMode()) {
                    Color[][] stamp = stampSupplier.get();
                    if (stamp != null) {
                        int stampRows = stamp.length;
                        int stampCols = stamp[0].length;
                        int startCol = hoverCol - stampCols / 2;
                        int startRow = hoverRow - stampRows / 2;
                        int endCol = Math.min(columns - 1, startCol + stampCols - 1);
                        int endRow = Math.min(rows - 1, startRow + stampRows - 1);
                        if (startCol < 0) startCol = 0;
                        if (startRow < 0) startRow = 0;

                        for (int r = startRow; r <= endRow; r++) {
                            for (int c = startCol; c <= endCol; c++) {
                                Color s = stamp[r - startRow][c - startCol];
                                if (s != null) {
                                    g2.setColor(new Color(s.getRed(), s.getGreen(), s.getBlue(), 120));
                                    g2.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
                                }
                            }
                        }
                        int x = startCol * cellSize;
                        int y = startRow * cellSize;
                        int w = (endCol - startCol + 1) * cellSize;
                        int h = (endRow - startRow + 1) * cellSize;
                        g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 180));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRect(x, y, w - 1, h - 1);
                    }
                } else {
                    int half = brushSize / 2;
                    int startCol = Math.max(0, hoverCol - half);
                    int startRow = Math.max(0, hoverRow - half);
                    int endCol = Math.min(columns - 1, startCol + brushSize - 1);
                    int endRow = Math.min(rows - 1, startRow + brushSize - 1);

                    int x = startCol * cellSize;
                    int y = startRow * cellSize;
                    int w = (endCol - startCol + 1) * cellSize;
                    int h = (endRow - startRow + 1) * cellSize;

                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 140));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x, y, w - 1, h - 1);
                }
            }

            g2.dispose();
        }

        private boolean isStampMode() {
            return modeSupplier != null && modeSupplier.get() == ToolMode.STAMP;
        }

        BufferedImage toImage() {
            BufferedImage img = new BufferedImage(columns, rows, BufferedImage.TYPE_INT_ARGB);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    Color color = pixels[r][c];
                    if (color == null) {
                        color = CANVAS_BG;
                    }
                    img.setRGB(c, r, color.getRGB());
                }
            }
            return img;
        }

        Color[][] getPixelsCopy() {
            Color[][] copy = new Color[rows][columns];
            for (int r = 0; r < rows; r++) {
                copy[r] = Arrays.copyOf(pixels[r], columns);
            }
            return copy;
        }

        void undo() {
            if (undoStack.isEmpty()) {
                return;
            }
            Color[][] prev = undoStack.pop();
            for (int r = 0; r < rows; r++) {
                System.arraycopy(prev[r], 0, pixels[r], 0, columns);
            }
            repaint();
        }

        private void pushUndo() {
            Color[][] snapshot = new Color[rows][columns];
            for (int r = 0; r < rows; r++) {
                snapshot[r] = Arrays.copyOf(pixels[r], columns);
            }
            undoStack.push(snapshot);
            while (undoStack.size() > undoLimit) {
                undoStack.removeLast();
            }
        }
    }

    private static Color adjustChannel(Color color, int redDelta, int greenDelta, int blueDelta) {
        int r = clamp(color.getRed() + redDelta);
        int g = clamp(color.getGreen() + greenDelta);
        int b = clamp(color.getBlue() + blueDelta);
        return new Color(r, g, b);
    }

    private static Color adjustBrightness(Color color, int delta) {
        int r = clamp(color.getRed() + delta);
        int g = clamp(color.getGreen() + delta);
        int b = clamp(color.getBlue() + delta);
        return new Color(r, g, b);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void handleCommand(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        String[] parts = input.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "save":
                if (parts.length < 2) {
                    console.setStatus("Usage: save <file.png>");
                    return;
                }
                String path = parts[1];
                try {
                    saveImage(path);
                    console.setStatus("Saved to " + path);
                } catch (IOException ex) {
                    console.setStatus("Save failed: " + ex.getMessage());
                }
                break;
            case "new":
                if (parts.length < 2) {
                    console.setStatus("Usage: new <size>");
                    return;
                }
                try {
                    int size = Integer.parseInt(parts[1]);
                    if (size <= 0) throw new NumberFormatException();
                    rebuildCanvas(size);
                    console.setStatus("Created new " + size + "x" + size + " canvas");
                } catch (NumberFormatException ex) {
                    console.setStatus("Size must be a positive number");
                }
                break;
            case "flip":
                if (parts.length < 2) {
                    console.setStatus("Usage: flip h|v");
                    return;
                }
                if ("h".equalsIgnoreCase(parts[1])) {
                    canvas.flipHorizontal();
                    console.setStatus("Flipped horizontally");
                } else if ("v".equalsIgnoreCase(parts[1])) {
                    canvas.flipVertical();
                    console.setStatus("Flipped vertically");
                } else {
                    console.setStatus("Usage: flip h|v");
                }
                break;
            case "help":
                console.setStatus("Commands: save <file.png> | new <size> | flip h | flip v");
                break;
            default:
                console.setStatus("Unknown command. Try: save <file.png> | new <size> | flip h | flip v");
        }
    }

    private void saveImage(String path) throws IOException {
        BufferedImage img = canvas.toImage();
        File file = new File(path);
        String format = "png";
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            format = path.substring(dot + 1);
        }
        ImageIO.write(img, format, file);
    }

    /**
     * Minimal 5x7 bitmap font renderer for a retro look.
     */
    private static class PixelFont {
        private static final Map<Character, boolean[][]> glyphs = new HashMap<>();

        static {
            add('0', "11111,10001,10011,10101,11001,10001,11111");
            add('1', "00100,01100,00100,00100,00100,00100,01110");
            add('2', "11110,00001,00001,11110,10000,10000,11111");
            add('3', "11110,00001,00001,01110,00001,00001,11110");
            add('4', "10010,10010,10010,11111,00010,00010,00010");
            add('5', "11111,10000,10000,11110,00001,00001,11110");
            add('6', "01110,10000,10000,11110,10001,10001,01110");
            add('7', "11111,00001,00010,00100,01000,01000,01000");
            add('8', "01110,10001,10001,01110,10001,10001,01110");
            add('9', "01110,10001,10001,01111,00001,00001,01110");
            add('A', "01110,10001,10001,11111,10001,10001,10001");
            add('B', "11110,10001,10001,11110,10001,10001,11110");
            add('C', "01110,10001,10000,10000,10000,10001,01110");
            add('D', "11110,10001,10001,10001,10001,10001,11110");
            add('E', "11111,10000,10000,11110,10000,10000,11111");
            add('F', "11111,10000,10000,11110,10000,10000,10000");
            add('G', "01110,10001,10000,10111,10001,10001,01111");
            add('H', "10001,10001,10001,11111,10001,10001,10001");
            add('I', "01110,00100,00100,00100,00100,00100,01110");
            add('J', "00001,00001,00001,00001,10001,10001,01110");
            add('K', "10001,10010,10100,11000,10100,10010,10001");
            add('L', "10000,10000,10000,10000,10000,10000,11111");
            add('M', "10001,11011,10101,10101,10001,10001,10001");
            add('N', "10001,10001,11001,10101,10011,10001,10001");
            add('O', "01110,10001,10001,10001,10001,10001,01110");
            add('P', "11110,10001,10001,11110,10000,10000,10000");
            add('Q', "01110,10001,10001,10001,10101,10010,01101");
            add('R', "11110,10001,10001,11110,10100,10010,10001");
            add('S', "01111,10000,10000,01110,00001,00001,11110");
            add('T', "11111,00100,00100,00100,00100,00100,00100");
            add('U', "10001,10001,10001,10001,10001,10001,01110");
            add('V', "10001,10001,10001,10001,10001,01010,00100");
            add('W', "10001,10001,10001,10101,10101,11011,10001");
            add('X', "10001,10001,01010,00100,01010,10001,10001");
            add('Y', "10001,10001,01010,00100,00100,00100,00100");
            add('Z', "11111,00001,00010,00100,01000,10000,11111");
            add(' ', "00000,00000,00000,00000,00000,00000,00000");
            add('>', "00100,00010,00001,00001,00010,00100,00000");
            add('<', "00100,01000,10000,10000,01000,00100,00000");
            add('-', "00000,00000,00000,11111,00000,00000,00000");
            add('_', "00000,00000,00000,00000,00000,00000,11111");
            add('.', "00000,00000,00000,00000,00000,01100,01100");
            add(',', "00000,00000,00000,00000,00000,01100,01000");
            add(':', "00000,01100,01100,00000,01100,01100,00000");
            add('/', "00001,00010,00100,00100,01000,10000,10000");
            add('!', "00100,00100,00100,00100,00100,00000,00100");
            add('?', "01110,10001,00010,00100,00100,00000,00100");
            add('\'', "00100,00100,00000,00000,00000,00000,00000");
            add('"', "01010,01010,00000,00000,00000,00000,00000");
            add('+', "00100,00100,11111,00100,00100,00000,00000");
        }

        private static void add(char ch, String rows) {
            String[] parts = rows.split(",");
            boolean[][] grid = new boolean[7][5];
            for (int r = 0; r < Math.min(parts.length, 7); r++) {
                String row = parts[r];
                for (int c = 0; c < Math.min(row.length(), 5); c++) {
                    grid[r][c] = row.charAt(c) == '1';
                }
            }
            glyphs.put(ch, grid);
        }

        static void draw(Graphics2D g2, String text, Rectangle bounds, int scale, Color color) {
            g2.setColor(color);
            String upper = text.toUpperCase();
            int charWidth = 5 * scale;
            int charHeight = 7 * scale;
            int spacing = scale;
            int totalWidth = 0;
            for (int i = 0; i < upper.length(); i++) {
                char ch = upper.charAt(i);
                if (!glyphs.containsKey(ch)) {
                    ch = ' ';
                }
                totalWidth += charWidth + spacing;
            }
            if (upper.length() > 0) {
                totalWidth -= spacing;
            }
            int startX = bounds.x + Math.max(0, (bounds.width - totalWidth) / 2);
            int startY = bounds.y + Math.max(0, (bounds.height - charHeight) / 2);

            int x = startX;
            for (int i = 0; i < upper.length(); i++) {
                char ch = upper.charAt(i);
                boolean[][] grid = glyphs.getOrDefault(ch, glyphs.get(' '));
                for (int r = 0; r < 7; r++) {
                    for (int c = 0; c < 5; c++) {
                        if (grid[r][c]) {
                            int px = x + c * scale;
                            int py = startY + r * scale;
                            g2.fillRect(px, py, scale, scale);
                        }
                    }
                }
                x += charWidth + spacing;
            }
        }

        static void drawLeft(Graphics2D g2, String text, Rectangle bounds, int scale, Color color) {
            g2.setColor(color);
            String upper = text.toUpperCase();
            int charWidth = 5 * scale;
            int charHeight = 7 * scale;
            int spacing = scale;

            int startX = bounds.x;
            int startY = bounds.y + Math.max(0, (bounds.height - charHeight) / 2);

            int x = startX;
            for (int i = 0; i < upper.length(); i++) {
                char ch = upper.charAt(i);
                boolean[][] grid = glyphs.getOrDefault(ch, glyphs.get(' '));
                for (int r = 0; r < 7; r++) {
                    for (int c = 0; c < 5; c++) {
                        if (grid[r][c]) {
                            int px = x + c * scale;
                            int py = startY + r * scale;
                            g2.fillRect(px, py, scale, scale);
                        }
                    }
                }
                x += charWidth + spacing;
            }
        }
    }
}
