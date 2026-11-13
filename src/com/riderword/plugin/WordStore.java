package com.riderword.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class WordStore {
    // Keep removed entries in session order so undo restores their exact place, even in random mode.
    private final List<Entry> entries = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private final Set<String> remembered = new HashSet<>();
    // The word just marked remembered. The next "previous" shows it once; any other move drops it.
    private Entry justRemembered;
    private boolean random;
    private int position;
    private long seed;
    private int skipped;

    public void load(List<Entry> parsed, int skippedLines, boolean randomMode, long savedSeed, String currentKey) {
        entries.clear();
        entries.addAll(parsed);
        remembered.clear();
        skipped = skippedLines;
        random = randomMode;
        seed = savedSeed != 0 ? savedSeed : new Random().nextLong();
        rebuildOrder();
        position = indexOf(currentKey);
        justRemembered = null;
    }

    public boolean isEmpty() { return size() == 0; }
    public int size() { return entries.size() - remembered.size(); }
    public int skipped() { return skipped; }
    public boolean isRandom() { return random; }
    public long seed() { return seed; }
    public Entry current() {
        return order.isEmpty() ? null : entries.get(order.get(position));
    }

    public boolean canGoBack() { return justRemembered != null || !isEmpty(); }
    public boolean canGoForward() { return !isEmpty(); }

    public boolean isCurrentRemembered() {
        return current() != null && remembered.contains(current().key());
    }

    public Entry move(int direction) {
        if (direction < 0 && justRemembered != null) {
            select(justRemembered);
            justRemembered = null;
            return current();
        }
        justRemembered = null;
        Entry candidate = nextActive(direction < 0 ? -1 : 1);
        if (candidate != null) {
            select(candidate);
        }
        return current();
    }

    public Entry dropCurrent() {
        Entry removed = current();
        if (removed == null || isCurrentRemembered()) {
            return null;
        }
        remembered.add(removed.key());
        justRemembered = removed;
        Entry next = nextActive(1);
        if (next != null) {
            select(next);
        }
        return removed;
    }

    public Entry restoreCurrent() {
        Entry restored = current();
        if (restored == null || !remembered.remove(restored.key())) {
            return null;
        }
        justRemembered = null;
        select(restored);
        return restored;
    }

    public int currentNumber() {
        if (current() == null || isCurrentRemembered()) {
            return 0;
        }
        int number = 0;
        for (int index : order) {
            Entry entry = entries.get(index);
            if (!remembered.contains(entry.key())) {
                number++;
                if (entry.key().equals(current().key())) {
                    return number;
                }
            }
        }
        return 0;
    }

    public List<Entry> entries() {
        return entries.stream().filter(e -> !remembered.contains(e.key())).toList();
    }

    public String resumeKey() {
        Entry entry = isCurrentRemembered() ? nextActive(1) : current();
        return entry == null ? "" : entry.key();
    }

    public void setRandom(boolean randomMode) {
        if (random == randomMode) {
            return;
        }
        String key = current() == null ? "" : current().key();
        random = randomMode;
        if (random) {
            seed = new Random().nextLong();
        }
        rebuildOrder();
        position = indexOf(key);
        justRemembered = null;
    }

    private Entry nextActive(int direction) {
        for (int step = 1; step <= order.size(); step++) {
            Entry entry = entries.get(order.get(Math.floorMod(position + direction * step, order.size())));
            if (!remembered.contains(entry.key())) {
                return entry;
            }
        }
        return null;
    }

    private void select(Entry entry) {
        if (entry != null) {
            position = indexOf(entry.key());
        }
    }

    private void rebuildOrder() {
        order.clear();
        for (int i = 0; i < entries.size(); i++) {
            order.add(i);
        }
        if (random) {
            Collections.shuffle(order, new Random(seed));
        }
    }

    private int indexOf(String key) {
        for (int i = 0; i < order.size(); i++) {
            if (entries.get(order.get(i)).key().equals(key)) {
                return i;
            }
        }
        return 0;
    }
}
