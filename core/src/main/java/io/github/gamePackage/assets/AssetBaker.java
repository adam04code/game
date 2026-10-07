package io.github.gamePackage.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

/** Writes processed copies of assets without the editor UI, e.g. the automatic green removal on opening it. */
public final class AssetBaker {
    private AssetBaker() {
    }

    /** Whether {@link #autoProcess} has anything to do for this asset. */
    public static boolean needsAutoProcess(SpriteAsset asset, AssetMeta meta) {
        if (asset.terrain) return false;
        if (meta == null) return true;
        if (!meta.removeGreen && !meta.noGreenBackground) return true;
        return meta.needsProcessing() && !processedFilesExist(asset);
    }

    /**
     * Turns on green removal if the image has a green screen (or records that it has none), then writes the
     * processed images if they are missing. Returns true if processed images were written.
     */
    public static boolean autoProcess(SpriteAsset asset, AssetMeta meta) {
        if (!meta.removeGreen && !meta.noGreenBackground) {
            Pixmap base = new Pixmap(GameFiles.resolve(asset.basePath()));
            try {
                if (ImageProcessor.hasGreenScreen(base)) meta.removeGreen = true;
                else meta.noGreenBackground = true;
            } finally {
                base.dispose();
            }
        }
        if (!meta.needsProcessing() || processedFilesExist(asset)) return false;
        bake(asset.basePath(), meta);
        if (asset.hasFlipped()) bake(asset.flippedPath(), meta);
        return true;
    }

    private static boolean processedFilesExist(SpriteAsset asset) {
        if (!GameFiles.resolve(GameFiles.processedPath(asset.basePath())).exists()) return false;
        return !asset.hasFlipped() || GameFiles.resolve(GameFiles.processedPath(asset.flippedPath())).exists();
    }

    private static void bake(String sourcePath, AssetMeta meta) {
        Pixmap original = new Pixmap(GameFiles.resolve(sourcePath));
        Pixmap processed = ImageProcessor.process(original, meta);
        try {
            FileHandle file = GameFiles.writable(GameFiles.processedPath(sourcePath));
            file.parent().mkdirs();
            PixmapIO.writePNG(file, processed);
        } finally {
            original.dispose();
            processed.dispose();
        }
    }
}
