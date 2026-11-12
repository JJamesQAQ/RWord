package com.riderword.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/** Holds the one word list shared by every open project and reloads it from the saved settings. */
@Service(Service.Level.APP)
public final class WordService {

    private final WordStore store = new WordStore();

    public WordService() {
        reload();
    }

    public static WordService getInstance() {
        return ApplicationManager.getApplication().getService(WordService.class);
    }

    public WordStore store() {
        return store;
    }

    public void reload() {
        WordState state = WordState.getInstance();
        ParseResult parsed = new WordListParser().parse(state.wordList);
        store.load(parsed.entries, parsed.skipped, state.random, state.seed, state.currentKey);
        remember();
    }

    /** Moves the current word into the remembered list and shows the next one. */
    public void markRemembered() {
        Entry removed = store.dropCurrent();
        if (removed == null) {
            return;
        }
        WordState state = WordState.getInstance();
        state.wordList = WordListParser.format(store.entries());
        if (!rememberedKeys(state.rememberedList).contains(removed.key())) {
            String line = removed.word + " | " + removed.chinese;
            state.rememberedList = state.rememberedList == null || state.rememberedList.isBlank()
                    ? line
                    : state.rememberedList.stripTrailing() + "\n" + line;
        }
        remember();
    }

    /** Restores a remembered entry as the current active word and removes it from the remembered list. */
    public void unremember() {
        Entry restored = store.restoreCurrent();
        if (restored == null) {
            return;
        }
        WordState state = WordState.getInstance();
        state.wordList = WordListParser.format(store.entries());
        java.util.List<Entry> remembered = new java.util.ArrayList<>();
        for (Entry entry : new WordListParser().parse(state.rememberedList).entries) {
            if (!entry.key().equals(restored.key())) {
                remembered.add(entry);
            }
        }
        state.rememberedList = WordListParser.format(remembered);
        remember();
    }

    private static java.util.Set<String> rememberedKeys(String text) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Entry entry : new WordListParser().parse(text).entries) {
            keys.add(entry.key());
        }
        return keys;
    }

    /** Writes the position back so the next launch opens on the same word. */
    public void remember() {
        WordState state = WordState.getInstance();
        Entry current = store.current();
        state.currentKey = current == null ? "" : current.key();
        state.random = store.isRandom();
        state.seed = store.seed();
    }
}
