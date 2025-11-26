import javax.swing.JComponent;
import java.awt.LayoutManager;

class FocusWrap extends JComponent {
    FocusWrap(JComponent inner) {
        setLayout(new java.awt.BorderLayout());
        add(inner, java.awt.BorderLayout.CENTER);
        setFocusable(true);
        setOpaque(false);
        inner.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                requestFocusInWindow();
            }
        });
    }

    @Override
    public void setLayout(LayoutManager mgr) {
        super.setLayout(new java.awt.BorderLayout());
    }
}
