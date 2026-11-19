package com.riderword.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads sentence pairs separated by a blank line. Inside a pair, lines before the first Han
 * character are English and the rest is Chinese. A new Latin-only line after Chinese starts the
 * next pair, so pasted pairs do not require a blank line between them.
 */
public final class SentenceParser {

    /** One editable sentence pair; either side may be empty while the user is reviewing it. */
    public record Draft(String english, String chinese) {
    }

    public ParseResult parse(String text) {
        Map<String, Entry> kept = new LinkedHashMap<>();
        int skipped = 0;
        for (Draft draft : parseDrafts(text)) {
            if (draft.english().isEmpty() || draft.chinese().isEmpty()) {
                skipped++;
                continue;
            }
            kept.putIfAbsent(draft.english().toLowerCase(),
                    new Entry(draft.english(), draft.chinese()));
        }
        return new ParseResult(new ArrayList<>(kept.values()), skipped);
    }

    /** Parses every non-empty block, including incomplete pairs for the settings editor. */
    public List<Draft> parseDrafts(String text) {
        List<Draft> drafts = new ArrayList<>();
        if (text == null) {
            return drafts;
        }
        StringBuilder english = new StringBuilder();
        StringBuilder chinese = new StringBuilder();
        boolean chineseStarted = false;
        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                finish(drafts, english, chinese);
                english.setLength(0);
                chinese.setLength(0);
                chineseStarted = false;
                continue;
            }
            // Once Chinese started, a Latin-only line is the next English sentence even without a blank line.
            if (chineseStarted && !hasHan(line)) {
                finish(drafts, english, chinese);
                english.setLength(0);
                chinese.setLength(0);
                chineseStarted = false;
            }
            if (!chineseStarted && !hasHan(line)) {
                append(english, line);
            } else {
                chineseStarted = true;
                append(chinese, line);
            }
        }
        finish(drafts, english, chinese);
        return drafts;
    }

    /** Formats the editable rows while preserving rows with only one side or neither side. */
    public static String formatDrafts(List<Draft> drafts) {
        StringBuilder text = new StringBuilder();
        for (Draft draft : drafts) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            if (draft.english() != null) {
                text.append(draft.english());
            }
            text.append('\n');
            if (draft.chinese() != null) {
                text.append(draft.chinese());
            }
        }
        return text.toString();
    }

    private static void finish(List<Draft> drafts, StringBuilder english, StringBuilder chinese) {
        if (!english.isEmpty() || !chinese.isEmpty()) {
            drafts.add(new Draft(english.toString(), chinese.toString()));
        }
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
