package com.synapmc;

import @CORE_PACKAGE@.client.api.classes.render.components.Alignable;

/** A read-only alignment target in scaled GUI pixels. Minecraft owns its position. */
public final class LoreWindow implements Alignable<LoreWindow> {
    private final int x, y, width, height, parentWidth, parentHeight;

    public LoreWindow(int x, int y, int width, int height, int parentWidth, int parentHeight) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.parentWidth = parentWidth; this.parentHeight = parentHeight;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    @Override public int getScaledLeft() { return x; }
    @Override public int getScaledTop() { return y; }
    @Override public int getScaledWidth() { return width; }
    @Override public int getScaledHeight() { return height; }
    @Override public int getParentWidth() { return parentWidth; }
    @Override public int getParentHeight() { return parentHeight; }
    @Override public LoreWindow moveTo(int x, int y) {
        throw new UnsupportedOperationException("Tooltip bounds are read-only; align your render element to this window");
    }
}
