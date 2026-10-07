package io.github.gamePackage.assets;

/**
 * One placeable game asset. An asset may have a "_flipped" counterpart: the same object seen from the other side,
 * which the game uses to rotate it.
 */
public class SpriteAsset {
    /** Path without extension or "_flipped" suffix, e.g. "containers/Wooden_box_game_asset_20261007040839". */
    public final String id;
    /** Folder the asset lives in, e.g. "trees/pine". Empty for the assets root. */
    public final String category;
    /** Short name for lists. */
    public final String name;
    String basePath;
    String flippedPath;

    SpriteAsset(String id) {
        this.id = id;
        int slash = id.lastIndexOf('/');
        category = slash < 0 ? "" : id.substring(0, slash);
        String fileName = slash < 0 ? id : id.substring(slash + 1);
        // Drop generator timestamps like "_20261007040839" and characters the default font cannot draw.
        name = fileName.replaceAll("_\\d{8,}$", "").replace("…", "...");
    }

    public String basePath() {
        return basePath;
    }

    public String flippedPath() {
        return flippedPath;
    }

    public boolean hasFlipped() {
        return flippedPath != null;
    }

    /** The source image for a variant. Without a flipped file the base image is used (and mirrored when drawn). */
    public String sourcePath(boolean flipped) {
        return flipped && flippedPath != null ? flippedPath : basePath;
    }

    @Override
    public String toString() {
        String label = name.replace('_', ' ');
        if (hasFlipped()) label += " [+flipped]";
        return category.isEmpty() ? label : label + "  (" + category + ")";
    }
}
