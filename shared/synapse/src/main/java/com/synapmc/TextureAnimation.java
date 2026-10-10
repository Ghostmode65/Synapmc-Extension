package com.synapmc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** PNG sprite-sheet frames, durations and optional interpolation from a Minecraft .mcmeta file. */
public final class TextureAnimation {
    private record Frame(int index, int ticks) {}
    private final BufferedImage source;
    private final List<Frame> frames;
    private final boolean interpolate;
    private final long totalTicks;
    private final int width, height, columns;
    private long lastFrame = Long.MIN_VALUE;

    public TextureAnimation(BufferedImage source, JsonObject metadata) {
        this.source = source;
        JsonObject animation = metadata != null && metadata.has("animation") ? metadata.getAsJsonObject("animation") : null;
        if (animation == null) {
            width = source.getWidth(); height = source.getHeight();
            frames = List.of(new Frame(0, 1)); interpolate = false;
        } else {
            int defaultSize = Math.min(source.getWidth(), source.getHeight());
            width = animation.has("width") ? positive(animation.get("width")) : animation.has("height") ? source.getWidth() : defaultSize;
            height = animation.has("height") ? positive(animation.get("height")) : animation.has("width") ? source.getHeight() : defaultSize;
            if (source.getWidth() % width != 0 || source.getHeight() % height != 0)
                throw new IllegalArgumentException("Animation frame size must divide the PNG dimensions");
            int count = source.getWidth() / width * (source.getHeight() / height);
            int defaultTime = animation.has("frametime") ? positive(animation.get("frametime")) : 1;
            List<Frame> selected = new ArrayList<>();
            if (animation.has("frames")) for (JsonElement entry : animation.getAsJsonArray("frames")) {
                int index = integer(entry.isJsonObject() ? entry.getAsJsonObject().get("index") : entry);
                int ticks = entry.isJsonObject() && entry.getAsJsonObject().has("time") ? positive(entry.getAsJsonObject().get("time")) : defaultTime;
                if (index < 0 || index >= count) throw new IllegalArgumentException("Animation frame index is outside the PNG");
                selected.add(new Frame(index, ticks));
            }
            if (selected.isEmpty()) for (int i = 0; i < count; i++) selected.add(new Frame(i, defaultTime));
            frames = List.copyOf(selected);
            interpolate = animation.has("interpolate") && animation.get("interpolate").getAsBoolean();
        }
        columns = source.getWidth() / width;
        totalTicks = frames.stream().mapToLong(Frame::ticks).sum();
    }

    public BufferedImage createImage() { return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); }

    /** Only uploads a new frame when pixels can have changed. A Minecraft tick is 50ms. */
    public synchronized boolean paint(BufferedImage destination, long elapsedNanos) {
        long tick = Math.max(0, elapsedNanos) / 50_000_000L;
        double fractionalTick = Math.max(0, elapsedNanos) % 50_000_000L / 50_000_000.0;
        long position = tick % totalTicks;
        int step = 0;
        while (position >= frames.get(step).ticks()) position -= frames.get(step++).ticks();
        long key = interpolate && frames.size() > 1 ? Math.max(0, elapsedNanos) / 1_000_000L : step;
        if (lastFrame == key) return false;
        Frame current = frames.get(step), next = frames.get((step + 1) % frames.size());
        double blend = interpolate ? (position + fractionalTick) / current.ticks() : 0;
        int fromX = current.index() % columns * width, fromY = current.index() / columns * height;
        int toX = next.index() % columns * width, toY = next.index() / columns * height;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int first = source.getRGB(fromX + x, fromY + y);
            int second = blend == 0 ? first : source.getRGB(toX + x, toY + y);
            int pixel = 0;
            for (int shift = 0; shift <= 24; shift += 8)
                pixel |= (int) Math.round(((first >>> shift) & 255) * (1 - blend) + ((second >>> shift) & 255) * blend) << shift;
            destination.setRGB(x, y, pixel);
        }
        lastFrame = key;
        return true;
    }

    private static int positive(JsonElement value) {
        int number = integer(value);
        if (number <= 0) throw new IllegalArgumentException("Animation size and duration must be positive integers");
        return number;
    }
    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Animation indices, sizes and durations must be integers");
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != value.getAsInt()) throw new IllegalArgumentException("Invalid animation integer");
        return value.getAsInt();
    }
}
