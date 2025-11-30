import java.awt.Rectangle;
import java.util.function.IntConsumer;

class SliderControl {
    final String label;
    final int min;
    int max;
    int value;
    private int step = 1;
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
        this.minus = new ActionButton("-", () -> setValue(this.value - step), false);
        this.plus = new ActionButton("+", () -> setValue(this.value + step), false);
    }

    void setMax(int newMax) {
        this.max = Math.max(min + 1, newMax);
        if (value > max) {
            setValue(max);
        }
    }

    void setStep(int step) {
        this.step = Math.max(1, step);
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

    void setValueSilently(int newValue) {
        int clamped = Math.max(min, Math.min(max, newValue));
        value = clamped;
    }

    double ratio() {
        int range = max - min;
        if (range <= 0) return 0;
        return (double) (value - min) / (double) range;
    }
}
