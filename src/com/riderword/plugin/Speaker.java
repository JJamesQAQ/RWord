package com.riderword.plugin;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class Speaker {
    private static final Logger LOG = Logger.getInstance(Speaker.class);
    private static final Speaker INSTANCE = new Speaker(Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "riderword-speech");
        thread.setDaemon(true);
        return thread;
    }), Speaker::startProcess);

    private final Executor executor;
    private final Function<String, Process> launcher;
    private Request current;

    Speaker(Executor executor, Function<String, Process> launcher) {
        this.executor = executor;
        this.launcher = launcher;
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static void speak(String word) {
        if (supported()) {
            INSTANCE.speakOrStop(word);
        }
    }

    synchronized void speakOrStop(String word) {
        if (word == null || word.isBlank()) {
            return;
        }
        String safe = word.replaceAll("[^\\p{IsLatin}0-9 '\\-]", " ").trim();
        if (safe.isEmpty()) {
            return;
        }
        boolean stop = current != null && current.word.equalsIgnoreCase(word.trim())
                && (current.process == null || current.process.isAlive());
        if (current != null && current.process != null) {
            current.process.destroyForcibly();
        }
        Request request = stop ? null : new Request(word.trim());
        current = request;
        if (request != null) {
            try {
                executor.execute(() -> say(request, safe));
            } catch (RuntimeException e) {
                if (current == request) {
                    current = null;
                }
                throw e;
            }
        }
    }

    private void say(Request request, String safe) {
        Process process = null;
        try {
            synchronized (this) {
                if (current != request) {
                    return;
                }
            }
            process = launcher.apply(safe);
            if (process == null) {
                return;
            }
            synchronized (this) {
                request.process = process;
                // A click may have cancelled this request while PowerShell was starting.
                if (current != request) {
                    process.destroyForcibly();
                }
            }
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            synchronized (this) {
                if (current == request) {
                    current = null;
                }
            }
        }
    }

    private static Process startProcess(String safe) {
        try {
            return new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName System.Speech; " +
                            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                            "$v = @($s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'en-US*' }); " +
                            "if ($v.Count -gt 0) { $s.SelectVoice($v[0].VoiceInfo.Name) }; " +
                            "$s.Speak('" + safe.replace("'", "''") + "')")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            LOG.warn("RiderWord could not speak", e);
            return null;
        }
    }

    private static final class Request {
        final String word;
        Process process;

        Request(String word) {
            this.word = word;
        }
    }
}
