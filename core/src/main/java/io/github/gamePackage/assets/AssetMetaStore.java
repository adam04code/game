package io.github.gamePackage.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.Comparator;

/** Loads and saves every asset's {@link AssetMeta} in {@code assets/data/asset_meta.json}. */
public class AssetMetaStore {
    public static final String PATH = "data/asset_meta.json";

    private final ObjectMap<String, AssetMeta> byId = new ObjectMap<>();
    private final Json json = createJson();

    public static class MetaFile {
        public Array<AssetMeta> assets = new Array<>();
    }

    public void load() {
        byId.clear();
        FileHandle file = GameFiles.resolve(PATH);
        if (!file.exists()) return;
        try {
            MetaFile metaFile = json.fromJson(MetaFile.class, file);
            for (AssetMeta meta : metaFile.assets) {
                meta.migrate();
                byId.put(meta.id, meta);
            }
        } catch (Exception e) {
            Gdx.app.error("AssetMetaStore", "Could not read " + PATH, e);
        }
    }

    public void save() {
        MetaFile metaFile = new MetaFile();
        for (AssetMeta meta : byId.values()) {
            if (!meta.isDefault()) metaFile.assets.add(meta);
        }
        metaFile.assets.sort(new Comparator<AssetMeta>() {
            @Override
            public int compare(AssetMeta a, AssetMeta b) {
                return a.id.compareTo(b.id);
            }
        });
        GameFiles.writable(PATH).writeString(json.prettyPrint(metaFile), false, "UTF-8");
    }

    /** Settings for an asset, or null if it was never edited. */
    public AssetMeta find(String id) {
        return byId.get(id);
    }

    /** Settings for an asset, created (unsaved) if it was never edited. */
    public AssetMeta getOrCreate(String id) {
        AssetMeta meta = byId.get(id);
        if (meta == null) {
            meta = new AssetMeta(id);
            byId.put(id, meta);
        }
        return meta;
    }

    private static Json createJson() {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        // Read the old single-polygon fields so they can be migrated, but never write them again.
        json.setIgnoreDeprecated(true);
        json.setReadDeprecated(true);
        json.setElementType(MetaFile.class, "assets", AssetMeta.class);
        return json;
    }
}
