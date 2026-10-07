package io.github.gamePackage.assets;

import com.badlogic.gdx.graphics.Pixmap;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Turns a source image into the image the game uses: resolution halved and green screen removed. */
public final class ImageProcessor {
    private ImageProcessor() {
    }

    /** Returns a new RGBA pixmap (caller disposes it); the original is left untouched. */
    public static Pixmap process(Pixmap original, AssetMeta meta) {
        Pixmap image = toRgba(original);
        // Halve before keying so the transparent background can't bleed into edge pixels when averaging.
        for (int i = 0; i < meta.halvings; i++) {
            Pixmap half = halve(image);
            image.dispose();
            image = half;
        }
        if (meta.removeGreen) removeGreen(image, meta.keyThreshold, meta.keySoftness);
        return image;
    }

    public static Pixmap toRgba(Pixmap source) {
        Pixmap copy = new Pixmap(source.getWidth(), source.getHeight(), Pixmap.Format.RGBA8888);
        copy.setBlending(Pixmap.Blending.None);
        copy.drawPixmap(source, 0, 0);
        return copy;
    }

    /** Halves width and height by averaging each 2x2 block. Expects RGBA8888. */
    public static Pixmap halve(Pixmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int newWidth = Math.max(1, width / 2);
        int newHeight = Math.max(1, height / 2);
        Pixmap result = new Pixmap(newWidth, newHeight, Pixmap.Format.RGBA8888);
        ByteBuffer src = source.getPixels();
        ByteBuffer dst = result.getPixels();
        for (int y = 0; y < newHeight; y++) {
            int y0 = Math.min(y * 2, height - 1);
            int y1 = Math.min(y * 2 + 1, height - 1);
            for (int x = 0; x < newWidth; x++) {
                int x0 = Math.min(x * 2, width - 1);
                int x1 = Math.min(x * 2 + 1, width - 1);
                for (int c = 0; c < 4; c++) {
                    int sum = (src.get((y0 * width + x0) * 4 + c) & 0xFF)
                        + (src.get((y0 * width + x1) * 4 + c) & 0xFF)
                        + (src.get((y1 * width + x0) * 4 + c) & 0xFF)
                        + (src.get((y1 * width + x1) * 4 + c) & 0xFF);
                    dst.put((y * newWidth + x) * 4 + c, (byte) ((sum + 2) / 4));
                }
            }
        }
        return result;
    }

    /**
     * Makes the green-screen background transparent. The background colour is sampled from the image border, and
     * each pixel is judged by how much greener than red/blue it is compared to that background. Green parts of the
     * object itself (leaves, moss) are far less saturated than the screen, so they survive. Edge pixels are faded
     * and have their green spill removed. Expects RGBA8888.
     */
    public static void removeGreen(Pixmap image, float threshold, float softness) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer pixels = image.getPixels();
        float[] key = sampleBorder(pixels, width, height);
        float keyGreenness = Math.max(0.05f, key[1] - Math.max(key[0], key[2]));
        float low = threshold;
        float range = Math.max(0.01f, softness);

        for (int i = 0, n = width * height; i < n; i++) {
            int offset = i * 4;
            float r = (pixels.get(offset) & 0xFF) / 255f;
            float g = (pixels.get(offset + 1) & 0xFF) / 255f;
            float b = (pixels.get(offset + 2) & 0xFF) / 255f;
            float max = Math.max(r, b);
            float t = (g - max) / keyGreenness;
            if (t <= low) continue;
            float s = Math.min(1f, (t - low) / range);
            s = s * s * (3f - 2f * s);
            float despilled = max + (g - max) * (1f - s);
            int alpha = pixels.get(offset + 3) & 0xFF;
            pixels.put(offset + 1, (byte) Math.round(despilled * 255f));
            pixels.put(offset + 3, (byte) Math.round(alpha * (1f - s)));
        }
    }

    /** Median colour of the image border, which on these assets is the green screen. */
    private static float[] sampleBorder(ByteBuffer pixels, int width, int height) {
        int count = 2 * width + 2 * height;
        float[][] channels = new float[3][count];
        int n = 0;
        for (int x = 0; x < width; x++) {
            n = sample(pixels, width, x, 0, channels, n);
            n = sample(pixels, width, x, height - 1, channels, n);
        }
        for (int y = 0; y < height; y++) {
            n = sample(pixels, width, 0, y, channels, n);
            n = sample(pixels, width, width - 1, y, channels, n);
        }
        float[] median = new float[3];
        for (int c = 0; c < 3; c++) {
            Arrays.sort(channels[c], 0, n);
            median[c] = channels[c][n / 2];
        }
        return median;
    }

    private static int sample(ByteBuffer pixels, int width, int x, int y, float[][] channels, int n) {
        int offset = (y * width + x) * 4;
        for (int c = 0; c < 3; c++) channels[c][n] = (pixels.get(offset + c) & 0xFF) / 255f;
        return n + 1;
    }
}
