package com.synapmc;

import java.util.ArrayList;
import java.util.List;

/** Shared, persisted editor palettes; recent colors are ordered newest first. */
public final class EditorPalette {
    private EditorPalette() {}

    public static List<Integer> favorites() {
        return SynapMcShared.config == null ? List.of() : List.copyOf(SynapMcShared.config.favoriteColors);
    }

    public static List<Integer> recent() {
        return SynapMcShared.config == null ? List.of() : List.copyOf(SynapMcShared.config.recentColors);
    }

    public static boolean addFavorite(int color) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null) return false;
        if (config.favoriteColors.contains(color)) return true;
        if (config.favoriteColors.size() >= 48) return false;
        List<Integer> previous = new ArrayList<>(config.favoriteColors);
        config.favoriteColors.add(color);
        if (SynapMcShared.saveConfig()) return true;
        config.favoriteColors = previous;
        return false;
    }

    public static boolean remove(int color, boolean favorite) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null) return false;
        List<Integer> colors = favorite ? config.favoriteColors : config.recentColors;
        List<Integer> previous = new ArrayList<>(colors);
        if (!colors.remove(Integer.valueOf(color))) return true;
        if (SynapMcShared.saveConfig()) return true;
        if (favorite) config.favoriteColors = previous;
        else config.recentColors = previous;
        return false;
    }

    public static boolean remember(int color) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null) return false;
        List<Integer> previous = new ArrayList<>(config.recentColors);
        config.recentColors.remove(Integer.valueOf(color));
        config.recentColors.add(0, color);
        while (config.recentColors.size() > 24) config.recentColors.remove(config.recentColors.size() - 1);
        if (SynapMcShared.saveConfig()) return true;
        config.recentColors = previous;
        return false;
    }
}
