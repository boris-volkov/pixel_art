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
    private PixelArtModel model;

    public PixelArtFileHandler(PixelArtModel model) {
        this.model = model;
    }

    public void loadImage(String path, PixelArtController controller) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null)
            throw new IOException("Unsupported image");
        int w = img.getWidth();
        int h = img.getHeight();
        if (w != h) {
            throw new IOException("Image must be square");
        }
        controller.rebuildCanvas(w, h);
        Color[][][] layers = model.getLayers();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                Color c = new Color(argb, true);
                layers[0][y][x] = c;
            }
        }
        // persist into frame data so applyAllCurrentFrames won't wipe the pixels
        model.saveCurrentFrames();
        controller.applyAllCurrentFrames();
    }

    public void saveImage(String path) throws IOException {
        BufferedImage img = new BufferedImage(model.getColumns(), model.getRows(), BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < model.getRows(); r++) {
            for (int c = 0; c < model.getColumns(); c++) {
                Color color = null;
                for (int l = model.getLayerCount() - 1; l >= 0; l--) {
                    Color[][] layer = model.getLayers()[l];
                    Color cc = layer[r][c];
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
        File file = new File(path);
        String format = "png";
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            format = path.substring(dot + 1);
        }
        ImageIO.write(img, format, file);
    }

    public void saveSequence(String basePath) throws IOException {
        model.saveCurrentFrames();
        int layerCount = model.getLayerCount();
        int maxFrames = 0;
        for (List<PixelArtModel.FrameData> lf : model.getLayerFrames()) {
            if (lf != null) {
                maxFrames = Math.max(maxFrames, lf.size());
            }
        }
        if (maxFrames <= 0) {
            throw new IOException("No frames to save");
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
        int digits = Math.max(3, String.valueOf(maxFrames).length());
        for (int i = 0; i < maxFrames; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<PixelArtModel.FrameData> lf = model.getLayerFrames()[l];
                if (lf.isEmpty())
                    continue;
                PixelArtModel.FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            BufferedImage img = toImage(snapshot);
            String idx = String.format("%0" + digits + "d", i + 1);
            String outName = new File(outDir, prefixFileName(dirName, idx, format)).getPath();
            ImageIO.write(img, format, new File(outName));
        }
    }

    public void saveGif(String path, int frameRate) throws IOException {
        model.saveCurrentFrames();
        int layerCount = model.getLayerCount();
        int lcm = 1;
        for (List<PixelArtModel.FrameData> lf : model.getLayerFrames()) {
            int len = lf.size();
            if (len > 0) {
                lcm = lcm(lcm, len);
            }
        }
        if (lcm <= 0) {
            throw new IOException("No frames to save");
        }
        int delayCs = Math.max(1, (int) Math.round(100.0 / Math.max(1, frameRate)));
        List<BufferedImage> framesOut = new ArrayList<>();
        for (int i = 0; i < lcm; i++) {
            Color[][][] snapshot = new Color[layerCount][][];
            for (int l = 0; l < layerCount; l++) {
                List<PixelArtModel.FrameData> lf = model.getLayerFrames()[l];
                if (lf.isEmpty())
                    continue;
                PixelArtModel.FrameData fd = lf.get(i % lf.size());
                snapshot[l] = fd.layer;
            }
            framesOut.add(toImage(snapshot));
        }
        writeGif(framesOut, delayCs, path);
    }

    public void saveProject(String path) throws IOException {
        model.saveCurrentFrames();
        PixelArtModel.ProjectData data = model.toProjectData();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(data);
        }
    }

    public void loadProject(String path, PixelArtController controller) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            PixelArtModel.ProjectData data = (PixelArtModel.ProjectData) ois.readObject();
            model.fromProjectData(data);
            controller.refreshViewFromModel();
            controller.applyAllCurrentFrames();
        }
    }

    private BufferedImage toImage(Color[][][] layerData) {
        int rows = model.getRows();
        int cols = model.getColumns();
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
