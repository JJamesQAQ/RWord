package com.riderword.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.WidgetEffectBoundsProvider;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Status bar control: word/sentence switch, previous, the text, next. */
public final class RiderWordWidget implements StatusBarWidget, StatusBarWidget.Multiframe, com.intellij.openapi.wm.CustomStatusBarWidget {

    static final String ID = "RWord";
    /** The Got preview shows EN+ even when the saved mode is still EN. */
    public static int displayAfterModeClick(int savedMode, boolean previewing) {
        return previewing || savedMode != WordState.ENGLISH ? WordState.ENGLISH : WordState.BOTH;
    }
    /** Long text scrolls inside this width instead of growing the status bar. */
    private static final int TEXT_WIDTH = 480;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{IsLatin}0-9]+(?:['’\\-][\\p{IsLatin}0-9]+)*");
    private static final List<RiderWordWidget> LIVE = new CopyOnWriteArrayList<>();

    private final Project project;
    private StatusBar statusBar;
    private WidgetComponent component;

    public RiderWordWidget(Project project) {
        this.project = project;
        LIVE.add(this);
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    /** Each frame gets its own component, all of them reading the same shared word list. */
    @Override
    public @NotNull StatusBarWidget copy() {
        return new RiderWordWidget(project);
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return null;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public void dispose() {
        LIVE.remove(this);
    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            component = new WidgetComponent();
        }
        component.refresh();
        return component;
    }

    /**
     * The status bar keeps the component it first created, so changing the word has to update
     * that component directly. updateWidget() alone leaves the old "Add words" label in place.
     */
    public static void refreshAll() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (RiderWordWidget widget : LIVE) {
                if (widget.component != null) {
                    widget.component.refresh();
                }
            }
        });
    }

    /** A clickable label that looks like plain status bar text. */
    private final class WidgetComponent extends JPanel implements WidgetEffectBoundsProvider {
        private final JLabel settings = clickable("Set", false, e -> RiderWordConfigurable.open());
        private final JLabel reading = clickable("Word", false, e -> toggleReading());
        private final JLabel previous = clickable("‹", true, e -> step(-1));
        private final SentenceWordLabel word = new SentenceWordLabel();
        private final JLabel chinese = new JLabel();
        private final JLabel next = clickable("›", true, e -> step(1));
        private final JLabel mode = clickable("EN+", false, e -> cycleMode());
        private final JLabel remembered = clickable("Got", false, e -> markRemembered());
        private final JLabel progress = new JLabel();
        private final JLabel copy = clickable("Copy", false, e -> copyEnglish());
        private final JLabel favorite = clickable("Fav", false, e -> toggleFavorite());
        private final JLabel auto = clickable("Auto", false, e -> toggleAuto());
        private final JPanel middle = column(word, chinese);
        private final JScrollPane scroller = scroller(middle);
        private final RememberConfirmation rememberConfirmation = new RememberConfirmation();
        private boolean dragged;
        private String shownKey = "";

        WidgetComponent() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            word.setFont(word.getFont().deriveFont(Font.BOLD, word.getFont().getSize2D() + 2f));
            chinese.setForeground(JBColor.GRAY);
            progress.setForeground(JBColor.GRAY);
            onClick(word, this::clickWord);
            onClick(chinese, e -> speak());
            onClick(middle, e -> {
                if (e.getSource() == middle) {
                    speak();
                }
            });
            onClick(scroller.getViewport(), e -> speak());
            bindScroll(word);
            bindScroll(chinese);
            bindScroll(middle);
            bindScroll(scroller.getViewport());
            scroller.getViewport().addChangeListener(e -> word.clearHover());
            add(settings);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(reading);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(auto);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(previous);
            add(Box.createHorizontalStrut(JBUI.scale(4)));
            add(scroller);
            add(Box.createHorizontalStrut(JBUI.scale(4)));
            add(next);
            add(Box.createHorizontalStrut(JBUI.scale(4)));
            add(mode);
            add(remembered);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(progress);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(copy);
            add(Box.createHorizontalStrut(JBUI.scale(6)));
            add(favorite);
            followHover();
        }

        /** Status bar hover is one rectangle per widget. Return only the control under the pointer. */
        @Override
        public @NotNull Rectangle getWidgetEffectBounds() {
            JComponent slot = currentSlot();
            return slot == null ? new Rectangle() : new Rectangle(slot.getBounds());
        }

        private JComponent currentSlot() {
            PointerInfo info = MouseInfo.getPointerInfo();
            if (info == null) {
                return null;
            }
            Point point = new Point(info.getLocation());
            SwingUtilities.convertPointFromScreen(point, this);
            return hoverSlot(SwingUtilities.getDeepestComponentAt(this, point.x, point.y));
        }

        private JComponent hoverSlot(Component hit) {
            while (hit != null && hit != this) {
                if (hit == word || hit == chinese || hit == middle || hit == scroller) {
                    return scroller;
                }
                if (hit == settings || hit == reading || hit == auto || hit == previous || hit == next
                        || hit == mode || hit == remembered || hit == copy || hit == favorite) {
                    return (JComponent) hit;
                }
                hit = hit.getParent();
            }
            return null;
        }

        private void followHover() {
            MouseAdapter hover = new MouseAdapter() {
                private JComponent slot;

                private void update() {
                    JComponent next = currentSlot();
                    if (next != slot) {
                        slot = next;
                        repaint();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    update();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    update();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    update();
                }
            };
            listen(this, hover);
        }

        private void listen(Component component, MouseAdapter hover) {
            component.addMouseListener(hover);
            component.addMouseMotionListener(hover);
            if (component instanceof Container container) {
                for (Component child : container.getComponents()) {
                    listen(child, hover);
                }
            }
        }

        void refresh() {
            boolean sentenceMode = WordState.getInstance().reading == WordState.SENTENCES;
            WordStore store = WordService.getInstance().store();
            Entry entry = store.current();
            boolean empty = entry == null;
            boolean reviewingRemembered = store.isCurrentRemembered();
            int savedDisplay = WordState.getInstance().displayMode;
            boolean confirmingRemember = rememberConfirmation.isPending(entry, savedDisplay);
            int display = confirmingRemember ? WordState.BOTH : savedDisplay;
            if (savedDisplay != WordState.ENGLISH && savedDisplay != WordState.BOTH && !confirmingRemember) {
                WordState.getInstance().displayMode = WordState.BOTH;
                display = WordState.BOTH;
            }
            reading.setText(sentenceMode ? "Sentence" : "Word");
            mode.setText(display == WordState.ENGLISH ? "EN" : "EN+");
            remembered.setText(reviewingRemembered ? "Undo" : confirmingRemember ? "OK" : "Got");
            auto.setText(WordState.getInstance().autoSpeak ? "Auto" : "Off");
            remembered.setToolTipText(null);
            favorite.setText(sentenceMode || empty || !WordService.getInstance().currentFavorite() ? "Fav" : "Unfav");
            favorite.setToolTipText(null);
            favorite.setVisible(!empty && !sentenceMode);
            previous.setVisible(!empty);
            next.setVisible(!empty);
            remembered.setVisible(!empty && !sentenceMode);
            copy.setVisible(!empty);
            progress.setText(store.currentNumber() + "/" + store.size());
            word.setVisible(true);
            word.setTokenHighlightEnabled(sentenceMode && !empty);
            chinese.setVisible(!empty && display != WordState.ENGLISH);
            float align = sentenceMode ? Component.LEFT_ALIGNMENT : Component.CENTER_ALIGNMENT;
            word.setAlignmentX(align);
            chinese.setAlignmentX(align);
            int horizontal = sentenceMode ? javax.swing.SwingConstants.LEFT : javax.swing.SwingConstants.CENTER;
            word.setHorizontalAlignment(horizontal);
            chinese.setHorizontalAlignment(horizontal);
            if (empty) {
                word.setText(sentenceMode ? "Add sentences" : "Add words");
                word.setToolTipText("Open RWord settings");
            } else {
                word.setText(entry.word);
                word.setToolTipText(null);
                chinese.setText(entry.chinese);
                chinese.setToolTipText(null);
            }
            String key = empty ? "" : entry.key();
            boolean moved = !key.equals(shownKey);
            shownKey = key;
            fitScroll();
            if (moved) {
                scroller.getHorizontalScrollBar().setValue(0);
            }
            revalidate();
            repaint();
            if (statusBar != null) {
                JComponent bar = statusBar.getComponent();
                if (bar != null) {
                    bar.revalidate();
                    bar.repaint();
                }
            }
        }

        private void fitScroll() {
            middle.setBorder(null);
            Dimension text = middle.getPreferredSize();
            Dimension arrow = previous.getPreferredSize();
            int width = Math.min(Math.max(text.width, arrow.width), JBUI.scale(TEXT_WIDTH));
            int height = Math.max(text.height, arrow.height);
            int padX = Math.max(0, width - text.width) / 2;
            int padY = Math.max(0, height - text.height) / 2;
            if (padX > 0 || padY > 0) {
                middle.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));
                width = Math.min(middle.getPreferredSize().width, JBUI.scale(TEXT_WIDTH));
                height = middle.getPreferredSize().height;
            }
            scroller.setPreferredSize(new Dimension(width, height));
            scroller.setMaximumSize(new Dimension(width, height));
            scroller.setMinimumSize(new Dimension(Math.min(width, JBUI.scale(120)), height));
        }

        private void toggleFavorite() {
            if (WordState.getInstance().reading == WordState.SENTENCES) {
                return;
            }
            WordService.getInstance().toggleFavorite();
            refreshAll();
        }

        private void copyEnglish() {
            Entry entry = WordService.getInstance().store().current();
            if (entry == null || entry.word == null || entry.word.isBlank()) {
                return;
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(entry.word));
        }

        private void toggleReading() {
            rememberConfirmation.cancel();
            WordState state = WordState.getInstance();
            state.reading = state.reading == WordState.SENTENCES ? WordState.WORDS : WordState.SENTENCES;
            refreshAll();
        }

        private void step(int direction) {
            WordStore store = WordService.getInstance().store();
            String before = store.current() == null ? "" : store.current().key();
            rememberConfirmation.cancel();
            store.move(direction);
            WordService.getInstance().remember();
            speakAutomatically(before);
            refreshAll();
        }

        private void speakAutomatically(String previousKey) {
            if (!WordState.getInstance().autoSpeak) {
                return;
            }
            Entry current = WordService.getInstance().store().current();
            if (AutoSpeak.shouldSpeak(WordState.getInstance().autoSpeak,
                    previousKey, current == null ? "" : current.key())) {
                Speaker.speak(current.word);
            }
        }

        private void clickWord(MouseEvent event) {
            if (dragged) {
                return;
            }
            if (WordState.getInstance().reading != WordState.SENTENCES) {
                speakCurrent();
                return;
            }
            String token = word.tokenAt(event.getX(), event.getY());
            if (token == null) {
                speakCurrent();
            } else {
                CopyPasteManager.getInstance().setContents(new StringSelection(token));
            }
        }

        private void toggleAuto() {
            WordState state = WordState.getInstance();
            state.autoSpeak = !state.autoSpeak;
            refreshAll();
        }

        private void speak() {
            if (!dragged) {
                speakCurrent();
            }
        }

        private void speakCurrent() {
            Entry entry = WordService.getInstance().store().current();
            if (entry == null) {
                RiderWordConfigurable.open();
                return;
            }
            if (!Speaker.supported()) {
                com.intellij.openapi.ui.Messages.showInfoMessage(project,
                        "Speech is not available on this system. Previous and next still work.", "RWord");
                return;
            }
            Speaker.speak(entry.word);
        }

        private void cycleMode() {
            WordState state = WordState.getInstance();
            Entry entry = WordService.getInstance().store().current();
            boolean previewing = rememberConfirmation.isPending(entry, state.displayMode);
            rememberConfirmation.cancel();
            state.displayMode = displayAfterModeClick(state.displayMode, previewing);
            refreshAll();
        }

        private void markRemembered() {
            if (WordState.getInstance().reading == WordState.SENTENCES) {
                return;
            }
            WordStore store = WordService.getInstance().wordStore();
            if (store.isCurrentRemembered()) {
                WordService.getInstance().unremember();
                rememberConfirmation.cancel();
                refreshAll();
                return;
            }
            WordState state = WordState.getInstance();
            int originalMode = state.displayMode == WordState.ENGLISH ? WordState.ENGLISH : WordState.BOTH;
            String before = store.current() == null ? "" : store.current().key();
            if (rememberConfirmation.click(store.current(), originalMode)) {
                WordService.getInstance().markRemembered();
                state.displayMode = originalMode;
                speakAutomatically(before);
            }
            refreshAll();
        }

        private void bindScroll(JComponent target) {
            MouseAdapter drag = new MouseAdapter() {
                private int originX;
                private int originY;
                private int origin;

                @Override
                public void mousePressed(MouseEvent e) {
                    dragged = false;
                    originX = e.getXOnScreen();
                    originY = e.getYOnScreen();
                    origin = scroller.getHorizontalScrollBar().getValue();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getXOnScreen() - originX;
                    if (Math.abs(dx) > 3 || Math.abs(e.getYOnScreen() - originY) > 3) {
                        dragged = true;
                        word.clearHover();
                    }
                    if (overflows()) {
                        scroller.getHorizontalScrollBar().setValue(origin - dx);
                    }
                }
            };
            target.addMouseListener(drag);
            target.addMouseMotionListener(drag);
            target.addMouseWheelListener(e -> {
                if (!overflows()) {
                    return;
                }
                JScrollBar bar = scroller.getHorizontalScrollBar();
                int step = Math.max(16, target.getFontMetrics(target.getFont()).charWidth('n') * 4);
                bar.setValue(bar.getValue() + e.getWheelRotation() * step);
                e.consume();
            });
        }

        private boolean overflows() {
            JScrollBar bar = scroller.getHorizontalScrollBar();
            return bar.getMaximum() - bar.getVisibleAmount() > 4;
        }
    }

    public static final class SentenceWordLabel extends JLabel {
        private static final Color TOKEN_HOVER = JBColor.namedColor("Editor.hoverHighlightColor",
                new JBColor(new Color(0xDCEBFF), new Color(0x3A506B)));
        private int hoverStart = -1;
        private int hoverEnd = -1;
        private boolean tokenHighlightEnabled;

        public SentenceWordLabel() {
            setToolTipText(null);
            MouseAdapter hover = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    clearHover();
                }
            };
            addMouseListener(hover);
            addMouseMotionListener(hover);
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            clearHover();
        }

        public void setTokenHighlightEnabled(boolean enabled) {
            if (tokenHighlightEnabled != enabled) {
                tokenHighlightEnabled = enabled;
                if (!enabled) {
                    clearHover();
                } else {
                    repaint();
                }
            }
        }

        public String tokenAt(int x, int y) {
            Token token = tokenAtPoint(x, y);
            return token == null ? null : token.text;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (tokenHighlightEnabled && hoverStart >= 0) {
                Token token = tokenAtRange(hoverStart, hoverEnd);
                if (token != null) {
                    Graphics2D highlight = (Graphics2D) graphics.create();
                    highlight.setColor(TOKEN_HOVER);
                    highlight.fillRoundRect(token.x - 1, token.y, token.width + 2, token.height,
                            JBUI.scale(3), JBUI.scale(3));
                    highlight.dispose();
                }
            }
            super.paintComponent(graphics);
        }

        private void updateHover(int x, int y) {
            Token token = tokenAtPoint(x, y);
            int start = token == null ? -1 : token.start;
            int end = token == null ? -1 : token.end;
            if (start != hoverStart || end != hoverEnd) {
                hoverStart = start;
                hoverEnd = end;
                repaint();
            }
        }

        private void clearHover() {
            if (hoverStart != -1 || hoverEnd != -1) {
                hoverStart = -1;
                hoverEnd = -1;
                repaint();
            }
        }

        private Token tokenAtPoint(int x, int y) {
            String text = getText();
            if (!tokenHighlightEnabled || text == null || text.isEmpty() || !contains(x, y)) {
                return null;
            }
            FontMetrics metrics = getFontMetrics(getFont());
            int originX = textOriginX(metrics, text);
            int originY = textOriginY(metrics);
            if (y < originY || y >= originY + metrics.getHeight()) {
                return null;
            }
            Matcher matcher = WORD_PATTERN.matcher(text);
            while (matcher.find()) {
                int tokenX = originX + metrics.stringWidth(text.substring(0, matcher.start()));
                int tokenWidth = metrics.stringWidth(matcher.group());
                if (x >= tokenX && x < tokenX + tokenWidth) {
                    return new Token(matcher.start(), matcher.end(), matcher.group(), tokenX, originY,
                            tokenWidth, metrics.getHeight());
                }
            }
            return null;
        }

        private Token tokenAtRange(int start, int end) {
            String text = getText();
            if (text == null || start < 0 || end > text.length()) {
                return null;
            }
            FontMetrics metrics = getFontMetrics(getFont());
            int originX = textOriginX(metrics, text);
            int originY = textOriginY(metrics);
            String tokenText = text.substring(start, end);
            int tokenX = originX + metrics.stringWidth(text.substring(0, start));
            return new Token(start, end, tokenText, tokenX, originY,
                    metrics.stringWidth(tokenText), metrics.getHeight());
        }

        private int textOriginX(FontMetrics metrics, String text) {
            int left = getInsets().left;
            int right = getWidth() - getInsets().right;
            int textWidth = metrics.stringWidth(text);
            int free = Math.max(0, right - left - textWidth);
            return switch (getHorizontalAlignment()) {
                case CENTER -> left + free / 2;
                case RIGHT -> left + free;
                default -> left;
            };
        }

        private int textOriginY(FontMetrics metrics) {
            int top = getInsets().top;
            int bottom = getHeight() - getInsets().bottom;
            int free = Math.max(0, bottom - top - metrics.getHeight());
            return switch (getVerticalAlignment()) {
                case TOP -> top;
                case BOTTOM -> top + free;
                default -> top + free / 2;
            };
        }

        private record Token(int start, int end, String text, int x, int y, int width, int height) {
        }
    }

    private static JScrollPane scroller(JComponent view) {
        JScrollPane pane = new JScrollPane(view,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(JBUI.Borders.empty());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setFocusable(false);
        pane.getViewport().setFocusable(false);
        pane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        return pane;
    }

    private static JPanel column(JComponent top, JComponent bottom) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        top.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottom.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(top);
        panel.add(bottom);
        return panel;
    }

    private static JLabel clickable(String text, boolean arrow, Consumer<MouseEvent> onClick) {
        JLabel label = new JLabel(text);
        label.setToolTipText(null);
        label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        label.setBorder(JBUI.Borders.empty(arrow ? 6 : 2, arrow ? 16 : 8));
        if (arrow) {
            Font font = label.getFont();
            label.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 8f));
        }
        onClick(label, onClick);
        return label;
    }

    private static void onClick(JComponent component, Consumer<MouseEvent> onClick) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.consume(e);
            }
        });
    }
}
