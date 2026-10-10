package com.synapmc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

/** Replaces an item type's main model texture in native rendering, excluding custom-model stacks. */
public final class ItemTexture {
    private final Object item;
    private final ItemTextureBackend backend;
    public ItemTexture(Object item) { this(item, NativeTextureAccess.INSTANCE); }
    public ItemTexture(Object item, ItemTextureBackend backend) { this.item = item; this.backend = backend; }
    public List<String> getIdentifiers() { return List.copyOf(new LinkedHashSet<>(backend.defaultIdentifiers(item))); }
    public List<File> export() throws IOException { return ItemStackTexture.exportAssets(backend, getIdentifiers()); }
    public ItemTexture replace(Object pngFile) throws IOException { return replace(pngFile, null); }
    public ItemTexture replace(Object pngFile, Object mcmetaFile) throws IOException {
        TextureFiles.Loaded files = TextureFiles.load(pngFile, mcmetaFile);
        List<String> ids = getIdentifiers();
        if (ids.isEmpty()) throw new IOException("This item has no replaceable model PNG texture");
        var replacement = ItemTextureModels.build(backend, backend.itemId(item), backend.defaultModelId(item), ids.get(0), files.png(), files.metadata());
        ItemTextureModels.set(item, replacement);
        backend.reloadItemTextures();
        return this;
    }
    public ItemTexture revert() {
        if (ItemTextureModels.remove(item)) backend.reloadItemTextures();
        return this;
    }
}
