import com.riderword.plugin.AutoSpeak;
import com.riderword.plugin.Entry;
import com.riderword.plugin.RememberConfirmation;
import com.riderword.plugin.WordState;
import com.riderword.plugin.ParseResult;
import com.riderword.plugin.ReviewHistory;
import com.riderword.plugin.WordListParser;
import com.riderword.plugin.WordStore;

/** Standalone check for the word list rules. Run with the plugin classes on the classpath. */
public final class WordListCheck {

    public static void main(String[] args) {
        ParseResult parsed = new WordListParser().parse("""
                decay\t腐烂；衰退
                \s
                broken line without a separator
                abstraction | 抽象
                Decay | 重复的写法
                only-english |
                \t只有中文
                encapsulation\t封装
                """);

        check(parsed.entries.size() == 3, "keeps three words, got " + parsed.entries.size());
        check(parsed.skipped == 3, "skips three bad lines, got " + parsed.skipped);
        check(parsed.entries.get(0).word.equals("decay"), "keeps the first spelling");
        check(parsed.entries.get(0).chinese.equals("腐烂；衰退"), "reads the tab-separated gloss");
        check(parsed.entries.get(1).word.equals("abstraction"), "reads the pipe-separated word");

        WordStore store = new WordStore();
        store.load(parsed.entries, parsed.skipped, false, 0L, null);
        check(store.current().word.equals("decay"), "starts at the first word");
        store.move(-1);
        check(store.current().word.equals("encapsulation"), "previous wraps to the end");
        store.move(1);
        check(store.current().word.equals("decay"), "next wraps back to the start");

        store.setRandom(true);
        long seed = store.seed();
        String shown = store.current().word;
        store.move(1);
        store.move(1);
        store.move(1);
        check(store.current().word.equals(shown), "a random order still visits every word once per cycle");

        WordStore restored = new WordStore();
        restored.load(parsed.entries, parsed.skipped, true, seed, shown);
        check(restored.current().word.equals(shown), "the same seed reopens on the same word");
        restored.move(1);
        String neighbour = restored.current().word;
        restored.load(parsed.entries, parsed.skipped, true, seed, shown);
        restored.move(1);
        check(restored.current().word.equals(neighbour), "the same seed restores the same order");

        store.load(parsed.entries, parsed.skipped, false, 0L, "abstraction");
        check(store.current().word.equals("abstraction"), "reloading keeps the current word");

        Entry dropped = store.dropCurrent();
        check(dropped.word.equals("abstraction"), "remembered word is the one on screen");
        check(store.current().word.equals("encapsulation"), "remembering continues at the next word");
        check(store.size() == 2, "remembered word leaves the active list");

        check(store.currentNumber() == 2 && store.size() == 2, "progress follows removal: 2/2");
        store.move(1);
        check(store.currentNumber() == 1, "progress wraps forward");
        store.move(-1);
        check(store.currentNumber() == 2, "progress wraps backward");
        store.dropCurrent();
        check(store.currentNumber() == 1 && store.size() == 1, "removing last position wraps to 1/1");
        store.dropCurrent();
        check(store.currentNumber() == 0 && store.size() == 0, "empty list is 0/0");
        store.move(1);
        check(store.currentNumber() == 0, "empty navigation stays at zero");

        store.load(parsed.entries, 0, true, 42L, null);
        check(store.currentNumber() == 1, "random order starts at browse position one");
        store.move(1);
        check(store.currentNumber() == 2, "random order reports browse position, not source row");
        String savedKey = store.current().key();
        restored.load(parsed.entries, 0, true, 42L, savedKey);
        check(restored.currentNumber() == 2, "restoring random order restores progress");
        store.dropCurrent();
        check(store.currentNumber() == 2 && store.size() == 2, "random removal keeps next browse position");

        RememberConfirmation confirmation = new RememberConfirmation();
        Entry current = parsed.entries.get(0);
        check(!confirmation.click(current, WordState.ENGLISH), "first remembered click only opens confirmation");
        check(confirmation.isPending(current, WordState.ENGLISH), "confirmation keeps the current word pending");
        check(confirmation.click(current, WordState.ENGLISH), "second remembered click confirms removal");
        check(!confirmation.isPending(current, WordState.ENGLISH), "confirmed word is no longer pending");
        check(!confirmation.click(current, WordState.BOTH), "mode change starts a new confirmation");
        check(!confirmation.isPending(current, WordState.ENGLISH), "changing display mode cancels confirmation");

        for (int displayMode : new int[]{WordState.BOTH, WordState.ENGLISH}) {
            store.load(parsed.entries, 0, false, 42L, null);
            Entry first = store.current();
            check(!confirmation.click(first, displayMode), "preview does not approve removal");
            check(store.size() == 3 && store.current() == first && store.currentNumber() == 1,
                    "preview leaves current word and count unchanged");
            check(confirmation.isPending(first, displayMode), "preview stays until second click");
            if (confirmation.click(first, displayMode)) {
                store.dropCurrent();
            }
            check(store.size() == 2 && store.current() == parsed.entries.get(1),
                    "confirmation advances only once");
            check(!confirmation.isPending(store.current(), displayMode), "next word is not in preview mode");
        }
        confirmation.click(current, WordState.ENGLISH);
        check(!confirmation.click(parsed.entries.get(1), WordState.ENGLISH), "another word needs a fresh preview");
        confirmation.cancel();
        check(!confirmation.click(parsed.entries.get(1), WordState.ENGLISH), "cancel prevents immediate confirmation");
        check(!confirmation.isPending(new Entry(current.word, current.chinese), WordState.ENGLISH),
                "reloaded entry invalidates pending confirmation");
        check(!confirmation.click(null, WordState.ENGLISH), "empty list cannot be confirmed");
        check(!confirmation.isPending(null, WordState.ENGLISH), "empty list has no pending confirmation");

        ReviewHistory history = new ReviewHistory();
        history.reset(new Entry("decay", "腐烂；衰退"));
        history.visit(new Entry("abstraction", "抽象"));
        check(history.previous().word.equals("decay"), "history can return to the previous word");
        check(history.next().word.equals("abstraction"), "history can move forward again");

        store.load(parsed.entries, 0, false, 42L, null);
        check(store.dropCurrent().word.equals("decay"), "remember removes the displayed word");
        check(store.current().word.equals("abstraction"), "remember advances to the next active word");
        check(store.move(-1).word.equals("decay"), "previous reaches the remembered word");
        check(store.isCurrentRemembered(), "remembered word is shown as remembered");
        check(store.restoreCurrent().word.equals("decay"), "undo restores the displayed word");
        check(!store.isCurrentRemembered() && store.currentNumber() == 1 && store.size() == 3,
                "undo returns the word to its original active position");

        store.load(parsed.entries, 0, true, 42L, null);
        Entry randomRemembered = store.current();
        store.dropCurrent();
        check(store.move(-1).word.equals(randomRemembered.word), "random previous reaches the remembered word");
        store.restoreCurrent();
        check(store.current() == randomRemembered && store.size() == 3,
                "undo restores a word in its original random position");

        check(AutoSpeak.shouldSpeak(true, "one", "two"), "auto speaks when the next word changes");
        check(!AutoSpeak.shouldSpeak(false, "one", "two"), "the switch can disable auto speech");
        check(!AutoSpeak.shouldSpeak(true, "one", "one"), "the same word is not spoken again");
        check(!AutoSpeak.shouldSpeak(true, "", ""), "an empty list does not auto speak");

        System.out.println("All checks passed (parser, navigation, remembered words, progress, confirmation, undo history).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
