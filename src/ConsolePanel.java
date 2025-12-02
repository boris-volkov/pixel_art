import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import PixelConstants;

class ConsolePanel extends JComponent {
    private final Consumer<String> commandHandler;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String currentInput = "";
    private String status = "Commands: save <file.png> | new <size> | help";
    private boolean caretVisible = true;
    private final Timer caretTimer = new Timer(500, e -> {
        caretVisible = !caretVisible;
        repaint();
    });
    private boolean hasFocus = false;
    private final PropertyChangeListener focusListener = evt -> {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean focused = owner != null && isAncestorOf(owner);
        if (focused != hasFocus) {
            hasFocus = focused;
            caretVisible = hasFocus;
            repaint();
        }
    };

    ConsolePanel(Consumer<String> commandHandler) {
        this.commandHandler = commandHandler;
        setOpaque(true);
        setBackground(PixelConstants.BG);
        setPreferredSize(new java.awt.Dimension(0, 70));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setFocusable(true);
                requestFocusInWindow();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                hasFocus = true;
                caretVisible = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                hasFocus = false;
                caretVisible = false;
                repaint();
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                if (!hasFocus) return;
                char ch = e.getKeyChar();
                if (ch >= 32 && ch <= 126) {
                    currentInput += ch;
                    repaint();
                }
            }

            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (!hasFocus) return;
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
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusListener);
    }

    void setStatus(String message) {
        status = message;
        repaint();
    }

    private void submit() {
        String text = currentInput.trim();
        if (!text.isEmpty()) {
            history.add(text);
            historyIndex = history.size();
            commandHandler.accept(text);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PixelConstants.BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int padding = 10;
        int lineHeight = 12;
        Rectangle statusBounds = new Rectangle(padding, padding, getWidth() - padding * 2, lineHeight + 6);
        PixelFont.drawLeft(g2, status.toUpperCase(), statusBounds, 2, PixelConstants.MUTED_TEXT);

        String prompt = "> " + currentInput + (caretVisible ? "_" : " ");
        Rectangle inputBounds = new Rectangle(padding, padding + 22, getWidth() - padding * 2, lineHeight + 10);
        PixelFont.drawLeft(g2, prompt, inputBounds, 2, PixelConstants.TEXT);

        if (hasFocus) {
            g2.setColor(new Color(255, 170, 60));
            g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
        }

        g2.dispose();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusListener);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusListener);
        super.removeNotify();
    }

    boolean isFocused() {
        return hasFocus;
    }
}
