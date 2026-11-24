import com.riderword.plugin.AutoSpeak;
import com.riderword.plugin.Entry;
import com.riderword.plugin.RememberConfirmation;
import com.riderword.plugin.RiderWordWidget;
import com.riderword.plugin.RiderWordWidget.SentenceWordLabel;
import com.riderword.plugin.WordService;
import com.riderword.plugin.WordState;
import com.riderword.plugin.ParseResult;
import com.riderword.plugin.ReviewHistory;
import com.riderword.plugin.SentenceParser;
import com.riderword.plugin.WordListParser;
import com.riderword.plugin.WordStore;
import com.riderword.plugin.YoudaoSpeech;
import javax.swing.SwingUtilities;
import java.util.List;
/** Standalone check for the word list rules. Run with the plugin classes on the classpath. */
public final class WordListCheck {

    public static void main(String[] args) throws Exception {
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
        store.move(-1);
        check(store.current().word.equals("abstraction"), "previous keeps walking backward");
        store.move(1);
        check(store.current().word.equals("encapsulation"), "next leaves the previous word");
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
        check(RiderWordWidget.displayAfterModeClick(WordState.ENGLISH, true) == WordState.ENGLISH,
                "EN+ shown by the Got preview switches to EN");
        check(RiderWordWidget.displayAfterModeClick(WordState.BOTH, false) == WordState.ENGLISH,
                "EN+ switches to EN");
        check(RiderWordWidget.displayAfterModeClick(WordState.ENGLISH, false) == WordState.BOTH,
                "EN switches to EN+");

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

        store.load(parsed.entries, 0, false, 42L, null);
        store.dropCurrent();
        check(store.move(-1).word.equals("decay") && store.isCurrentRemembered(),
                "previous just after remember shows that word");
        check(store.move(-1).word.equals("encapsulation") && !store.isCurrentRemembered(),
                "another previous continues backward instead of jumping back");
        check(store.move(-1).word.equals("abstraction"), "previous keeps moving after the remembered word");

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
        String signature = YoudaoSpeech.signature("app", "good", "salt", "100", "secret");
        check(signature.equals(YoudaoSpeech.signature("app", "good", "salt", "100", "secret")),
                "Youdao signs the same request consistently");
        check(YoudaoSpeech.input("abcdefghijklmnopqrstu").equals("abcdefghij21lmnopqrstu"),
                "Youdao truncates text longer than twenty characters");
        check(YoudaoSpeech.requestBody("good", "app", "secret", "salt", "100").contains("from=en&to=zh-CHS"),
                "Youdao requests English speech");
        check(YoudaoSpeech.speakUrl("{\"errorCode\":\"0\",\"speakUrl\":\"https:\\/\\/example.test\\/a.mp3\"}")
                .equals("https://example.test/a.mp3"), "Youdao speech URL is extracted");
        check(YoudaoSpeech.speakUrl("{\"errorCode\":\"110\"}").isEmpty(), "a failed Youdao response stays silent");
        check(YoudaoSpeech.cacheKey("Illusion").equals(YoudaoSpeech.cacheKey(" illusion ")),
                "the same word reuses one cached pronunciation");

        ParseResult sentences = new SentenceParser().parse("""
                In order to work through more of the Cybertruck order backlog, production will be extended through the end of summer 2027
                为了消化更多的 Cybertruck 订单积压，生产将延长至 2027 年夏末。

                We would be happy to take the compute back. No problem.
                But I think the opposite is going to happen.
                我们很乐意把算力拿回来。没问题。
                但我认为接下来会发生相反的情况。

                Much appreciated, Mississippi ❤️
                非常感谢，密西西比 ❤️

                Robotaxi works 168 hours a week
                Robotaxi 每周运行 168 小时。

                Only English
                still English

                In order to work through more of the Cybertruck order backlog, production will be extended through the end of summer 2027
                重复的一句
                """);
        check(sentences.entries.size() == 4, "keeps four sentences, got " + sentences.entries.size());
        check(sentences.skipped == 1, "skips a block with no Chinese, got " + sentences.skipped);
        check(sentences.entries.get(0).chinese.equals("为了消化更多的 Cybertruck 订单积压，生产将延长至 2027 年夏末。"),
                "pairs the Chinese line under the English");
        check(sentences.entries.get(1).word.equals(
                        "We would be happy to take the compute back. No problem. But I think the opposite is going to happen."),
                "joins the English lines of one block");
        check(sentences.entries.get(1).chinese.equals("我们很乐意把算力拿回来。没问题。 但我认为接下来会发生相反的情况。"),
                "joins the Chinese lines of one block");
        check(sentences.entries.get(2).word.endsWith("❤️"), "keeps an emoji on the English line");
        check(sentences.entries.get(3).chinese.equals("Robotaxi 每周运行 168 小时。"),
                "a Han character starts the Chinese half");
        ParseResult adjacentPairs = new SentenceParser().parse("""
                Definitely not 😂
                当然不是 😂
                I’m of the view that you can do what you want, so long as it doesn’t materially harm others.
                我的看法是，你可以做自己想做的事，只要不会对他人造成实质性伤害。
                """);
        check(adjacentPairs.entries.size() == 2, "recognizes adjacent sentence pairs without blank lines");
        check(adjacentPairs.entries.get(0).word.equals("Definitely not 😂"), "keeps the first adjacent sentence");
        check(adjacentPairs.entries.get(1).word.startsWith("I’m of the view"), "starts the next sentence after Chinese");

        ParseResult groupedLines = new SentenceParser().parse("""
                It was the smartest of times
                It was the dumbest of times
                这是最聪明的时代
                这是最愚蠢的时代
                """);
        check(groupedLines.entries.size() == 1, "keeps consecutive English and Chinese lines together");
        check(groupedLines.entries.get(0).word.equals("It was the smartest of times It was the dumbest of times"),
                "joins multiple English lines in one sentence");
        check(groupedLines.entries.get(0).chinese.equals("这是最聪明的时代 这是最愚蠢的时代"),
                "joins multiple Chinese lines in one sentence");

        List<SentenceParser.Draft> incomplete = new SentenceParser().parseDrafts("""
                Only English

                只有中文
                """);
        check(incomplete.size() == 2, "keeps incomplete sentence rows for editing");
        check(incomplete.get(0).chinese().isEmpty(), "keeps an English-only row");
        check(incomplete.get(1).english().isEmpty(), "keeps a Chinese-only row");
        check(SentenceParser.formatDrafts(incomplete).contains("Only English"), "formats editable rows");
        WordStore sentenceStore = new WordStore();
        sentenceStore.load(sentences.entries, sentences.skipped, false, 1L, null);
        check(sentenceStore.current().word.startsWith("In order to"), "sentences stay in pasted order");
        sentenceStore.move(1);
        check(sentenceStore.current().word.startsWith("We would be happy"), "next walks to the following sentence");
        sentenceStore.load(sentences.entries, sentences.skipped, false, 1L, sentences.entries.get(3).key());
        check(sentenceStore.current().chinese.startsWith("Robotaxi"), "a saved sentence key reopens on that sentence");

        Entry decay = new Entry("decay", "腐烂；衰退");
        String favorites = WordService.toggleFavorite("", decay);
        check(favorites.equals("decay | 腐烂；衰退"), "favorite stores the current word");
        check(WordService.favoriteContains(favorites, "decay"), "favorite finds the stored word");
        String appended = WordService.toggleFavorite("abstraction | 抽象", decay);
        check(appended.equals("abstraction | 抽象\ndecay | 腐烂；衰退"), "favorite appends without dropping the word list");
        check(WordService.toggleFavorite(appended, new Entry("Decay", "别的释义")).equals("abstraction | 抽象"),
                "favorite again removes the same English key");
        check(WordService.toggleFavorite("abstraction | 抽象", null).equals("abstraction | 抽象"),
                "no current word leaves the favorite list");
        check(!WordService.favoriteContains(null, "decay"), "a missing favorite list is empty");

        SwingUtilities.invokeAndWait(WordListCheck::checkSentenceWordHits);

        System.out.println("All checks passed (parser, navigation, remembered words, progress, confirmation, undo history, sentences, sentence word hits).");
    }

