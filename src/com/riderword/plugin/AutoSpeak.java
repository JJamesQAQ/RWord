package com.riderword.plugin;

/** Decides when moving forward should start pronunciation. */
public final class AutoSpeak {

    private AutoSpeak() {
    }

    public static boolean shouldSpeak(boolean enabled, String previousKey, String currentKey) {
        return enabled && currentKey != null && !currentKey.isEmpty() && !currentKey.equals(previousKey);
    }
}
