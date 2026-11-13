package com.riderword.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads sentence pairs separated by a blank line. Inside a pair, lines before the first Han
 * character are English and the rest is Chinese. Each side is joined with spaces so it fits
 * one status-bar line.
 */
public final class SentenceParser {

    public ParseResult parse(String text) {
        Map<String, Entry> kept = new LinkedHashMap<>();
        int skipped = 0;
        if (text != null) {
            StringBuilder english = new StringBuilder();
            StringBuilder chinese = new StringBuilder();
            boolean chineseStarted = false;
            boolean any = false;
            for (String raw : text.split("\\R", -1)) {
                String line = raw.strip();
                if (line.isEmpty()) {
                    skipped += finish(kept, english, chinese, any);
                    english.setLength(0);
                    chinese.setLength(0);
                    chineseStarted = false;
                    any = false;
                    continue;
                }
                any = true;
                // ponytail: first Han line starts Chinese. A Han character in the English half splits early.
                if (!chineseStarted && !hasHan(line)) {
                    append(english, line);
                } else {
                    chineseStarted = true;
                    append(chinese, line);
                }
            }
            skipped += finish(kept, english, chinese, any);
        }
        return new ParseResult(new ArrayList<>(kept.values()), skipped);
    }

    private static int finish(Map<String, Entry> kept, StringBuilder english, StringBuilder chinese, boolean any) {
        if (!any) {
            return 0;
        }
        if (english.isEmpty() || chinese.isEmpty()) {
            return 1;
        }
        kept.putIfAbsent(english.toString().toLowerCase(), new Entry(english.toString(), chinese.toString()));
        return 0;
    }

    private static void append(StringBuilder part, String line) {
        if (!part.isEmpty()) {
            part.append(' ');
        }
        part.append(line);
    }

    private static boolean hasHan(String line) {
        return line.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
