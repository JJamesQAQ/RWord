package com.riderword.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

import java.util.ArrayList;
import java.util.List;

/** Holds the word list and the sentence list shared by every open project. */
@Service(Service.Level.APP)
public final class WordService {

    private final WordStore words = new WordStore();
    private final WordStore sentences = new WordStore();

    public WordService() {
        reload();
    }

    public static WordService getInstance() {
        return ApplicationManager.getApplication().getService(WordService.class);
    }

    /** The list the status bar is browsing right now. */
    public WordStore store() {
        return WordState.getInstance().reading == WordState.SENTENCES ? sentences : words;
    }

    public WordStore wordStore() {
        return words;
    }

    public WordStore sentenceStore() {
        return sentences;
    }

    public void reload() {
        WordState state = WordState.getInstance();
        ParseResult parsed = new WordListParser().parse(state.wordList);
        words.load(parsed.entries, parsed.skipped, state.random, state.seed, state.currentKey);
        ParseResult sentenceParsed = new SentenceParser().parse(state.sentenceList);
        // Sentences ignore the word-list browse radio and stay in pasted order.
        sentences.load(sentenceParsed.entries, sentenceParsed.skipped, false, 1L, state.sentenceKey);
        remember();
    }

    /** The word stays in the list. A second click on the same English key removes the copy. */
    public void toggleFavorite() {
        WordState state = WordState.getInstance();
        state.favoriteList = toggleFavorite(state.favoriteList, words.current());
    }

    public boolean currentFavorite() {
        Entry current = words.current();
        return current != null && favoriteContains(WordState.getInstance().favoriteList, current.key());
    }

    public static String toggleFavorite(String favoriteList, Entry current) {
        if (current == null) {
            return favoriteList == null ? "" : favoriteList;
        }
        List<Entry> kept = new ArrayList<>();
        boolean present = false;
        for (Entry entry : new WordListParser().parse(favoriteList).entries) {
            if (entry.key().equals(current.key())) {
                present = true;
            } else {
                kept.add(entry);
            }
        }
        if (!present) {
            kept.add(current);
        }
        return WordListParser.format(kept);
    }

    public static boolean favoriteContains(String favoriteList, String key) {
        if (key == null) {
            return false;
        }
        for (Entry entry : new WordListParser().parse(favoriteList).entries) {
            if (entry.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Moves the current word into the remembered list and shows the next one. */
    public void markRemembered() {
        Entry removed = words.dropCurrent();
        if (removed == null) {
            return;
        }
        WordState state = WordState.getInstance();
        state.wordList = WordListParser.format(words.entries());
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
        Entry restored = words.restoreCurrent();
        if (restored == null) {
            return;
        }
        WordState state = WordState.getInstance();
        state.wordList = WordListParser.format(words.entries());
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

    /** Writes both positions back so the next launch reopens on the same word and sentence. */
    public void remember() {
        WordState state = WordState.getInstance();
        Entry word = words.current();
        state.currentKey = word == null ? "" : word.key();
        state.random = words.isRandom();
        state.seed = words.seed();
        Entry sentence = sentences.current();
        state.sentenceKey = sentence == null ? "" : sentence.key();
    }
}
