package com.synapmc.repo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.synapmc.SynapMcConfig;
import com.synapmc.SynapMcShared;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A discovery feed, separate from repositories containing installed scripts. */
public final class PopularRepositories {
    private PopularRepositories() {}

    public static List<String> parse(String json) throws IOException {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("popularRepos"))
            throw new IOException("Popular manifest must contain a popularRepos array");
        JsonElement links = root.getAsJsonObject().get("popularRepos");
        if (!links.isJsonArray()) throw new IOException("popularRepos must be an array of manifest URLs");
        List<String> result = new ArrayList<>();
        for (JsonElement link : links.getAsJsonArray()) {
            if (!link.isJsonPrimitive() || !link.getAsJsonPrimitive().isString())
                throw new IOException("popularRepos entries must be manifest URL strings");
            String url = link.getAsString().trim();
            if (!url.isEmpty() && !result.contains(url)) result.add(url);
        }
        return result;
    }

    public static synchronized List<String> load() {
        Path cache = SynapMcShared.roamingDir.toPath().resolve("config/popular.json");
        try {
            String json = SynapMcShared.core.httpGetText(RepositoryDefaults.POPULAR_MANIFEST);
            List<String> links = parse(json);
            try { SynapMcConfig.write(cache, JsonParser.parseString(json)); }
            catch (IOException error) { SynapMcShared.error("[SynapMc] Could not cache popular feed: " + error); }
            return links;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not load popular feed: " + error.getMessage());
        }
        if (Files.isRegularFile(cache)) {
            try { return parse(Files.readString(cache)); }
            catch (Exception error) { SynapMcShared.error("[SynapMc] Could not read popular cache: " + error.getMessage()); }
        }
        return new ArrayList<>();
    }
}
