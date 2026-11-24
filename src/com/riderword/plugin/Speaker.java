package com.riderword.plugin;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class Speaker {
    private static final Logger LOG = Logger.getInstance(Speaker.class);
    private static final Speaker INSTANCE = new Speaker(Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "riderword-speech");
        thread.setDaemon(true);
        return thread;
    }), Speaker::startProcess, Speaker::youdaoAudio);

    private final Executor executor;
    private final Function<String, Process> launcher;
    private final Function<String, byte[]> youdao;
    private Request current;

    Speaker(Executor executor, Function<String, Process> launcher, Function<String, byte[]> youdao) {
        this.executor = executor;
        this.launcher = launcher;
        this.youdao = youdao;
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static void speak(String word) {
        if (supported()) {
            INSTANCE.speakOrStop(word);
        }
    }

    public static void replay(String word) {
        if (supported()) {
            INSTANCE.speakOrReplay(word);
        }
    }

    synchronized void speakOrStop(String word) {
        speakOrStop(word, true);
    }

    synchronized void speakOrReplay(String word) {
        speakOrStop(word, false);
    }

    private synchronized void speakOrStop(String word, boolean toggle) {
        if (word == null || word.isBlank()) {
            return;
        }
        String safe = word.replaceAll("[^\\p{IsLatin}0-9 '\\-]", " ").trim();
        if (safe.isEmpty()) {
            return;
        }
        boolean stop = toggle && current != null && current.word.equalsIgnoreCase(word.trim())
                && (current.process == null || current.process.isAlive());
        if (current != null && current.process != null) {
            current.process.destroyForcibly();
        }
        Request request = stop ? null : new Request(word.trim());
        current = request;
        if (request != null) {
            try {
                WordState state = WordState.getInstance();
                executor.execute(state.youdaoSpeech ? () -> sayYoudao(request, safe) : () -> say(request, safe));
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

    private void sayYoudao(Request request, String safe) {
        Process process = null;
        Path audio = null;
        try {
            Path cached = YoudaoSpeech.cacheFile(safe);
            if (!Files.isRegularFile(cached) || Files.size(cached) == 0) {
                byte[] data = youdao.apply(safe);
                synchronized (this) {
                    if (current != request || data == null || data.length == 0) {
                        return;
                    }
                }
                Files.createDirectories(cached.getParent());
                Path writing = cached.resolveSibling(cached.getFileName() + ".part");
                Files.write(writing, data);
                try {
                    Files.move(writing, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(writing, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            synchronized (this) {
                if (current != request) {
                    return;
                }
            }
            audio = cached;
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName PresentationCore; " +
                            "$done = New-Object System.Threading.ManualResetEvent($false); " +
                            "$p = New-Object System.Windows.Media.MediaPlayer; " +
                            "$p.add_MediaEnded({ $done.Set() }); $p.add_MediaFailed({ $done.Set() }); " +
                            "$p.Open([Uri]'" + audio.toAbsolutePath().toString().replace("'", "''") + "'); " +
                            "$p.Play(); [void]$done.WaitOne(30000); $p.Close()")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            synchronized (this) {
                request.process = process;
                if (current != request) {
                    process.destroyForcibly();
                }
            }
            process.waitFor();
        } catch (IOException e) {
            LOG.warn("RiderWord could not play Youdao speech", e);
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

    private static byte[] youdaoAudio(String text) {
        WordState state = WordState.getInstance();
        if (state.youdaoAppKey.isBlank() || state.youdaoAppSecret.isBlank()) {
            return null;
        }
        try {
            String body = YoudaoSpeech.requestBody(text, state.youdaoAppKey, state.youdaoAppSecret,
                    YoudaoSpeech.salt(), YoudaoSpeech.time());
            HttpURLConnection api = (HttpURLConnection) URI.create("https://openapi.youdao.com/api").toURL().openConnection();
            api.setConnectTimeout(5000);
            api.setReadTimeout(8000);
            api.setRequestMethod("POST");
            api.setDoOutput(true);
            api.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (java.io.OutputStream output = api.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            String response = new String(read(api), StandardCharsets.UTF_8);
            String url = YoudaoSpeech.speakUrl(response);
            if (!url.startsWith("https://")) {
                LOG.warn("Youdao returned no speech URL");
                return null;
            }
            HttpURLConnection audio = (HttpURLConnection) URI.create(url).toURL().openConnection();
            audio.setConnectTimeout(5000);
            audio.setReadTimeout(8000);
            return read(audio);
        } catch (Exception e) {
            LOG.warn("RiderWord could not download Youdao speech", e);
            return null;
        }
    }

    private static byte[] read(HttpURLConnection connection) throws IOException {
        try (InputStream input = connection.getResponseCode() < 400 ? connection.getInputStream() : connection.getErrorStream()) {
            return input == null ? new byte[0] : input.readAllBytes();
        } finally {
            connection.disconnect();
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
