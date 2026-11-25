package com.synapmc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Checks the same PATH-based executable used by repository actions once at startup. */
final class GitInstallation {
    static final String DOWNLOAD_URL = "https://git-scm.com/downloads";
    private static volatile Boolean available;
    private static boolean started;

    private GitInstallation() { }

    static Boolean available() { return available; }

    static synchronized void startCheck() {
        if (started) return;
        started = true;
        Thread worker = new Thread(() -> available = probe(new ProcessBuilder("git", "--version"), 5000),
                "SynapMc Git installation check");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean probe(ProcessBuilder builder, long timeoutMillis) {
        Process process = null;
        try {
            process = builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            return process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) && process.exitValue() == 0;
        } catch (IOException | SecurityException error) {
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
