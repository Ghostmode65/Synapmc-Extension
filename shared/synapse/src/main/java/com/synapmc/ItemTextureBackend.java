package com.synapmc;

/** Native item identity/default model operations used by ItemTexture. */
public interface ItemTextureBackend extends TextureBackend {
    String itemId(Object item);
    String defaultModelId(Object item);
    java.util.List<String> defaultIdentifiers(Object item);
    boolean legacyModels();
    void reloadItemTextures();
}
