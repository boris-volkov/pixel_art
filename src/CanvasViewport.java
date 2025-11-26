import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.LayoutManager;

class CanvasViewport extends JPanel {
    private PixelCanvas canvas;
    private int offsetX = 0;
    private int offsetY = 0;

    CanvasViewport(PixelCanvas canvas) {
        this.canvas = canvas;
        setLayout(null);
        add(canvas);
        setFocusable(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                requestFocusInWindow();
            }
        });
    }

    void setCanvas(PixelCanvas newCanvas) {
        remove(canvas);
        canvas = newCanvas;
        add(canvas);
        offsetX = 0;
        offsetY = 0;
        refreshLayout();
    }

    void refreshLayout() {
        Dimension size = getSize();
        Dimension pref = canvas.getPreferredSize();
        int cw = pref.width;
        int ch = pref.height;
        int x = (size.width - cw) / 2 + offsetX;
        int y = (size.height - ch) / 2 + offsetY;
        canvas.setBounds(x, y, cw, ch);
        revalidate();
        repaint();
    }

    void pan(int dx, int dy) {
        offsetX += dx;
        offsetY += dy;
        refreshLayout();
    }

    void recenter() {
        offsetX = 0;
        offsetY = 0;
        refreshLayout();
    }

    @Override
    public void setLayout(LayoutManager mgr) {
        // force null layout for manual positioning
        super.setLayout(null);
    }

    @Override
    public Dimension getPreferredSize() {
        return canvas != null ? canvas.getPreferredSize() : super.getPreferredSize();
    }

    @Override
    public void doLayout() {
        refreshLayout();
    }
}
