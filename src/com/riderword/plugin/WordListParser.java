package com.riderword.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads "english &lt;separator&gt; chinese" lines. A separator is a tab, or a '|' that is not
 * inside the word itself. Blank lines are ignored, malformed lines are counted and dropped,
 * and a repeated word (ignoring case) keeps the first occurrence.
 */
public final class WordListParser {

    public ParseResult parse(String text) {
        Map<String, Entry> kept = new LinkedHashMap<>();
        int skipped = 0;
        if (text != null) {
            for (String raw : text.split("\\R", -1)) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int split = splitAt(line);
                if (split < 0) {
                    skipped++;
                    continue;
                }
                String word = line.substring(0, split).trim();
                String chinese = line.substring(split + 1).trim();
                if (word.isEmpty() || chinese.isEmpty()) {
                    skipped++;
                    continue;
                }
                kept.putIfAbsent(word.toLowerCase(), new Entry(word, chinese));
            }
        }
        return new ParseResult(new ArrayList<>(kept.values()), skipped);
    }

    public static String format(List<Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(entry.word).append(" | ").append(entry.chinese);
        }
        return text.toString();
    }

    private static int splitAt(String line) {
        int tab = line.indexOf('\t');
        if (tab >= 0) {
            return tab;
        }
        return line.indexOf('|');
    }
}
