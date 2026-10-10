package com.synapmc;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/** Minecraft operations separated from texture asset and animation handling. */
public interface TextureBackend {
    List<String> identifiers(Object stack);
    byte[] readAsset(String identifier) throws IOException;
    Object copyStack(Object stack);
    Object registerImage(BufferedImage image, String name);
    void updateImage(Object image);
    void drawImage(Object image, Object context, int x, int y);
    void releaseImage(Object image);
}
