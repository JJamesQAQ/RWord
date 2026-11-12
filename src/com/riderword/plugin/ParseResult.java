package com.riderword.plugin;

import java.util.List;

/** Outcome of reading a word list: the kept entries and how many lines were skipped. */
public final class ParseResult {
    public final List<Entry> entries;
    public final int skipped;

    public ParseResult(List<Entry> entries, int skipped) {
        this.entries = entries;
        this.skipped = skipped;
    }
}
