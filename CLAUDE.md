# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

RWord is a JetBrains Rider plugin (id `com.riderword.plugin`, `since-build`/`until-build` `262`) that shows one user-supplied English–Chinese entry in the status bar. It does not translate, does not use the network, and stores everything in the application-level file `riderWord.xml`. There is no Gradle project. Source is plain Java under `src/com/riderword/plugin`, compiled against the Rider 2026.2 installation.

## Build and check

Rider’s JBR is Java 25. The path below is the machine this repo is developed on.

```bash
RIDER="C:/Program Files/JetBrains/JetBrains Rider 2026.2.0.2"
CP=$(find "$RIDER/lib" -name "*.jar" | tr '\n' ';')
CLASSPATH="$CP" "$RIDER/jbr/bin/javac.exe" --release 25 -encoding UTF-8 -d classes $(find src -name "*.java")
```

`python build/run-tooltip-check.py` compiles `src` and `check` with that same JBR and runs `WordListCheck` then `WidgetTooltipCheck`. Run it after navigation, parser, remember/undo, or status-bar click changes. Both mains must print their success line. There is no other test runner, and a single check is just that class name on the classpath the script builds.

The installable jar is `classes` plus `src/META-INF/plugin.xml` packed as `META-INF/plugin.xml`. `classes/`, `dist/`, `build/publish/`, and `build/backups/` are gitignored. Signing material stays out of the repo.

## Settings page

The page is an `applicationConfigurable` in `src/META-INF/plugin.xml`. It must stay `groupId="root"` (with `groupWeight="4"`, just under Backup and Sync). On Rider 2026.2 `ConfigurableWrapper.getParentId()` reads only `parentId`; `groupConfigurables` then falls back to `groupId`. `groupId="root"` is how Keymap and Plugins sit on the left-nav root. `groupId="tools"` nests the page under Tools. Omitting both attributes does not reach the root: a null parent is rewritten to the `other` group.

`RiderWordConfigurable.open()` is what the empty-list labels "添加词表" and "添加句子" call. The form itself is `SettingsPanel`.

## How a word moves

`WordState` is the persistent bean (`wordList`, `rememberedList`, `sentenceList`, `displayMode` `BOTH`/`ENGLISH`, `reading` `WORDS`/`SENTENCES`, `random`, `autoSpeak`, `seed`, `currentKey`, `sentenceKey`). `WordService` is the only writer the UI should use. `reload()` parses `wordList` into the word `WordStore` and `sentenceList` into a second one. `markRemembered()` / `unremember()` rewrite the word lists with `WordListParser.format` and then `remember()`, which saves `currentKey`, `sentenceKey`, `random`, and `seed`. Got is hidden while reading sentences, and those two methods only change the word store. The status bar’s leftmost control switches `reading`, and `refreshAll()` follows. `SentenceParser` splits on a blank line. Within a block, lines before the first Han character are English and the rest is Chinese; each side is joined with spaces. Duplicate English keeps the first. A block missing either half increments `skipped`. Sentences stay in pasted order and are not shuffled.

`WordListParser` splits on the first tab, otherwise the first `|`. Blank lines are ignored. A missing half or a missing separator increments `skipped` and is dropped. Duplicate English, compared with `Entry.key()` (lowercase), keeps the first spelling and gloss.

`WordStore` keeps every parsed row and a browse `order` (source order, or `Collections.shuffle` with `Random(seed)`). Remembered keys stay in `entries` but are skipped by navigation, `size()`, `currentNumber()`, and `entries()`. Previous and next wrap. `dropCurrent()` records `justRemembered` and advances; the next `move(-1)` shows that word once (`isCurrentRemembered()`), and any further move clears it. `restoreCurrent()` puts it back in its original slot, including in random order. A remembered word reports progress `0`, so the widget hides it behind Undo rather than a number. Changing random mode keeps the current key and, when turning random on, replaces `seed`.

`RememberConfirmation` is the two-click Got: the first click only arms a preview (the widget forces `BOTH` and relabels the button `OK`), the second click removes. A different word, a new `Entry` instance, a display-mode change, or `cancel()` (previous, next, EN+, or the word/sentence switch) drops the preview. Do not remove on the first click.

`RiderWordWidget.refreshAll()` updates the components already installed in each status bar. Calling `updateWidget` alone leaves the old label on screen. Clicks on Word Sentence ‹ › EN+ Got Auto Copy must keep a null tooltip; `WidgetTooltipCheck` fails if one is set. The text sits in a horizontal scroller capped at 480px: a longer line scrolls by dragging or the wheel, and a drag does not also speak. Auto speech runs only when moving forward onto a different key (`AutoSpeak`). `Speaker` is Windows-only, shells out to `System.Speech` on a single daemon thread, keeps one utterance in flight, and strips non-Latin characters before speaking.

`ReviewHistory` is exercised by `WordListCheck` and is not used by the status bar. Live previous/next is `WordStore.move`.
