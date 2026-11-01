package net.minecraft.client.gui.screens;

import java.nio.file.Path;
import java.util.List;

public class Screen {
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) { return false; }
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) { return false; }
    public void onFilesDrop(List<Path> paths) {}
}
