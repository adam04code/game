package io.github.gamePackage.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers every image in the assets folder, so new files are picked up just by dropping them in.
 * <ul>
 *   <li>Images in a folder whose name contains "terrain" are terrain maps. They are assets too (they can have
 *   bounds), flagged {@link SpriteAsset#terrain}, and listed separately in {@link #terrains()}.</li>
 *   <li>Every other image is a sprite asset. "name_flipped.jpg" is paired with "name.jpg" as its flipped side.</li>
 *   <li>The editor's own output folders ({@code processed/}, {@code data/}) are skipped.</li>
 * </ul>
 */
public class AssetCatalog {
    private static final Pattern FLIPPED_SUFFIX = Pattern.compile("(?i)[_\\- ]flipped$");
    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp"};
    private static final String[] IGNORED_FOLDERS = {"processed", "data"};
    private static final String[] IGNORED_FILES = {"libgdx.png"};

    private final Array<SpriteAsset> sprites = new Array<>();
    private final Array<SpriteAsset> terrains = new Array<>();
    private final ObjectMap<String, SpriteAsset> byId = new ObjectMap<>();

    /** Rebuilds the catalog from what is currently in the assets folder. */
    public void scan() {
        Set<String> paths = new TreeSet<>();
        FileHandle root = GameFiles.writable("");
        if (root.isDirectory()) walk(root, "", paths);
        // Packaged builds can't list classpath folders, so fall back to the list Gradle generates at build time.
        FileHandle list = GameFiles.resolve("assets.txt");
        if (list.exists()) {
            for (String line : list.readString("UTF-8").split("\\r?\\n")) {
                String path = line.trim();
                if (isCandidate(path)) paths.add(path);
            }
        }

        sprites.clear();
        terrains.clear();
        byId.clear();
        for (String path : paths) {
            if (isTerrain(path)) {
                SpriteAsset terrain = new SpriteAsset(GameFiles.stripExtension(path), true);
                if (byId.containsKey(terrain.id)) continue;
                terrain.basePath = path;
                byId.put(terrain.id, terrain);
                terrains.add(terrain);
                continue;
            }
            String stem = GameFiles.stripExtension(path);
            Matcher flipped = FLIPPED_SUFFIX.matcher(stem);
            boolean isFlipped = flipped.find();
            String id = isFlipped ? stem.substring(0, flipped.start()) : stem;
            SpriteAsset asset = byId.get(id);
            if (asset == null) {
                asset = new SpriteAsset(id, false);
                byId.put(id, asset);
                sprites.add(asset);
            }
            if (isFlipped) {
                if (asset.flippedPath == null) asset.flippedPath = path;
            } else if (asset.basePath == null) {
                asset.basePath = path;
            }
        }
        // A flipped file without its base is just a normal asset.
        for (SpriteAsset asset : sprites) {
            if (asset.basePath == null) {
                asset.basePath = asset.flippedPath;
                asset.flippedPath = null;
            }
        }
        sprites.sort(new Comparator<SpriteAsset>() {
            @Override
            public int compare(SpriteAsset a, SpriteAsset b) {
                return a.id.compareToIgnoreCase(b.id);
            }
        });
        Gdx.app.log("AssetCatalog", "Found " + sprites.size + " assets and " + terrains.size + " terrain maps");
    }

    public Array<SpriteAsset> sprites() {
        return sprites;
    }

    public Array<SpriteAsset> terrains() {
        return terrains;
    }

    /** A sprite or terrain by id. */
    public SpriteAsset find(String id) {
        return byId.get(id);
    }

    private static void walk(FileHandle dir, String prefix, Set<String> out) {
        for (FileHandle child : dir.list()) {
            String path = prefix + child.name();
            if (child.isDirectory()) {
                if (prefix.isEmpty() && isIgnoredFolder(child.name())) continue;
                walk(child, path + "/", out);
            } else if (isCandidate(path)) {
                out.add(path);
            }
        }
    }

    private static boolean isCandidate(String path) {
        if (path.isEmpty()) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        boolean image = false;
        for (String extension : IMAGE_EXTENSIONS) image |= lower.endsWith(extension);
        if (!image) return false;
        int slash = path.indexOf('/');
        if (slash >= 0 && isIgnoredFolder(path.substring(0, slash))) return false;
        for (String ignored : IGNORED_FILES) if (path.equals(ignored)) return false;
        return true;
    }

    private static boolean isIgnoredFolder(String name) {
        for (String ignored : IGNORED_FOLDERS) if (ignored.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static boolean isTerrain(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 && path.substring(0, slash).toLowerCase(Locale.ROOT).contains("terrain");
    }
}
