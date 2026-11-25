package com.synapmc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Executes Git without a shell, interactive credentials, or secrets in command arguments. */
final class GitHubGit {
    private final Map<String, String> environment;
    GitHubGit() { this(Map.of()); }
    GitHubGit(Map<String, String> environment) { this.environment = Map.copyOf(environment); }

    record Result(int code, String output) { }
    record Credential(String host, String token) { }
    String run(Path folder, Map<String, String> extra, String token, String... args) throws IOException {
        return run(folder, extra, new Credential("github.com", token), args);
    }
    String run(Path folder, Map<String, String> extra, Credential credential, String... args) throws IOException {
        Result result = call(folder, extra, credential, args);
        if (result.code != 0) throw new IOException(result.output.isBlank() ? "Git failed (" + result.code + ")." : result.output.strip());
        return result.output;
    }

    String runInput(Path folder, Map<String, String> extra, Credential credential, byte[] input, String... args) throws IOException {
        Result result = call(folder, extra, credential, input, args);
        if (result.code != 0) throw new IOException(result.output.isBlank() ? "Git failed (" + result.code + ")." : result.output.strip());
        return result.output;
    }

    Result call(Path folder, Map<String, String> extra, String token, String... args) throws IOException {
        return call(folder, extra, new Credential("github.com", token), args);
    }
    Result call(Path folder, Map<String, String> extra, Credential credential, String... args) throws IOException {
        return call(folder, extra, credential, new byte[0], args);
    }
    private Result call(Path folder, Map<String, String> extra, Credential credential, byte[] input, String... args) throws IOException {
        GitCredentials.host(credential.host);
        String token = credential.token;
        List<String> command = new ArrayList<>(List.of("git", "-c", "credential.helper=", "-c", "core.hooksPath=" +
                Path.of(System.getProperty("java.io.tmpdir"), "synapmc-no-hooks").toAbsolutePath(),
                "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false"));
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        // Do not inherit an enclosing repository's index, worktree, tracing, or askpass.
        env.keySet().removeIf(key -> key.startsWith("GIT_"));
        env.putAll(environment);
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "");
        env.put("SSH_ASKPASS", "");
        env.put("GIT_OPTIONAL_LOCKS", "0");
        env.put("LC_ALL", "C.UTF-8");
        env.putAll(extra);
        String encoded = "";
        if (!token.isEmpty()) {
            String username = credential.host.equals("gitlab.com") ? "oauth2" : "x-access-token";
            encoded = Base64.getEncoder().encodeToString((username + ":" + token).getBytes(StandardCharsets.UTF_8));
            env.put("GIT_CONFIG_COUNT", "2");
            env.put("GIT_CONFIG_KEY_0", "http.https://" + credential.host + "/.extraheader");
            env.put("GIT_CONFIG_VALUE_0", "");
            env.put("GIT_CONFIG_KEY_1", "http.https://" + credential.host + "/.extraheader");
            env.put("GIT_CONFIG_VALUE_1", "Authorization: Basic " + encoded);
        }
        Process process;
        try { process = builder.start(); }
        catch (IOException error) { throw new IOException("Install Git and make it available on PATH, then reopen the GitHub page."); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean[] overflow = {false};
        Thread reader = new Thread(() -> {
            try (InputStream stdout = process.getInputStream()) {
                byte[] buffer = new byte[8192]; int size;
                while ((size = stdout.read(buffer)) != -1) {
                    if (output.size() + size <= 8 * 1024 * 1024) output.write(buffer, 0, size);
                    else overflow[0] = true;
                }
            } catch (IOException ignored) { }
        }, "SynapMc Git output");
        reader.setDaemon(true); reader.start();
        Thread writer = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) { stdin.write(input); }
            catch (IOException ignored) { }
        }, "SynapMc Git input");
        writer.setDaemon(true); writer.start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git timed out. Check the connection and try again.");
            }
            reader.join(5000);
            writer.join(5000);
            if (reader.isAlive() || writer.isAlive() || overflow[0]) throw new IOException("Git input/output exceeded the supported limit.");
            String text = output.toString(StandardCharsets.UTF_8);
            if (!token.isEmpty()) text = text.replace(token, "[token]").replace(encoded, "[credentials]");
            return new Result(process.exitValue(), text);
        } catch (InterruptedException error) {
            process.destroyForcibly(); Thread.currentThread().interrupt();
            throw new IOException("Git operation interrupted.");
        }
    }
}
