package com.riderword.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Application-wide settings: the pasted word list, the browse mode, and enough state to reopen
 * Rider on the same word in the same order.
 */
@Service(Service.Level.APP)
@State(name = "RiderWordState", storages = @Storage("riderWord.xml"))
public final class WordState implements PersistentStateComponent<WordState> {

    public static final int BOTH = 0;
    public static final int ENGLISH = 1;
    public static final int WORDS = 0;
    public static final int SENTENCES = 1;

    public String wordList = "";
    public String rememberedList = "";
    public String sentenceList = "";
    public int displayMode = BOTH;
    public int reading = WORDS;
    public boolean random = false;
    public boolean autoSpeak = true;
    public long seed = 0L;
    public String currentKey = "";
    public String sentenceKey = "";

    public static WordState getInstance() {
        return ApplicationManager.getApplication().getService(WordState.class);
    }

    @Override
    public WordState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull WordState stored) {
        XmlSerializerUtil.copyBean(stored, this);
    }
}
