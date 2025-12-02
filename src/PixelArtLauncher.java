import javax.swing.SwingUtilities;

public class PixelArtLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PixelArtModel model = new PixelArtModel();
            SwingPixelArtView view = new SwingPixelArtView();
            new PixelArtController(model, view); // controller wires the canvas into the view
            view.start();
        });
    }
}
