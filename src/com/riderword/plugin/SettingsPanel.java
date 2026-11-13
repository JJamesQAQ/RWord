package com.riderword.plugin;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The settings form: a word list, a file to import, and the browse mode. */
public final class SettingsPanel {

    private final JBTextArea words = new JBTextArea(12, 60);
    private final JBTextArea sentences = new JBTextArea(8, 60);
    private final JBTextArea remembered = new JBTextArea(6, 60);
    private final JBLabel summary = new JBLabel(" ");
    private final JBLabel sentenceSummary = new JBLabel(" ");
    private final JBRadioButton sequential = new JBRadioButton("按词表顺序");
    private final JBRadioButton random = new JBRadioButton("随机顺序");
    private final TextFieldWithBrowseButton file = new TextFieldWithBrowseButton();
    private final JPanel root;

    public SettingsPanel() {
        words.setLineWrap(true);
        words.getEmptyText().setText("每行一个词：英文<Tab或|>中文，例如 decay\t腐烂；衰退");
        sentences.setLineWrap(true);
        sentences.setWrapStyleWord(true);
        sentences.getEmptyText().setText("英文在上，中文在下，两组之间空一行");
        remembered.setLineWrap(true);
        remembered.getEmptyText().setText("点状态栏的“已记住”后，单词会出现在这里，可以直接复制");

        ButtonGroup group = new ButtonGroup();
        group.add(sequential);
        group.add(random);

        file.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileDescriptor("txt")
                .withFileFilter(f -> {
                    String name = f.getName().toLowerCase();
                    return name.endsWith(".txt") || name.endsWith(".csv");
                })
                .withTitle("选择词表文件"));

        JButton importFile = new JButton("导入文件");
        importFile.addActionListener(e -> importSelectedFile());

        JPanel fileRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        fileRow.add(file, BorderLayout.CENTER);
        fileRow.add(importFile, BorderLayout.EAST);

        JPanel modes = new JPanel(new BorderLayout());
        modes.add(sequential, BorderLayout.WEST);
        modes.add(random, BorderLayout.CENTER);

        root = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("词表（每行：英文 + Tab 或 | + 中文）"))
                .addComponentFillVertically(new JBScrollPane(words), 0)
                .addComponent(summary)
                .addLabeledComponent("从文件导入", fileRow)
                .addLabeledComponent("词表浏览", modes)
                .addComponent(new JBLabel("已记住（不再出现在状态栏，可在这里复制）"))
                .addComponent(new JBScrollPane(remembered))
                .addComponent(new JBLabel("句子（英文在上，中文在下，两组之间空一行）"))
                .addComponent(new JBScrollPane(sentences))
                .addComponent(sentenceSummary)
                .getPanel();
    }

    public JPanel component() {
        return root;
    }

    public void load(String wordList, String rememberedList, boolean randomMode, int count, int skipped,
                     String sentenceList, int sentenceCount, int sentenceSkipped) {
        words.setText(wordList);
        sentences.setText(sentenceList == null ? "" : sentenceList);
        remembered.setText(rememberedList == null ? "" : rememberedList);
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

    private void importSelectedFile() {
        String path = file.getText().trim();
        if (path.isEmpty()) {
            Messages.showWarningDialog(root, "请先选择一个词表文件。", "RWord");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            words.setText(decode(bytes));
        } catch (IOException e) {
            Messages.showErrorDialog(root, "无法读取文件：" + e.getMessage(), "RWord");
        }
    }

    /** UTF-8 when the file is valid UTF-8, otherwise the platform default (GBK on Chinese Windows). */
    static String decode(byte[] bytes) {
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.defaultCharset());
        }
    }
}
