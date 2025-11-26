import java.awt.Rectangle;
import java.util.function.IntConsumer;

class SliderControl {
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
