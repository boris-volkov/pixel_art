import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public class PixelArtFileHandler {
    private PixelArtApp app;

    public PixelArtFileHandler(PixelArtApp app) {
        this.app = app;
    }

    public void loadImage(String path) throws IOException {
        app.resetAnimationState();
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null)
            throw new IOException("Unsupported image");
        int w = img.getWidth();
        int h = img.getHeight();
        if (w != h) {
            throw new IOException("Image must be square");
        }
        app.gridSize = w;
        app.canvasCellSize = Math.min(PixelArtApp.MAX_CELL_SIZE, Math.max(2, app.canvasCellSize));
        PixelCanvas newCanvas = new PixelCanvas(w, h, app.canvasCellSize, app::pickBrushColor, app::setBrushSize,
                app::getToolMode, app::getStampPixels, app::getOnionComposite, app::getActiveLayer, 3,
                app::isLayerVisible, null, false, () -> app.recordUndo(PixelArtApp.CanvasTarget.MAIN));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                Color c = new Color(argb, true);
                newCanvas.setPixelDirect(y, x, c);
            }
        }
        newCanvas.setCurrentColor(app.currentBrushColor());
        newCanvas.setBrushSize(app.brushSize);
        app.canvas = newCanvas;
        app.canvasHolder.setCanvas(newCanvas);
        app.setCanvasCellSize(app.computeMaxCellSizeForScreen());
        app.syncHSBFromRGB();
        if (app.controlBar != null)
            app.controlBar.syncSliders();
    }

    public void saveImage(String path) throws IOException {
        BufferedImage img = app.canvas.toImage();
        File file = new File(path);
        String format = "png";
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            format = path.substring(dot + 1);
        }
        ImageIO.write(img, format, file);
    }

    public void saveSequence(String basePath) throws IOException {
        app.ensureFrameCapacity();
        int layerCount = app.canvas.getLayerCount();
        int maxFrames = 0;
        for (List<PixelArtApp.FrameData> lf : app.layerFrames) {
            if (lf != null) {
                maxFrames = Math.max(maxFrames, lf.size());
            }
        }
        if (maxFrames <= 0) {
            app.console.setStatus("No frames to save");
            return;
        }
        String format = "png";
        int dot = basePath.lastIndexOf('.');
        String prefix = basePath;
        if (dot > 0 && dot < basePath.length() - 1) {
            format = basePath.substring(dot + 1);
            prefix = basePath.substring(0, dot);
        }
        File baseFile = new File(prefix);
        File parentDir = baseFile.getParentFile();
        if (parentDir == null)
            parentDir = new File(".");
        String dirName = baseFile.getName();
        File outDir = new File(parentDir, dirName);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create directory " + outDir.getAbsolutePath());
        }
        saveCurrentFrames();
        int digits = Math.max(3, String.valueOf(maxFrames).length());
        for (int i = 0; i < maxFrames; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<PixelArtApp.FrameData> lf = app.layerFrames[l];
                if (lf.isEmpty())
                    continue;
                PixelArtApp.FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            BufferedImage img = toImage(snapshot);
            String idx = String.format("%0" + digits + "d", i + 1);
            String outName = new File(outDir, prefixFileName(dirName, idx, format)).getPath();
            ImageIO.write(img, format, new File(outName));
        }
        applyAllCurrentFrames();
    }

    public void saveGif(String path) throws IOException {
        app.ensureFrameCapacity();
        int layerCount = app.canvas.getLayerCount();
        int lcm = 1;
        for (List<PixelArtApp.FrameData> lf : app.layerFrames) {
            int len = lf.size();
            if (len > 0) {
                lcm = lcm(lcm, len);
            }
        }
        if (lcm <= 0) {
            app.console.setStatus("No frames to save");
            return;
        }
        saveCurrentFrames();
        int delayCs = Math.max(1, (int) Math.round(100.0 / Math.max(1, app.frameRate)));
        List<BufferedImage> framesOut = new ArrayList<>();
        for (int i = 0; i < lcm; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<PixelArtApp.FrameData> lf = app.layerFrames[l];
                if (lf.isEmpty())
                    continue;
                PixelArtApp.FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            framesOut.add(toImage(snapshot));
        }
        writeGif(framesOut, delayCs, path);
        applyAllCurrentFrames();
    }

    public void saveProject(String path) throws IOException {
        app.ensureFrameCapacity();
        PixelArtApp.ProjectData data = new PixelArtApp.ProjectData();
        data.cols = app.canvas.getColumns();
        data.rows = app.canvas.getRows();
        data.cellSize = app.canvasCellSize;
        data.layerNames = app.layerNames.clone();
        data.layerVisible = app.layerVisible.clone();
        data.animatedLayers = app.animatedLayers.clone();
        data.currentFrameIndex = app.currentFrameIndex.clone();
        data.activeLayer = app.activeLayer;
        data.brushSize = app.brushSize;
        data.red = app.colorState.getRed();
        data.green = app.colorState.getGreen();
        data.blue = app.colorState.getBlue();
        data.frameRate = app.frameRate;
        data.viewportBg = app.viewportBg;
        data.layerFrames = new ArrayList<>();
        for (List<PixelArtApp.FrameData> lf : app.layerFrames) {
            List<Color[][]> saved = new ArrayList<>();
            for (PixelArtApp.FrameData fd : lf) {
                saved.add(cloneLayer(fd.layer));
            }
            data.layerFrames.add(saved);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(data);
        }
    }

    public void loadProject(String path) throws IOException, ClassNotFoundException {
        if (app.playTimer != null)
            app.playTimer.stop();
        app.playing = false;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            PixelArtApp.ProjectData data = (PixelArtApp.ProjectData) ois.readObject();
            app.gridSize = Math.max(data.cols, data.rows);
            app.canvasCellSize = Math.min(PixelArtApp.MAX_CELL_SIZE, Math.max(2, data.cellSize));
            PixelCanvas newCanvas = new PixelCanvas(data.cols, data.rows, app.canvasCellSize, app::pickBrushColor,
                    app::setBrushSize, app::getToolMode, app::getStampPixels, app::getOnionComposite,
                    app::getActiveLayer, data.layerFrames.size(), app::isLayerVisible, null, false,
                    () -> app.recordUndo(PixelArtApp.CanvasTarget.MAIN));
            app.canvas = newCanvas;
            app.canvasHolder.setCanvas(newCanvas);
            app.viewportBg = data.viewportBg != null ? data.viewportBg : PixelArtApp.BG;
            app.canvasHolder.setBackground(app.viewportBg);
            app.ensureLayerNamesSize(data.layerNames.length);
            System.arraycopy(data.layerNames, 0, app.layerNames, 0,
                    Math.min(app.layerNames.length, data.layerNames.length));
            for (int i = 0; i < Math.min(app.layerVisible.length, data.layerVisible.length); i++) {
                app.layerVisible[i] = data.layerVisible[i];
            }
            app.animatedLayers = Arrays.copyOf(data.animatedLayers, data.layerFrames.size());
            app.currentFrameIndex = Arrays.copyOf(data.currentFrameIndex, data.layerFrames.size());
            app.brushSize = data.brushSize;
            app.colorState.setFromColor(new Color(data.red, data.green, data.blue));
            app.updateBrushTargets(app.colorState.getColor());
            app.frameRate = data.frameRate;
            app.activeLayer = Math.max(0, Math.min(data.activeLayer, data.layerFrames.size() - 1));
            app.initLayerFrames(data.layerFrames.size());
            for (int l = 0; l < data.layerFrames.size(); l++) {
                List<Color[][]> saved = data.layerFrames.get(l);
                List<PixelArtApp.FrameData> dest = app.layerFrames[l];
                dest.clear();
                for (Color[][] layer : saved) {
                    dest.add(new PixelArtApp.FrameData(cloneLayer(layer)));
                }
                if (dest.isEmpty()) {
                    dest.add(app.createEmptyFrameForLayer(l));
                }
            }
            app.applyAllCurrentFrames();
            app.updateBrushTargets(app.currentBrushColor());
            app.canvas.setBrushSize(app.brushSize);
            app.syncHSBFromRGB();
            if (app.controlBar != null)
                app.controlBar.syncSliders();
            if (app.timeline != null)
                app.timeline.repaint();
            if (app.topBar != null)
                app.topBar.repaint();
        }
    }

    private BufferedImage toImage(Color[][][] layerData) {
        int rows = app.canvas.getRows();
        int cols = app.canvas.getColumns();
        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color color = null;
                for (int l = layerData.length - 1; l >= 0; l--) {
                    if (layerData[l] == null)
                        continue;
                    Color cc = layerData[l][r][c];
                    if (cc != null) {
                        color = cc;
                        break;
                    }
                }
                if (color == null) {
                    img.setRGB(c, r, 0x00000000);
                } else {
                    img.setRGB(c, r, color.getRGB());
                }
            }
        }
        return img;
    }

    private void writeGif(List<BufferedImage> framesOut, int delayCs, String path) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").hasNext()
                ? ImageIO.getImageWritersBySuffix("gif").next()
                : null;
        if (writer == null)
            throw new IOException("No GIF writer available");
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File(path))) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            ImageWriteParam param = writer.getDefaultWriteParam();
            for (int i = 0; i < framesOut.size(); i++) {
                BufferedImage bi = framesOut.get(i);
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, param);
                String metaFormat = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);
                IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                gce.setAttribute("transparentColorIndex", "0");

                if (i == 0) {
                    IIOMetadataNode aes = getNode(root, "ApplicationExtensions");
                    IIOMetadataNode ae = new IIOMetadataNode("ApplicationExtension");
                    ae.setAttribute("applicationID", "NETSCAPE");
                    ae.setAttribute("authenticationCode", "2.0");
                    byte[] loop = new byte[] { 1, 0, 0 };
                    ae.setUserObject(loop);
                    aes.appendChild(ae);
                }

                metadata.setFromTree(metaFormat, root);
                writer.writeToSequence(new javax.imageio.IIOImage(bi, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }

    private String prefixFileName(String base, String idx, String format) {
        return base + idx + "." + format;
    }

    private void saveCurrentFrames() {
        if (app.layerFrames == null || app.currentFrameIndex == null)
            return;
        int layers = Math.min(app.canvas.getLayerCount(), app.layerFrames.length);
        for (int l = 0; l < layers; l++) {
            List<PixelArtApp.FrameData> frames = app.layerFrames[l];
            if (frames.isEmpty())
                continue;
            int idx = Math.max(0, Math.min(app.currentFrameIndex[l], frames.size() - 1));
            frames.set(idx, app.captureFrameForLayer(l));
        }
    }

    private void applyAllCurrentFrames() {
        for (int l = 0; l < app.layerFrames.length; l++) {
            List<PixelArtApp.FrameData> frames = app.layerFrames[l];
            if (frames.isEmpty())
                continue;
            int idx = Math.max(0, Math.min(app.currentFrameIndex[l], frames.size() - 1));
            PixelArtApp.FrameData fd = frames.get(idx);
            app.applyFrameForLayer(l, fd);
        }
    }

    private Color[][] cloneLayer(Color[][] src) {
        if (src == null)
            return null;
        Color[][] copy = new Color[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = Arrays.copyOf(src[r], src[r].length);
        }
        return copy;
    }

    private long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return Math.max(1, a);
    }

    private int lcm(int a, int b) {
        long g = gcd(a, b);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, (a / g) * (long) b));
    }
}
