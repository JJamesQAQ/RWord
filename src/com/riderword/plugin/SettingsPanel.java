package com.riderword.plugin;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** The settings form: pasted word and sentence lists, and the browse mode. */
public final class SettingsPanel {

    private final JBTextArea words = new JBTextArea(12, 60);
    private final JBTextArea sentences = new JBTextArea(8, 60);
    private final JBTextArea remembered = new JBTextArea(6, 60);
    private final JBTextArea favorites = new JBTextArea(6, 60);
    private final JBLabel summary = new JBLabel(" ");
    private final JBLabel sentenceSummary = new JBLabel(" ");
    private final JBRadioButton sequential = new JBRadioButton("按词表顺序");
    private final JBRadioButton random = new JBRadioButton("随机顺序");
    private final JPanel root;

    public SettingsPanel() {
        words.setLineWrap(true);
        words.getEmptyText().setText("每行一个词：英文<Tab或|>中文，例如 decay\t腐烂；衰退");
        sentences.setLineWrap(true);
        sentences.setWrapStyleWord(true);
        sentences.getEmptyText().setText("英文在上，中文在下，两组之间空一行");
        remembered.setLineWrap(true);
        remembered.getEmptyText().setText("点状态栏的“已记住”后，单词会出现在这里，可以直接复制");
        favorites.setLineWrap(true);
        favorites.getEmptyText().setText("点状态栏最右侧的 Fav 后，单词会出现在这里，可以直接复制");

        ButtonGroup group = new ButtonGroup();
        group.add(sequential);
        group.add(random);

        JPanel modes = new JPanel(new BorderLayout());
        modes.add(sequential, BorderLayout.WEST);
        modes.add(random, BorderLayout.CENTER);

        root = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("词表（每行：英文 + Tab 或 | + 中文）"))
                .addComponentFillVertically(new JBScrollPane(words), 0)
                .addComponent(summary)
                .addLabeledComponent("词表浏览", modes)
                .addComponent(new JBLabel("已记住（不再出现在状态栏，可在这里复制）"))
                .addComponent(new JBScrollPane(remembered))
                .addComponent(new JBLabel("收藏（仍留在词表里，可在这里单独复制）"))
                .addComponent(new JBScrollPane(favorites))
                .addComponent(new JBLabel("句子（英文在上，中文在下，两组之间空一行）"))
                .addComponent(new JBScrollPane(sentences))
                .addComponent(sentenceSummary)
                .getPanel();
    }

    public JPanel component() {
        return root;
    }

    public void load(String wordList, String rememberedList, String favoriteList, boolean randomMode, int count, int skipped,
                     String sentenceList, int sentenceCount, int sentenceSkipped) {
        words.setText(wordList);
        sentences.setText(sentenceList == null ? "" : sentenceList);
        remembered.setText(rememberedList == null ? "" : rememberedList);
        favorites.setText(favoriteList == null ? "" : favoriteList);
        (randomMode ? random : sequential).setSelected(true);
        showSummary(count, skipped);
        showSentenceSummary(sentenceCount, sentenceSkipped);
    }

    public String wordList() {
        return words.getText();
    }

    public String sentenceList() {
        return sentences.getText();
    }

    public String rememberedList() {
        return remembered.getText();
    }

    public String favoriteList() {
        return favorites.getText();
    }

    public boolean randomSelected() {
        return random.isSelected();
    }

    public void showSummary(int count, int skipped) {
        summary.setText(skipped == 0
                ? "已载入 " + count + " 个词"
                : "已载入 " + count + " 个词，跳过 " + skipped + " 行（空行不计）");
    }

    public void showSentenceSummary(int count, int skipped) {
        sentenceSummary.setText(skipped == 0
                ? "已载入 " + count + " 句"
                : "已载入 " + count + " 句，跳过 " + skipped + " 组（缺英文或中文）");
    }
}
