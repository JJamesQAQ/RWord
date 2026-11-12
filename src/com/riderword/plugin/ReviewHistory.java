package com.riderword.plugin;

import java.util.ArrayList;
import java.util.List;

public final class ReviewHistory {
    private final List<Entry> visits = new ArrayList<>();
    private int cursor;

    public void reset(Entry current) {
        visits.clear();
        if (current == null) {
            cursor = -1;
            return;
        }
        visits.add(current);
        cursor = 0;
    }

    public Entry current() {
        return cursor < 0 || cursor >= visits.size() ? null : visits.get(cursor);
    }

    public boolean hasPrevious() { return cursor > 0; }
    public boolean hasNext() { return cursor >= 0 && cursor + 1 < visits.size(); }

    public Entry previous() {
        if (hasPrevious()) {
            cursor--;
        }
        return current();
    }

    public Entry next() {
        if (hasNext()) {
            cursor++;
        }
        return current();
    }

    public void visit(Entry entry) {
        if (entry == null) {
            return;
        }
        if (cursor >= 0) {
            visits.subList(cursor + 1, visits.size()).clear();
        }
        visits.add(entry);
        cursor = visits.size() - 1;
    }

    public boolean reviewing(Entry active) {
        Entry shown = current();
        return shown != null && (active == null || !shown.key().equals(active.key()));
    }
}
