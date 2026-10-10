package com.synapmc;

import java.awt.image.BufferedImage;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Weak identity bindings: an override follows one copied stack, never every stack of an item type. */
public final class ItemTextureOverrides {
    private static final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private static final Map<Key, Replacement> replacements = new HashMap<>();
    private static final ConcurrentLinkedQueue<Replacement> retired = new ConcurrentLinkedQueue<>();
    private ItemTextureOverrides() {}

    private static final class Key extends WeakReference<Object> {
        private final int hash;
        Key(Object stack, ReferenceQueue<Object> queue) { super(stack, queue); hash = System.identityHashCode(stack); }
        public int hashCode() { return hash; }
        public boolean equals(Object value) {
            return this == value || value instanceof Key other && get() != null && get() == other.get();
        }
    }

    public static synchronized void set(Object stack, TextureAnimation animation, TextureBackend backend) {
        Replacement old = replacements.put(new Key(stack, collected), new Replacement(animation, backend));
        if (old != null) retired.add(old);
    }
    public static synchronized void remove(Object stack) {
        Replacement old = replacements.remove(new Key(stack, null));
        if (old != null) retired.add(old);
    }

    /** Invoked by the JsMacros Item mixin on the real rendering thread. */
    public static boolean draw(Object context, Object stack, int x, int y) {
        Replacement replacement;
        synchronized (ItemTextureOverrides.class) {
            Key key;
            while ((key = (Key) collected.poll()) != null) {
                Replacement old = replacements.remove(key);
                if (old != null) retired.add(old);
            }
            replacement = replacements.get(new Key(stack, null));
        }
        Replacement old;
        while ((old = retired.poll()) != null) old.release();
        if (replacement == null) return false;
        replacement.draw(context, x, y);
        return true;
    }

    private static final class Replacement {
        private final TextureAnimation animation;
        private final TextureBackend backend;
        private final BufferedImage pixels;
        private final long started = System.nanoTime();
        private Object image;
        Replacement(TextureAnimation animation, TextureBackend backend) {
            this.animation = animation; this.backend = backend; pixels = animation.createImage();
        }
        synchronized void draw(Object context, int x, int y) {
            boolean changed = animation.paint(pixels, System.nanoTime() - started);
            if (image == null) image = backend.registerImage(pixels, "synapmc_" + java.util.UUID.randomUUID());
            else if (changed) backend.updateImage(image);
            backend.drawImage(image, context, x, y);
        }
        synchronized void release() { if (image != null) { backend.releaseImage(image); image = null; } }
    }
}
