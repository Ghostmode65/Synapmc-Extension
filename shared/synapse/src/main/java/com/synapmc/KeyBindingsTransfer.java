package com.synapmc;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Portable registrations for the current profile and its services. Script paths are absolute. */
public final class KeyBindingsTransfer {
    private KeyBindingsTransfer() {}

    public static final class Registration {
        public String type;
        public String event;
        public String file;
        public boolean enabled;
        public boolean joined;
    }

    public static final class Archive {
        public int version = 1;
        public List<Registration> triggers = new ArrayList<>();
        public List<Registration> services = new ArrayList<>();
    }

    public static Path archivePath() {
        return SynapMcShared.roamingDir.toPath().resolve("config/keys/keys.json");
    }

    private static Object registry() throws Exception {
        return SynapMcShared.core.getProfile().getClass().getMethod("getRegistry")
                .invoke(SynapMcShared.core.getProfile());
    }

    private static Path absolute(Object file) {
        Path path = file instanceof Path ? (Path) file : file instanceof File
                ? ((File) file).toPath() : Path.of(file.toString());
        return (path.isAbsolute() ? path : SynapMcShared.core.getMacroFolder().toPath().resolve(path))
                .toAbsolutePath().normalize();
    }

    private static Registration readTrigger(Object trigger) throws Exception {
        Registration row = new Registration();
        row.type = trigger.getClass().getField("triggerType").get(trigger).toString();
        row.event = (String) trigger.getClass().getField("event").get(trigger);
        row.file = absolute(trigger.getClass().getField("scriptFile").get(trigger)).toString();
        row.enabled = trigger.getClass().getField("enabled").getBoolean(trigger);
        row.joined = trigger.getClass().getField("joined").getBoolean(trigger);
        return row;
    }

    public static synchronized String exportKeys() throws Exception {
        Archive archive = new Archive();
        Object registry = registry();
        for (Object trigger : (Collection<?>) registry.getClass().getMethod("getScriptTriggers").invoke(registry))
            archive.triggers.add(readTrigger(trigger));
        Object services = SynapMcShared.core.getServices();
        if (services != null) {
            for (Object name : (Collection<?>) services.getClass().getMethod("getServices").invoke(services)) {
                Object trigger = services.getClass().getMethod("getTrigger", String.class).invoke(services, name);
                Registration row = new Registration();
                row.type = "SERVICE";
                row.event = name.toString();
                row.file = absolute(trigger.getClass().getField("file").get(trigger)).toString();
                row.enabled = trigger.getClass().getField("enabled").getBoolean(trigger);
                archive.services.add(row);
            }
        }
        SynapMcConfig.write(archivePath(), archive);
        return "Exported " + (archive.triggers.size() + archive.services.size()) + " registrations to config/keys/keys.json.";
    }

    /** Supports the File constructor in legacy and the Path constructor in newer editions. */
    public static Object serviceTrigger(File file, boolean enabled) throws Exception {
        String name = SynapMcShared.core.getScriptTriggerClassName();
        String corePackage = name.substring(0, name.lastIndexOf(".config."));
        Class<?> type = Class.forName(corePackage + ".service.ServiceTrigger");
        try { return type.getConstructor(Path.class, boolean.class).newInstance(file.toPath(), enabled); }
        catch (NoSuchMethodException ignored) {
            return type.getConstructor(File.class, boolean.class).newInstance(file, enabled);
        }
    }

    private static boolean isKey(String type) { return type.startsWith("KEY_"); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized String importKeys() throws Exception {
        Archive archive = new Gson().fromJson(Files.readString(archivePath()), Archive.class);
        if (archive == null || archive.version != 1 || archive.triggers == null || archive.services == null)
            throw new IOException("Unsupported registration archive");
        Class<?> triggerClass = Class.forName(SynapMcShared.core.getScriptTriggerClassName());
        Class<? extends Enum> typeClass = (Class<? extends Enum>) Class.forName(triggerClass.getName() + "$TriggerType");
        // Validate the entire archive before adding any registrations.
        for (Registration row : archive.triggers) {
            validate(row);
            Enum.valueOf(typeClass, row.type);
            if ("SERVICE".equals(row.type)) throw new IOException("Service in trigger list");
        }
        for (Registration row : archive.services) {
            validate(row);
            if (!"SERVICE".equals(row.type)) throw new IOException("Invalid service registration");
        }
        Object services = SynapMcShared.core.getServices();
        if (services == null && !archive.services.isEmpty()) throw new IOException("Service manager unavailable");
        Object registry = registry();
        Set<String> keys = new HashSet<>();
        Set<String> existing = new HashSet<>();
        for (Object trigger : (Collection<?>) registry.getClass().getMethod("getScriptTriggers").invoke(registry)) {
            Registration row = readTrigger(trigger);
            existing.add(identity(row));
            if (isKey(row.type)) keys.add(row.event.toLowerCase(java.util.Locale.ROOT));
        }
        int added = 0, disabled = 0, skipped = 0;
        for (Registration row : archive.triggers) {
            if (!existing.add(identity(row))) { skipped++; continue; }
            boolean enabled = row.enabled;
            if (isKey(row.type) && !keys.add(row.event.toLowerCase(java.util.Locale.ROOT))) { enabled = false; disabled++; }
            String relative = SynapMcShared.core.getMacroFolder().toPath().toAbsolutePath().normalize()
                    .relativize(Path.of(row.file).toAbsolutePath().normalize()).toString();
            Object trigger = triggerClass.getConstructor(typeClass, String.class, String.class, boolean.class, boolean.class)
                    .newInstance(Enum.valueOf(typeClass, row.type), row.event, relative, enabled, row.joined);
            registry.getClass().getMethod("addScriptTrigger", triggerClass).invoke(registry, trigger);
            added++;
        }
        SynapMcShared.core.getProfile().getClass().getMethod("saveProfile").invoke(SynapMcShared.core.getProfile());
        if (services != null) {
            Set<String> names = new HashSet<>((Collection<String>) services.getClass().getMethod("getServices").invoke(services));
            for (Registration row : archive.services) {
                String name = row.event;
                boolean enabled = row.enabled;
                boolean duplicate = false;
                for (String candidate : names) {
                    if (candidate.equals(name) || candidate.startsWith(name + " (imported ")) {
                        Object current = services.getClass().getMethod("getTrigger", String.class).invoke(services, candidate);
                        if (absolute(current.getClass().getField("file").get(current)).equals(absolute(row.file))) {
                            duplicate = true;
                            break;
                        }
                    }
                }
                if (duplicate) { skipped++; continue; }
                if (names.contains(name)) {
                    int suffix = 2;
                    while (names.contains(name)) name = row.event + " (imported " + suffix++ + ")";
                    enabled = false;
                    disabled++;
                }
                Object trigger = serviceTrigger(new File(row.file), enabled);
                if (!Boolean.TRUE.equals(services.getClass().getMethod("registerService", String.class, trigger.getClass())
                        .invoke(services, name, trigger))) throw new IOException("Could not register service " + name);
                names.add(name);
                added++;
            }
            services.getClass().getMethod("save").invoke(services);
        }
        return "Imported " + added + "; disabled conflicts: " + disabled + "; already registered: " + skipped + ".";
    }

    private static void validate(Registration row) throws IOException {
        if (row == null || row.type == null || row.event == null || row.file == null || row.file.isBlank())
            throw new IOException("Incomplete registration");
        if (!Path.of(row.file).isAbsolute()) throw new IOException("Registration path must be absolute");
    }

    private static String identity(Registration row) {
        return row.type + "\n" + row.event + "\n" + absolute(row.file);
    }
}
