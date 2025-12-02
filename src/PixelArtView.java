import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;


public interface PixelArtView {
    // Lifecycle
    void initialize();

    void start();

    void dispose();

    // Canvas operations
    void setCanvasCellSize(int size);

    int getCanvasCellSize();

    void setViewportBackground(Color color);

    void panViewport(int dx, int dy);

    void recenterViewport();

    // UI updates
    void updateBrushTargets(Color color);

    void repaintCanvas();

    void repaintControls();

    void setConsoleStatus(String status);

    void showAnimationPanel(boolean visible);

    // Callbacks setup
    void setCanvasPickCallback(Consumer<Color> callback);

    void setBrushSizeCallback(IntConsumer callback);

    void setToolModeCallback(Supplier<ToolMode> callback);

    void setStampCallback(Supplier<Color[][]> callback);

    void setOnionCallback(Supplier<Color[][][]> callback);

    void setActiveLayerCallback(IntSupplier callback);

    void setLayerVisibleCallback(IntPredicate callback);

    void setPanBlockCallback(Supplier<Boolean> callback);

    void setUndoCallback(Runnable callback);
    void setRedoCallback(Runnable callback);
    void setFrameStepCallback(IntConsumer callback);
    void setToggleOnionCallback(Runnable callback);

    // Controllers
    void setCanvasController(Object canvasController); // PixelCanvas

    void setControlBarController(Object controlBarController); // ControlBar

    void setTopBarController(Object topBarController); // TopBar

    void setStampController(Object stampController); // StampPanel

    void setConsoleController(Object consoleController); // ConsolePanel

    void setAnimationController(Object animationController); // AnimationPanel

    // Image operations
    BufferedImage getCanvasImage();

    BufferedImage getCompositeImage(Color[][][] layerData);

    // Dialogs
    void showErrorDialog(String message);

    void showInfoDialog(String message);

    boolean showConfirmDialog(String message);
}
