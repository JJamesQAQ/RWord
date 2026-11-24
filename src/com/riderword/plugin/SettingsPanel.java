package com.riderword.plugin;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** The settings form, split into word settings and an editable sentence review. */
public final class SettingsPanel {

    private static final String WORD_CARD = "words";
    private static final String SENTENCE_CARD = "sentences";

    private final JBTextArea words = new JBTextArea(12, 60);
    private final JBTextArea sentenceInput = new JBTextArea(12, 60);
    private final JBTextArea remembered = new JBTextArea(6, 60);
    private final JBTextArea favorites = new JBTextArea(6, 60);
    private final JBLabel summary = new JBLabel(" ");
    private final JBLabel sentenceSummary = new JBLabel(" ");
    private final JBRadioButton sequential = new JBRadioButton("按词表顺序");
    private final JBRadioButton random = new JBRadioButton("随机顺序");
    private final JBCheckBox youdao = new JBCheckBox("有道云");
    private final JBTextField youdaoAppKey = new JBTextField();
    private final JBPasswordField youdaoAppSecret = new JBPasswordField();
    private final JPanel sentenceRows = new JPanel();
    private final List<SentenceRow> rows = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JToggleButton wordTab = new JToggleButton("单词设置");
    private final JToggleButton sentenceTab = new JToggleButton("句子设置");
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private String initialSentenceInput = "";
    private boolean rowsEdited;
    private boolean loading;

    public SettingsPanel() {
        configureTextAreas();
        configureTabs();
        cardPanel.add(wordPanel(), WORD_CARD);
        cardPanel.add(sentencePanel(), SENTENCE_CARD);
        root.add(tabBar(), BorderLayout.NORTH);
        root.add(cardPanel, BorderLayout.CENTER);
    }

