package com.synapmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorLiteral {
    private static final Pattern HEX = Pattern.compile("(?<![\\p{L}\\p{N}_$])0[xX](?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6})(?![\\p{L}\\p{N}_$])");
    public final int start;
    public final int end;
    public final String text;
    public final int argb;

    private ColorLiteral(int start, int end, String text) {
        this.start = start;
        this.end = end;
        this.text = text;
        int value = (int) Long.parseLong(text.substring(2), 16);
        argb = text.length() == 8 ? 0xFF000000 | value : value;
    }

    public static List<ColorLiteral> find(String source) {
        List<ColorLiteral> colors = new ArrayList<>();
        Matcher matcher = HEX.matcher(source);
        while (matcher.find()) colors.add(new ColorLiteral(matcher.start(), matcher.end(), matcher.group()));
        return colors;
    }

    public static ColorLiteral at(String source, int index) {
        for (ColorLiteral color : find(source)) if (index >= color.start && index < color.end) return color;
        return null;
    }

    public String format(int color) {
        String digits = String.format(Locale.ROOT, text.length() == 8 ? "%06x" : "%08x",
                text.length() == 8 ? color & 0xFFFFFF : color);
        if (text.substring(2).matches(".*[A-F].*") || text.startsWith("0X")) digits = digits.toUpperCase(Locale.ROOT);
        return text.substring(0, 2) + digits;
    }
}
