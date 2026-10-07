package io.github.gamePackage.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetErrorListener;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The game's asset manager: discovers assets with {@link AssetCatalog}, loads them through libGDX's
 * {@link AssetManager}, and serves the processed copy of an asset when the editor has made one.
 */
public class GameAssets implements Disposable {
    public final AssetManager manager = new AssetManager(GameFiles.RESOLVER);
    public final AssetCatalog catalog = new AssetCatalog();
    public final AssetMetaStore meta = new AssetMetaStore();

    /** Asset variant key ("id|base" or "id|flipped") to the path loaded for it. */
    private final ObjectMap<String, String> loadedPaths = new ObjectMap<>();
    private final TextureLoader.TextureParameter textureParameter = new TextureLoader.TextureParameter();

    public GameAssets() {
        textureParameter.genMipMaps = true;
        textureParameter.minFilter = Texture.TextureFilter.MipMapLinearLinear;
        textureParameter.magFilter = Texture.TextureFilter.Linear;
        // Skip files that fail to load instead of crashing; the asset just won't be drawn.
        manager.setErrorListener(new AssetErrorListener() {
            @Override
            public void error(AssetDescriptor asset, Throwable throwable) {
                Gdx.app.error("GameAssets", "Could not load " + asset.fileName, throwable);
            }
        });
    }

    /**
     * Scans the assets folder and queues everything not loaded yet; call {@link AssetManager#update()} to load.
     * Load {@link #meta} first so processed copies are picked.
     */
    public void scanAndQueue() {
        catalog.scan();
        for (SpriteAsset terrain : catalog.terrains()) queue(key(terrain.id, false), pathFor(terrain, false));
        for (SpriteAsset asset : catalog.sprites()) {
            queue(key(asset.id, false), pathFor(asset, false));
            if (asset.hasFlipped()) queue(key(asset.id, true), pathFor(asset, true));
        }
    }

    /** Path to load for a variant: the processed copy if the asset is processed and the copy exists. */
    public String pathFor(SpriteAsset asset, boolean flipped) {
        String source = asset.sourcePath(flipped);
        AssetMeta assetMeta = meta.find(asset.id);
        if (assetMeta != null && assetMeta.needsProcessing()) {
            String processed = GameFiles.processedPath(source);
            if (GameFiles.resolve(processed).exists()) return processed;
        }
        return source;
    }

    /** Texture for a variant. Without a flipped file this is the base texture; draw it mirrored. */
    public Texture texture(SpriteAsset asset, boolean flipped) {
        String path = loadedPath(asset, flipped);
        return path != null && manager.isLoaded(path) ? manager.get(path, Texture.class) : null;
    }

    public String loadedPath(SpriteAsset asset, boolean flipped) {
        return loadedPaths.get(key(asset.id, flipped && asset.hasFlipped()));
    }

    /** Reloads an asset after the editor changed its files. */
    public void reload(SpriteAsset asset) {
        reloadVariant(asset, false);
        if (asset.hasFlipped()) reloadVariant(asset, true);
    }

    private void reloadVariant(SpriteAsset asset, boolean flipped) {
        String key = key(asset.id, flipped);
        String old = loadedPaths.remove(key);
        if (old != null && manager.isLoaded(old)) manager.unload(old);
        String path = pathFor(asset, flipped);
        queue(key, path);
        manager.finishLoadingAsset(path);
    }

    private void queue(String key, String path) {
        if (loadedPaths.containsKey(key)) return;
        loadedPaths.put(key, path);
        manager.load(path, Texture.class, textureParameter);
    }

    private static String key(String id, boolean flipped) {
        return id + (flipped ? "|flipped" : "|base");
    }

    @Override
    public void dispose() {
        manager.dispose();
    }
}
