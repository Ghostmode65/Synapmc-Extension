package xyz.wagyourtail.jsmacros.client.api.classes.render.components;


public interface Alignable<B extends Alignable<B>> {
    B moveTo(int x, int y);
    int getScaledLeft();
    int getScaledTop();
    int getScaledWidth();
    int getScaledHeight();
    int getParentWidth();
    int getParentHeight();
}
