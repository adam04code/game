package io.github.gamePackage.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;

/**
 * Finds files in the assets folder. Files the editor writes (processed images, metadata) go to the assets folder
 * on disk, and every read prefers that folder so freshly written files are picked up without a rebuild.
 */
public final class GameFiles {
    /** Lets the AssetManager read from the same place as everything else. */
    public static final FileHandleResolver RESOLVER = new FileHandleResolver() {
        @Override
        public FileHandle resolve(String fileName) {
            return GameFiles.resolve(fileName);
        }
    };

    private static String root;

    private GameFiles() {
    }

    /**
     * Prefix that turns an asset path into a path relative to the working directory. Running through Gradle the
     * working directory is the assets folder itself; running from an IDE it is usually the project root.
     */
    public static String root() {
        if (root == null) root = Gdx.files.local("assets").isDirectory() ? "assets/" : "";
        return root;
    }

    /** The on-disk location of an asset path, used for scanning and writing. */
    public static FileHandle writable(String path) {
        return Gdx.files.local(root() + path);
    }

    /** The on-disk file if it exists, otherwise the packaged (classpath) copy. */
    public static FileHandle resolve(String path) {
        FileHandle local = writable(path);
        if (local.exists()) return local;
        return Gdx.files.internal(path);
    }

    /** Where the editor stores the processed copy (green removed, resized) of a source image. */
    public static String processedPath(String sourcePath) {
        return "processed/" + stripExtension(sourcePath) + ".png";
    }

    public static String stripExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(0, dot) : path;
    }
}
