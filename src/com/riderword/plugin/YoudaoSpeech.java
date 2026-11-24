package com.riderword.plugin;

import com.intellij.openapi.application.PathManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the signed Youdao request and extracts its documented pronunciation URL. */
public final class YoudaoSpeech {
    private static final Pattern SPEAK_URL = Pattern.compile("\"speakUrl\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private YoudaoSpeech() {
    }

    public static String requestBody(String text, String appKey, String appSecret, String salt, String time) {
        String query = text == null ? "" : text.strip();
        String key = appKey == null ? "" : appKey.strip();
        String secret = appSecret == null ? "" : appSecret.strip();
        return "q=" + encode(query)
                + "&from=en&to=zh-CHS"
                + "&appKey=" + encode(key)
                + "&salt=" + encode(salt)
                + "&sign=" + encode(signature(key, query, salt, time, secret))
                + "&signType=v3&curtime=" + encode(time)
                + "&voice=0&strict=true";
    }

    public static String signature(String appKey, String query, String salt, String time, String appSecret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (appKey + input(query) + salt + time + appSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String input(String query) {
        if (query.length() <= 20) {
            return query;
        }
        return query.substring(0, 10) + query.length() + query.substring(query.length() - 10);
    }

    public static String speakUrl(String response) {
        if (response == null) {
            return "";
        }
        Matcher matcher = SPEAK_URL.matcher(response);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : "";
    }

    public static String cacheKey(String text) {
        return signature("", text == null ? "" : text.strip().toLowerCase(), "", "", "");
    }

    public static Path cacheFile(String text) {
        return Paths.get(PathManager.getSystemPath(), "riderword-speech", cacheKey(text) + ".mp3");
    }

    public static String salt() {
        return UUID.randomUUID().toString();
    }

    public static String time() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
