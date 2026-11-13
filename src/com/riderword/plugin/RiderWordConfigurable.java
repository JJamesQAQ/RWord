package com.riderword.plugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/** Settings | Tools | RiderWord. */
public final class RiderWordConfigurable implements Configurable {

    private SettingsPanel panel;

    @Override
    public String getDisplayName() {
        return "RWord";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new SettingsPanel();
        WordState state = WordState.getInstance();
        ParseResult parsed = new WordListParser().parse(state.wordList);
        panel.load(state.wordList, state.rememberedList, state.random, parsed.entries.size(), parsed.skipped);
        return panel.component();
    }

    @Override
    public boolean isModified() {
        WordState state = WordState.getInstance();
        return panel != null
                && (!panel.wordList().equals(state.wordList)
                || !panel.rememberedList().equals(state.rememberedList)
                || panel.randomSelected() != state.random);
    }

    @Override
    public void apply() {
        WordState state = WordState.getInstance();
        String edited = panel.wordList();
        if (!edited.equals(state.wordList)) {
            state.currentKey = "";
        }
        state.wordList = edited;
        state.rememberedList = panel.rememberedList();
        state.random = panel.randomSelected();
        WordService.getInstance().reload();
        WordStore store = WordService.getInstance().store();
        panel.showSummary(store.size(), store.skipped());
        RiderWordWidget.refreshAll();
    }

    @Override
    public void reset() {
        if (panel != null) {
            WordState state = WordState.getInstance();
            ParseResult parsed = new WordListParser().parse(state.wordList);
            panel.load(state.wordList, state.rememberedList, state.random, parsed.entries.size(), parsed.skipped);
        }
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }

    public static void open() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, RiderWordConfigurable.class);
    }
}
