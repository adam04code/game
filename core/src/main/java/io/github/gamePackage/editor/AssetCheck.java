package io.github.gamePackage.editor;

import io.github.gamePackage.assets.AssetMeta;
import io.github.gamePackage.assets.SpriteAsset;

/** Finds what still needs doing on an asset. */
final class AssetCheck {
    private AssetCheck() {
    }

    /** Comma-separated problems with the saved settings, or null when the asset is done. */
    static String issues(SpriteAsset asset, AssetMeta meta) {
        StringBuilder issues = new StringBuilder();
        // Terrain maps have no green screen.
        if (!asset.terrain && (meta == null || (!meta.removeGreen && !meta.noGreenBackground))) {
            issues.append("green background");
        }
        if (meta == null || (!meta.noCollision && !meta.hasCollision())) {
            if (issues.length() > 0) issues.append(", ");
            issues.append("no collision");
        }
        return issues.length() == 0 ? null : issues.toString();
    }
}
