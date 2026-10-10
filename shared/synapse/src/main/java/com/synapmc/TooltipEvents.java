package com.synapmc;

/** Registers the native event while allowing older Legacy cores to keep loading. */
public final class TooltipEvents {
    public static final String NAME = "renderTooltip";
    private TooltipEvents() {}

    public static void register(Object registry, Class<?> eventType) {
        register(registry, NAME, eventType);
    }

    public static void register(Object registry, String name, Class<?> eventType) {
        try {
            if (eventType == null) registry.getClass().getMethod("addEvent", String.class).invoke(registry, name);
            else registry.getClass().getMethod("addEvent", Class.class).invoke(registry, eventType);
            // Legacy's compile core predates joinableEvents. Live 1.21 cores expose this set.
            try {
                @SuppressWarnings("unchecked")
                java.util.Set<String> joined = (java.util.Set<String>) registry.getClass().getField("joinableEvents").get(registry);
                joined.add(name);
            } catch (NoSuchFieldException olderCore) { }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not register the " + name + " event", error);
        }
    }
}
