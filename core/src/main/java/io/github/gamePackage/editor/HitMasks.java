package io.github.gamePackage.editor;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.gamePackage.assets.GameFiles;

/** Low-resolution opacity masks, so clicking a transparent part of an image doesn't select it. */
class HitMasks {
    private static final int STEP = 4;

    private final ObjectMap<String, boolean[]> masks = new ObjectMap<>();
    private final ObjectMap<String, int[]> sizes = new ObjectMap<>();

    /** Whether the image at {@code path} is opaque at (u, v), measured from the top-left in 0..1. */
    boolean isOpaque(String path, float u, float v) {
        if (!masks.containsKey(path)) build(path);
        boolean[] mask = masks.get(path);
        int[] size = sizes.get(path);
        if (mask == null) return true;
        int x = Math.min(size[0] - 1, Math.max(0, (int) (u * size[0])));
        int y = Math.min(size[1] - 1, Math.max(0, (int) (v * size[1])));
        return mask[y * size[0] + x];
    }

    void invalidate(String path) {
        masks.remove(path);
        sizes.remove(path);
    }

    private void build(String path) {
        Pixmap pixmap;
        try {
            pixmap = new Pixmap(GameFiles.resolve(path));
        } catch (Exception e) {
            masks.put(path, null);
            return;
        }
        int width = (pixmap.getWidth() + STEP - 1) / STEP;
        int height = (pixmap.getHeight() + STEP - 1) / STEP;
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixmap.getPixel(Math.min(pixmap.getWidth() - 1, x * STEP + STEP / 2),
                    Math.min(pixmap.getHeight() - 1, y * STEP + STEP / 2));
                mask[y * width + x] = (pixel & 0xFF) > 64;
            }
        }
        pixmap.dispose();
        masks.put(path, mask);
        sizes.put(path, new int[] {width, height});
    }
}
