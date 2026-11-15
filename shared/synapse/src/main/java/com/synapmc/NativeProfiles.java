package com.synapmc;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operates directly on the same CoreConfigV2 and BaseProfile as native Settings. */
public final class NativeProfiles {
    public record Registration(String script, String kind, String assignment, String path, boolean enabled) {}
    public record Entry(String name, List<Registration> registrations) {
        public int bindings() { return registrations.size(); }
    }
    public record Snapshot(List<Entry> entries, String current, String startup) {}

    private NativeProfiles() {}

    public static Snapshot snapshot() throws Exception {
        Access access = access();
        // Services are global in JsMacros. Read them outside the config monitor;
        // ServiceManager.save() can acquire the same monitors in the reverse order.
        List<Registration> services = serviceRegistrations();
        synchronized (access.manager) {
            List<Entry> entries = new ArrayList<>();
            String current = access.current();
            for (var entry : access.profiles().entrySet()) {
                Collection<?> triggers = entry.getValue();
                if (entry.getKey().equals(current)) {
                    Object registry = access.profile.getClass().getMethod("getRegistry").invoke(access.profile);
                    triggers = (Collection<?>) registry.getClass().getMethod("getScriptTriggers").invoke(registry);
                }
                List<Registration> registrations = new ArrayList<>();
                for (Object trigger : triggers) {
                    Class<?> type = trigger.getClass();
                    String kind = type.getField("triggerType").get(trigger).toString().startsWith("KEY_") ? "Key" : "Event";
                    registrations.add(registration(type.getField("scriptFile").get(trigger), kind,
                            (String) type.getField("event").get(trigger), type.getField("enabled").getBoolean(trigger)));
                }
                registrations.addAll(services);
                entries.add(new Entry(entry.getKey(), List.copyOf(registrations)));
            }
            entries.sort(Comparator.comparing((Entry entry) -> !entry.name().equals("default")).thenComparing(Entry::name));
            return new Snapshot(List.copyOf(entries), access.current(), access.startup());
        }
    }

    private static List<Registration> serviceRegistrations() throws Exception {
        Object services = SynapMcShared.core == null ? null : SynapMcShared.core.getServices();
        if (services == null) return List.of();
        List<Registration> rows = new ArrayList<>();
        synchronized (services) {
            Collection<?> names = (Collection<?>) services.getClass().getMethod("getServices").invoke(services);
            for (Object name : names.stream().sorted(Comparator.comparing(Object::toString)).toList()) {
                Object trigger = services.getClass().getMethod("getTrigger", String.class).invoke(services, name.toString());
                if (trigger == null) continue;
                rows.add(registration(trigger.getClass().getField("file").get(trigger), "Service", name.toString(),
                        trigger.getClass().getField("enabled").getBoolean(trigger)));
            }
        }
        return List.copyOf(rows);
    }

