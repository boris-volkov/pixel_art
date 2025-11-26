import java.awt.Rectangle;

class ActionButton {
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
