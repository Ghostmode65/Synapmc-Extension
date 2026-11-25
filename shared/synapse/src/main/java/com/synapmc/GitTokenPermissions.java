package com.synapmc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Advisory checks only: a scope lookup failure never changes the saved token or blocks Git. */
final class GitTokenPermissions {
    record Response(int status, String scopes, String body) { }
    record Check(String message, boolean warning) { }
    @FunctionalInterface interface Transport { Response get(String host, String token) throws Exception; }
    private final Transport transport;
    GitTokenPermissions() { this(GitTokenPermissions::request); }
    GitTokenPermissions(Transport transport) { this.transport = transport; }
    Check inspect(String host, String token) {
        if (token.isEmpty()) return new Check("No token saved.", false);
        try {
            Response response = transport.get(host, token);
            if (response.status == 401) return new Check("Token saved; the permission endpoint rejected it. Check expiry and repository access.", true);
            if (response.status != 200) return new Check("Token saved. Permission details unavailable (HTTP " + response.status + ").", false);
            if (host.equals("github.com")) {
                if (token.startsWith("github_pat_"))
                    return new Check("Fine-grained token saved. Verify selected repositories and Contents read/write in GitHub settings.", false);
                Set<String> scopes = scopes(response.scopes);
                Set<String> extra = new TreeSet<>(scopes); extra.remove("repo");
                if (!extra.isEmpty()) return new Check("Warning: extra GitHub permissions: " + String.join(", ", extra) + ". Prefer a fine-grained repository token.", true);
                return new Check("Classic token saved. Prefer a fine-grained token limited to selected repositories.", true);
            }
            JsonObject body = JsonParser.parseString(response.body).getAsJsonObject();
            if (body.has("active") && !body.get("active").getAsBoolean() || body.has("revoked") && body.get("revoked").getAsBoolean())
                return new Check("Token saved; GitLab reports it inactive or revoked.", true);
            if (!body.has("scopes")) return new Check("Token saved. GitLab did not report standard token scopes.", false);
            Set<String> scopes = new TreeSet<>();
            for (var value : body.getAsJsonArray("scopes")) scopes.addAll(scopes(value.getAsString()));
            Set<String> extra = new TreeSet<>(scopes); extra.removeAll(Set.of("read_repository", "write_repository"));
            if (!extra.isEmpty()) return new Check("Warning: extra GitLab permissions: " + String.join(", ", extra) + ". Repository read/write is enough for Git operations.", true);
            if (!scopes.contains("write_repository")) return new Check("Token saved. No write_repository scope was reported; pushes may be unavailable.", true);
            return new Check("GitLab repository token saved. Use a project access token to limit access to one project.", false);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Check("Token saved. Permission details could not be checked; Git operations can still use it.", false);
        }
    }
    private static Set<String> scopes(String value) {
        Set<String> result = new TreeSet<>();
        for (String scope : value.split(",")) if (scope.trim().matches("[a-z_:]+")) result.add(scope.trim());
        return result;
    }
    private static Response request(String host, String token) throws Exception {
        GitCredentials.host(host);
        URI uri = URI.create(host.equals("github.com") ? "https://api.github.com/user" : "https://gitlab.com/api/v4/personal_access_tokens/self");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "SynapMc").header("Accept", "application/json");
        if (host.equals("github.com")) request.header("Authorization", "Bearer " + token);
        else request.header("PRIVATE-TOKEN", token);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.headers().firstValue("X-OAuth-Scopes").orElse(""), response.body());
    }
}
