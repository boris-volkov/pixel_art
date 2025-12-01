import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PixelArtAnimationHandler {
    private PixelArtApp app;

    public PixelArtAnimationHandler(PixelArtApp app) {
        this.app = app;
    }

    public void addFrameFromCurrent() {
        app.ensureFrameCapacity();
        saveCurrentFrames();
        int layer = app.activeLayer;
        PixelArtApp.FrameData data = app.captureFrameForLayer(layer);
        List<PixelArtApp.FrameData> frames = app.layerFrames[layer];
        frames.add(data);
        app.currentFrameIndex[layer] = frames.size() - 1;
        app.applyFrameForLayer(layer, data);
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void addBlankFrame() {
        app.ensureFrameCapacity();
        saveCurrentFrames();
        int layer = app.activeLayer;
        List<PixelArtApp.FrameData> frames = app.layerFrames[layer];
        int insertAt = Math.min(frames.size(), app.currentFrameIndex[layer] + 1);
        PixelArtApp.FrameData data = app.createEmptyFrameForLayer(layer);
        frames.add(insertAt, data);
        app.currentFrameIndex[layer] = insertAt;
        syncOtherLayersToActive(app.currentFrameIndex[layer]);
        applyAllCurrentFrames();
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void duplicateCurrentFrame() {
        app.ensureFrameCapacity();
        int layer = app.activeLayer;
        List<PixelArtApp.FrameData> frames = app.layerFrames[layer];
        if (frames.isEmpty()) {
            addBlankFrame();
            return;
        }
        saveCurrentFrames();
        PixelArtApp.FrameData snapshot = app.captureFrameForLayer(layer);
        int insertAt = Math.min(frames.size(), app.currentFrameIndex[layer] + 1);
        frames.add(insertAt, snapshot);
        app.currentFrameIndex[layer] = insertAt;
        syncOtherLayersToActive(app.currentFrameIndex[layer]);
        applyAllCurrentFrames();
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void deleteCurrentFrame() {
        app.ensureFrameCapacity();
        int layer = app.activeLayer;
        List<PixelArtApp.FrameData> frames = app.layerFrames[layer];
        if (frames.isEmpty())
            return;
        frames.remove(app.currentFrameIndex[layer]);
        if (frames.isEmpty()) {
            PixelArtApp.FrameData blank = app.createEmptyFrameForLayer(layer);
            frames.add(blank);
            app.currentFrameIndex[layer] = 0;
            app.applyFrameForLayer(layer, blank);
        } else {
            app.currentFrameIndex[layer] = Math.max(0, Math.min(app.currentFrameIndex[layer], frames.size() - 1));
            app.applyFrameForLayer(layer, frames.get(app.currentFrameIndex[layer]));
        }
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void selectFrame(int index) {
        app.ensureFrameCapacity();
        int layer = app.activeLayer;
        List<PixelArtApp.FrameData> frames = app.layerFrames[layer];
        if (index < 0 || index >= frames.size())
            return;
        saveCurrentFrames();
        app.currentFrameIndex[layer] = index;
        app.playCursor = index;
        syncOtherLayersToActive(index);
        applyAllCurrentFrames();
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void stepFrame(int delta) {
        app.ensureFrameCapacity();
        if (app.layerFrames == null || app.activeLayer < 0 || app.activeLayer >= app.layerFrames.length)
            return;
        List<PixelArtApp.FrameData> frames = app.layerFrames[app.activeLayer];
        if (frames == null || frames.isEmpty())
            return;
        saveCurrentFrames();
        int size = frames.size();
        int next = (app.currentFrameIndex[app.activeLayer] + delta) % size;
        if (next < 0)
            next += size;
        app.currentFrameIndex[app.activeLayer] = next;
        app.playCursor = next;
        syncOtherLayersToActive(next);
        applyAllCurrentFrames();
        if (app.timeline != null)
            app.timeline.repaint();
    }

    public void togglePlayback() {
        boolean anyFrames = false;
        for (List<PixelArtApp.FrameData> lf : app.layerFrames) {
            if (lf != null && lf.size() > 1) {
                anyFrames = true;
                break;
            }
        }
        if (!anyFrames) {
            app.console.setStatus("No frames to play");
            return;
        }
        syncOtherLayersToActive(app.currentFrameIndex[app.activeLayer]);
        app.playCursor = app.currentFrameIndex[app.activeLayer];
        app.playing = !app.playing;
        if (app.playing) {
            if (app.playTimer == null) {
                app.playTimer = new javax.swing.Timer(app.delayFromFPS(), e -> advanceFrame());
            }
            app.playTimer.setDelay(app.delayFromFPS());
            app.playTimer.start();
        } else {
            if (app.playTimer != null)
                app.playTimer.stop();
        }
        if (app.timeline != null)
            app.timeline.repaint();
    }

    private void advanceFrame() {
        app.ensureFrameCapacity();
        saveCurrentFrames();
        int maxLen = 0;
        for (int l = 0; l < app.layerFrames.length; l++) {
            List<PixelArtApp.FrameData> lf = app.layerFrames[l];
            if (lf == null)
                continue;
            if (!app.animatedLayers[l])
                continue;
            maxLen = Math.max(maxLen, lf.size());
        }
        if (maxLen <= 0) {
            app.playing = false;
            if (app.playTimer != null)
                app.playTimer.stop();
            return;
        }
        app.playCursor = (app.playCursor + 1) % maxLen;
        for (int l = 0; l < app.layerFrames.length; l++) {
            List<PixelArtApp.FrameData> lf = app.layerFrames[l];
            if (lf == null || lf.isEmpty())
                continue;
            if (!app.animatedLayers[l])
                continue;
            app.currentFrameIndex[l] = app.playCursor % lf.size();
        }
        applyAllCurrentFrames();
        if (app.timeline != null)
            app.timeline.repaint();
    }

    private void syncOtherLayersToActive(int activeIndex) {
        for (int l = 0; l < app.layerFrames.length; l++) {
            if (l == app.activeLayer)
                continue;
            List<PixelArtApp.FrameData> lf = app.layerFrames[l];
            if (lf == null || lf.isEmpty())
                continue;
            app.currentFrameIndex[l] = activeIndex % lf.size();
        }
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
}
