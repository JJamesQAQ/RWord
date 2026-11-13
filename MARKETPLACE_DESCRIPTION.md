# RWord Marketplace listing

## Short description

Browse your own English–Chinese vocabulary in Rider’s status bar, with pronunciation and undoable remembered words.

## Full description

RWord helps you review a personal vocabulary list without leaving Rider.

- Import your own English–Chinese entries by pasting text or loading a TXT/CSV file.
- Display the English word and Chinese meaning in Rider’s status bar.
- Move backward and forward through the list and see your current position.
- Click the word to hear an English pronunciation. On Windows, RWord prefers an installed en-US voice.
- Turn on Auto to read the next word automatically.
- Switch between `EN+` (English and Chinese) and `EN` (English only).
- Use `Got` as a two-step confirmation before removing a word from the active list.
- Review remembered words in Settings and copy them whenever needed.
- Press previous to revisit a remembered word and use `Undo` to restore it to the active list.
- Choose sequential or stable random browsing for words.
- Switch to sentences from the leftmost status-bar control. Paste English above Chinese, with a blank line between pairs. Sentence mode hides Got, stays in pasted order, and scrolls a line that does not fit.

### Privacy

RWord is local-first. Your word list and progress are stored in Rider’s local settings. The plugin does not translate words online, upload your vocabulary, or require an account or API key.

### Input format

Use one entry per line with a tab or `|` between the English word and its Chinese meaning:

```text
decay | 衰退；腐烂
abstraction\t抽象
```

A sentence pair is one or more English lines, then the Chinese lines, then a blank line:

```text
Tesla self-driving now approved in Finland
特斯拉自动驾驶现已获芬兰批准。
```

### Compatibility

Rider 2026.2.
