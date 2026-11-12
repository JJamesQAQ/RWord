package com.riderword.plugin;

/** One vocabulary row: the English word exactly as written, plus its Chinese gloss. */
public final class Entry {
    public final String word;
    public final String chinese;

    public Entry(String word, String chinese) {
        this.word = word;
        this.chinese = chinese;
    }

    /** Key used for de-duplication and for remembering the current word across reloads. */
    public String key() {
        return word.toLowerCase();
    }
}
