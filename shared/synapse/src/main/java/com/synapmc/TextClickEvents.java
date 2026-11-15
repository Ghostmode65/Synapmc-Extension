package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads click metadata from native text JSON without executing the click action. */
public final class TextClickEvents {
    private static final Gson GSON = new Gson();
    private TextClickEvents() {}

    public static List<Map<String, Object>> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        List<Map<String, Object>> events = new ArrayList<>();
        collect(JsonParser.parseString(json), events);
        return events.isEmpty() ? null : events;
    }

    private static void collect(JsonElement text, List<Map<String, Object>> events) {
        if (text == null || text.isJsonNull() || text.isJsonPrimitive()) return;
        if (text.isJsonArray()) {
            for (JsonElement child : text.getAsJsonArray()) collect(child, events);
            return;
        }
        JsonObject component = text.getAsJsonObject();
        for (String key : new String[] {"click_event", "clickEvent"}) {
            JsonElement value = component.get(key);
            if (value == null || !value.isJsonObject()) continue;
            JsonObject click = value.getAsJsonObject();
            JsonElement action = click.get("action");
            if (action == null || !action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) continue;
            Map<String, Object> result = GSON.fromJson(click, new TypeToken<LinkedHashMap<String, Object>>() {}.getType());
            // Older Minecraft uses value for all actions; newer versions use typed fields.
            String payload = switch (action.getAsString()) {
                case "run_command", "suggest_command" -> "command";
                case "open_url" -> "url";
                case "open_file" -> "path";
                case "change_page" -> "page";
                default -> "value";
            };
            if (!payload.equals("value") && result.containsKey("value")) {
                Object legacy = result.remove("value");
                result.putIfAbsent(payload, legacy);
            }
            if (payload.equals("page") && result.get("page") != null) {
                Object page = result.get("page");
                if (page instanceof Number number) result.put("page", number.intValue());
                else try { result.put("page", Integer.parseInt(page.toString())); }
                catch (NumberFormatException ignored) { /* Preserve older events with a nonnumeric value. */ }
            }
            events.add(result);
            break;
        }
        // Only visit text components: hover text and custom event payloads can contain
        // unrelated click events that are not part of this message's clickable text.
        for (String key : new String[] {"with", "separator", "extra"}) collect(component.get(key), events);
    }
}