    private void configureTextAreas() {
        words.setLineWrap(true);
        words.getEmptyText().setText("每行一个词：英文<Tab或|>中文，例如 decay\t腐烂；衰退");
        sentenceInput.setLineWrap(true);
        sentenceInput.setWrapStyleWord(true);
        sentenceInput.getEmptyText().setText("直接粘贴英文和中文句子，内容会自动识别");
        sentenceInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                syncSentenceRows();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncSentenceRows();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncSentenceRows();
            }
        });
        remembered.setLineWrap(true);
        remembered.getEmptyText().setText("点状态栏的“已记住”后，单词会出现在这里，可以直接复制");
        favorites.setLineWrap(true);
        favorites.getEmptyText().setText("点状态栏最右侧的 Fav 后，单词会出现在这里，可以直接复制");
    }

    private void configureTabs() {
        ButtonGroup group = new ButtonGroup();
        group.add(wordTab);
        group.add(sentenceTab);
        wordTab.addActionListener(e -> selectTab(false));
        sentenceTab.addActionListener(e -> selectTab(true));
    }

    private JPanel tabBar() {
        JPanel tabs = new JPanel(new GridLayout(1, 2, 6, 0));
        tabs.add(wordTab);
        tabs.add(sentenceTab);
        return tabs;
    }

    private JPanel wordPanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(sequential);
        group.add(random);
        JPanel modes = new JPanel(new BorderLayout());
        modes.add(sequential, BorderLayout.WEST);
        modes.add(random, BorderLayout.CENTER);

        return FormBuilder.createFormBuilder()
                .addComponent(youdao)
                .addLabeledComponent("应用 ID", youdaoAppKey)
                .addLabeledComponent("应用密钥", youdaoAppSecret)
                .addComponent(new JBLabel("词表（每行：英文 + Tab 或 | + 中文）"))
                .addComponentFillVertically(new JBScrollPane(words), 0)
                .addComponent(summary)
                .addLabeledComponent("词表浏览", modes)
                .addComponent(new JBLabel("已记住（不再出现在状态栏，可在这里复制）"))
                .addComponent(new JBScrollPane(remembered))
                .addComponent(new JBLabel("收藏（仍留在词表里，可在这里单独复制）"))
                .addComponent(new JBScrollPane(favorites))
                .getPanel();
    }

    private JPanel sentencePanel() {
        JScrollPane inputScroll = new JBScrollPane(sentenceInput);
        inputScroll.setPreferredSize(new Dimension(600, 190));

        sentenceRows.setLayout(new BoxLayout(sentenceRows, BoxLayout.Y_AXIS));
        sentenceRows.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JScrollPane rowsScroll = new JBScrollPane(sentenceRows);
        rowsScroll.setBorder(BorderFactory.createTitledBorder("识别结果（可直接编辑）"));
        rowsScroll.setPreferredSize(new Dimension(600, 360));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(new JBLabel("粘贴句子内容（自动识别）"));
        top.add(Box.createVerticalStrut(4));
        top.add(inputScroll);
        top.add(Box.createVerticalStrut(8));

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(top, BorderLayout.NORTH);
        panel.add(rowsScroll, BorderLayout.CENTER);
        panel.add(sentenceSummary, BorderLayout.SOUTH);
        return panel;
    }

    public JPanel component() {
        return root;
    }

    public void load(String wordList, String rememberedList, String favoriteList, boolean randomMode,
                     boolean youdaoMode, String appKey, String appSecret,
                     int count, int skipped, String sentenceList, int sentenceCount, int sentenceSkipped,
                     boolean sentenceMode) {
        words.setText(wordList == null ? "" : wordList);
        loading = true;
        sentenceInput.setText(sentenceList == null ? "" : sentenceList);
        loading = false;
        initialSentenceInput = sentenceInput.getText();
        rowsEdited = false;
        remembered.setText(rememberedList == null ? "" : rememberedList);
        favorites.setText(favoriteList == null ? "" : favoriteList);
        (randomMode ? random : sequential).setSelected(true);
        youdao.setSelected(youdaoMode);
        youdaoAppKey.setText(appKey == null ? "" : appKey);
        youdaoAppSecret.setText(appSecret == null ? "" : appSecret);
        replaceRows(new SentenceParser().parseDrafts(sentenceList));
        rowsEdited = false;
        showSummary(count, skipped);
        showSentenceSummary(sentenceCount, sentenceSkipped);
        selectTab(sentenceMode);
    }

    public String wordList() {
        return words.getText();
    }

    public String sentenceList() {
        if (!rowsEdited) {
            return initialSentenceInput;
        }
        List<SentenceParser.Draft> drafts = new ArrayList<>();
        for (SentenceRow row : rows) {
            drafts.add(new SentenceParser.Draft(row.english.getText().strip(), row.chinese.getText().strip()));
        }
        return SentenceParser.formatDrafts(drafts);
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

    public boolean youdaoSelected() {
        return youdao.isSelected();
    }

    public String youdaoAppKey() {
        return youdaoAppKey.getText().strip();
    }

    public String youdaoAppSecret() {
        return new String(youdaoAppSecret.getPassword()).strip();
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

    private void selectTab(boolean sentence) {
        (sentence ? sentenceTab : wordTab).setSelected(true);
        cards.show(cardPanel, sentence ? SENTENCE_CARD : WORD_CARD);
    }

    private void syncSentenceRows() {
        if (loading) {
            return;
        }
        List<SentenceParser.Draft> drafts = new SentenceParser().parseDrafts(sentenceInput.getText());
        replaceRows(drafts);
        rowsEdited = true;
        if (rows.isEmpty()) {
            addRow(0, "", "");
        }
        ParseResult parsed = new SentenceParser().parse(sentenceInput.getText());
        showSentenceSummary(parsed.entries.size(), parsed.skipped);
    }

    private void replaceRows(List<SentenceParser.Draft> drafts) {
        rows.clear();
        sentenceRows.removeAll();
        for (SentenceParser.Draft draft : drafts) {
            addRow(rows.size(), draft.english(), draft.chinese());
        }
        sentenceRows.revalidate();
        sentenceRows.repaint();
    }

    private void addRow(int index, String english, String chinese) {
        SentenceRow row = new SentenceRow(english, chinese);
        rows.add(index, row);
        sentenceRows.add(row.panel, index);
        sentenceRows.revalidate();
        sentenceRows.repaint();
    }

    private void deleteRow(SentenceRow row) {
        int index = rows.indexOf(row);
        if (index < 0) {
            return;
        }
        rows.remove(index);
        rowsEdited = true;
        sentenceRows.remove(row.panel);
        sentenceRows.revalidate();
        sentenceRows.repaint();
    }

    private final class SentenceRow {
        private final JBTextArea english = editableArea();
        private final JBTextArea chinese = editableArea();
        private final JPanel panel = new JPanel(new BorderLayout(6, 0));

        private SentenceRow(String englishText, String chineseText) {
            english.setText(englishText);
            chinese.setText(chineseText);
            DocumentListener edited = new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    rowsEdited = true;
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    rowsEdited = true;
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    rowsEdited = true;
                }
            };
            english.getDocument().addDocumentListener(edited);
            chinese.getDocument().addDocumentListener(edited);
            JPanel fields = new JPanel(new GridLayout(2, 1, 0, 3));
            fields.add(fieldLine("英文", english));
            fields.add(fieldLine("中文", chinese));
            JPanel actions = new JPanel(new GridLayout(2, 1, 0, 3));
            JButton delete = new JButton("删除");
            JButton add = new JButton("增加");
            delete.addActionListener(e -> deleteRow(this));
            add.addActionListener(e -> {
                rowsEdited = true;
                addRow(rows.indexOf(this) + 1, "", "");
            });
            actions.add(delete);
            actions.add(add);
            panel.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
            panel.add(fields, BorderLayout.CENTER);
            panel.add(actions, BorderLayout.EAST);
            panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        }
    }

    private static JPanel fieldLine(String label, JBTextArea area) {
        JPanel line = new JPanel(new BorderLayout(4, 0));
        TitledBorder border = BorderFactory.createTitledBorder(label);
        border.setTitleColor(JBColor.GRAY);
        border.setBorder(BorderFactory.createLineBorder(JBColor.namedColor("Borders.color", JBColor.GRAY)));
        line.setBorder(border);
        line.add(area, BorderLayout.CENTER);
        return line;
    }

    private static JBTextArea editableArea() {
        JBTextArea area = new JBTextArea(1, 50);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setMinimumSize(new Dimension(100, 34));
        return area;
    }
}
