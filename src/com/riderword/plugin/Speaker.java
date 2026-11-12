package com.riderword.plugin;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Speaker {
    private static final Logger LOG = Logger.getInstance(Speaker.class);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "riderword-speech");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean SPEAKING = new AtomicBoolean();

    private Speaker() {
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static void speak(String word) {
        if (!supported() || word == null || word.isBlank()) {
            return;
        }
        String safe = word.replaceAll("[^\\p{IsLatin}0-9 '\\-]", " ").trim();
        if (safe.isEmpty() || !SPEAKING.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR.execute(() -> {
                try {
                    say(safe.replace("'", "''"));
                } finally {
                    SPEAKING.set(false);
                }
            });
        } catch (RuntimeException e) {
            SPEAKING.set(false);
            throw e;
        }
    }

    private static void say(String safe) {
        try {
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName System.Speech; " +
                            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                            "$v = @($s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'en-US*' }); " +
                            "if ($v.Count -gt 0) { $s.SelectVoice($v[0].VoiceInfo.Name) }; " +
                            "$s.Speak('" + safe + "')")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOG.warn("RiderWord could not speak", e);
        }
    }
}