    private static Registration registration(Object file, String kind, String assignment, boolean enabled) {
        String path = file == null ? "" : file.toString();
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isEmpty()) name = "No script";
        return new Registration(name, kind, assignment == null || assignment.isBlank() ? "Unassigned" : assignment, path, enabled);
    }

    public static void initialize() throws Exception {
        Access access = access();
        synchronized (access.manager) {
            if (!access.profiles().isEmpty()) return;
            transaction(access, () -> {
                Object registry = access.profile.getClass().getMethod("getRegistry").invoke(access.profile);
                @SuppressWarnings("unchecked") List<Object> bindings = (List<Object>) registry.getClass().getMethod("getScriptTriggers").invoke(registry);
                access.profiles().put("default", new ArrayList<>(bindings));
                access.startup("default");
                access.renameCurrent("default");
            });
        }
    }

    public static void create(String name) throws Exception {
        String requested = validName(name);
        Access access = access();
        synchronized (access.manager) {
            requireNew(access, requested);
            access.saveCurrent();
            transaction(access, () -> access.profiles().put(requested, new ArrayList<>()));
        }
        switchTo(access, requested, true);
    }

    public static void switchTo(String name) throws Exception {
        Access access = access();
        switchTo(access, name, true);
    }

    private static void switchTo(Access access, String name, boolean remember) throws Exception {
        String previous;
        synchronized (access.manager) {
            requireExisting(access, name);
            if (name.equals(access.current())) {
                if (remember && !name.equals(access.startup())) {
                    access.saveCurrent();
                    transaction(access, () -> access.startup(name));
                }
                return;
            }
            access.saveCurrent();
            previous = access.current();
        }
        // Native loading fires script events. Let their callbacks access config without
        // waiting for a manager monitor held by the thread loading the profile.
        try {
            access.profile.getClass().getMethod("loadOrCreateProfile", String.class).invoke(access.profile, name);
            if (!name.equals(access.current())) throw new IllegalStateException("Could not load profile " + name);
            if (remember) synchronized (access.manager) {
                if (!name.equals(access.startup())) transaction(access, () -> access.startup(name));
            }
        } catch (Exception failure) {
            try { access.profile.getClass().getMethod("loadOrCreateProfile", String.class).invoke(access.profile, previous); }
            catch (Exception restore) { failure.addSuppressed(restore); }
            throw unwrap(failure);
        }
    }

    public static void rename(String name, String replacement) throws Exception {
        String requested = validName(replacement);
        Access access = access();
        synchronized (access.manager) {
            requireExisting(access, name);
            if (name.equals(requested)) return;
            requireNew(access, requested);
            access.saveCurrent();
            transaction(access, () -> {
                List<Object> bindings = access.profiles().remove(name);
                access.profiles().put(requested, bindings);
                if (name.equals(access.current())) access.renameCurrent(requested);
                if (name.equals(access.startup())) access.startup(requested);
            });
        }
    }

    public static void delete(String name) throws Exception {
        Access access = access();
        String previousCurrent;
        String fallback = null;
        synchronized (access.manager) {
            requireExisting(access, name);
            if (access.profiles().size() == 1) throw new IllegalArgumentException("Keep at least one profile.");
            access.saveCurrent();
            previousCurrent = access.current();
            // Switch first: saving the old active registry after removal would recreate it.
            if (name.equals(access.current())) {
                fallback = access.profiles().keySet().stream().filter(key -> !key.equals(name))
                        .sorted(Comparator.comparing((String key) -> !key.equals("default")).thenComparing(key -> key))
                        .findFirst().orElseThrow();
            }
        }
        if (fallback != null) switchTo(access, fallback, false);
        boolean switchedAway = fallback != null;
        try {
            synchronized (access.manager) {
                transaction(access, () -> {
                    access.profiles().remove(name);
                    if (switchedAway || name.equals(access.startup())) access.startup(access.current());
                });
            }
        } catch (Exception failure) {
            if (!previousCurrent.equals(access.current())) {
                try { access.profile.getClass().getMethod("loadOrCreateProfile", String.class).invoke(access.profile, previousCurrent); }
                catch (Exception restore) { failure.addSuppressed(restore); }
            }
            throw failure;
        }
    }

    private static void transaction(Access access, Change change) throws Exception {
        Map<String, List<Object>> profiles = access.profiles();
        Map<String, List<Object>> previous = new LinkedHashMap<>(profiles);
        String current = access.current(), startup = access.startup();
        Field rawField = access.manager.getClass().getField("rawOptions");
        JsonElement raw = (JsonElement) rawField.get(access.manager);
        JsonElement previousRaw = raw == null ? null : raw.deepCopy();
        try { change.apply(); access.persist(); }
        catch (Exception failure) {
            profiles.clear(); profiles.putAll(previous);
            access.startup(startup); access.renameCurrent(current);
            rawField.set(access.manager, previousRaw);
            // Shared storage can fail after the native write succeeded. Restore that
            // file too, so restarting does not apply an action reported as failed.
            try {
                access.manager.getClass().getMethod("saveConfig").invoke(access.manager);
                access.verifySaved();
            } catch (Exception restore) { failure.addSuppressed(restore); }
            throw unwrap(failure);
        }
    }

    private static void requireExisting(Access access, String name) throws Exception {
        if (!access.profiles().containsKey(name)) throw new IllegalArgumentException("Profile no longer exists. Refresh and try again.");
    }

    private static void requireNew(Access access, String name) throws Exception {
        if (access.profiles().containsKey(name)) throw new IllegalArgumentException("A profile with that name already exists.");
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Enter a profile name.");
        String normalized = name.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Profile names cannot contain control characters.");
        return normalized;
    }

    static Access access() throws Exception {
        ICoreAccess core = SynapMcShared.core;
        if (core == null || core.getProfile() == null || core.getJsMacrosConfigManager() == null)
            throw new IllegalStateException("JsMacros profiles are unavailable.");
        String trigger = core.getScriptTriggerClassName();
        if (trigger == null) throw new IllegalStateException("JsMacros config type is unavailable.");
        Class<?> configType = Class.forName(trigger.substring(0, trigger.lastIndexOf('.')) + ".CoreConfigV2");
        Object manager = core.getJsMacrosConfigManager();
        Object options = manager.getClass().getMethod("getOptions", Class.class).invoke(manager, configType);
        return new Access(manager, options, core.getProfile());
    }

    private static Exception unwrap(Exception failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() instanceof Exception cause) return cause;
        return failure;
    }

    private interface Change { void apply() throws Exception; }

    record Access(Object manager, Object options, Object profile) {
        @SuppressWarnings("unchecked")
        Map<String, List<Object>> profiles() throws Exception {
            return (Map<String, List<Object>>) options.getClass().getField("profiles").get(options);
        }
        String current() throws Exception { return (String) profile.getClass().getMethod("getCurrentProfileName").invoke(profile); }
        String startup() throws Exception { return (String) options.getClass().getField("defaultProfile").get(options); }
        void startup(String name) throws Exception { options.getClass().getField("defaultProfile").set(options, name); }
        void renameCurrent(String name) throws Exception { profile.getClass().getMethod("renameCurrentProfile", String.class).invoke(profile, name); }
        void saveCurrent() throws Exception { profile.getClass().getMethod("saveProfile").invoke(profile); verifySaved(); SharedProfiles.save(this); }
        void persist() throws Exception { manager.getClass().getMethod("saveConfig").invoke(manager); verifySaved(); SharedProfiles.save(this); }
        void verifySaved() throws Exception {
            File file = (File) manager.getClass().getField("configFile").get(manager);
            JsonElement expected = (JsonElement) manager.getClass().getField("rawOptions").get(manager);
            if (file == null || !file.isFile() || expected == null
                    || !JsonParser.parseString(Files.readString(file.toPath())).equals(expected))
                throw new IllegalStateException("Could not save JsMacros profiles. Check the config directory.");
        }
    }
}