    private static void checkSentenceWordHits() {
        SentenceWordLabel label = new SentenceWordLabel();
        label.setTokenHighlightEnabled(true);
        label.setText("Don't stop well-known words.");
        label.setSize(label.getPreferredSize());
        javax.swing.JLabel metrics = new javax.swing.JLabel(label.getText());
        metrics.setFont(label.getFont());
        java.awt.FontMetrics fontMetrics = metrics.getFontMetrics(metrics.getFont());
        int knownX = fontMetrics.stringWidth("Don't stop ") + fontMetrics.stringWidth("well") / 2;
        int baselineY = (label.getHeight() - fontMetrics.getHeight()) / 2 + fontMetrics.getAscent() / 2;
        check("well-known".equals(label.tokenAt(knownX, baselineY)), "sentence hover finds the compound word");
        int gapX = fontMetrics.stringWidth("Don't stop") + fontMetrics.stringWidth(" ") / 2;
        check(label.tokenAt(gapX, baselineY) == null, "sentence whitespace speaks the sentence");
        int punctuationX = fontMetrics.stringWidth("Don't stop well-known words") + fontMetrics.stringWidth(".") / 2;
        check(label.tokenAt(punctuationX, baselineY) == null, "sentence punctuation speaks the sentence");
        label.setTokenHighlightEnabled(false);
        check(label.tokenAt(knownX, baselineY) == null, "word mode does not copy from the label");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
