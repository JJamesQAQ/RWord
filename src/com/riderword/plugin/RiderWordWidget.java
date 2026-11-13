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
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Status bar control: word/sentence switch, previous, the text, next. */
public final class RiderWordWidget implements StatusBarWidget, StatusBarWidget.Multiframe, com.intellij.openapi.wm.CustomStatusBarWidget {

    static final String ID = "RWord";
    /** The Got preview shows EN+ even when the saved mode is still EN. */
    public static int displayAfterModeClick(int savedMode, boolean previewing) {
        return previewing || savedMode != WordState.ENGLISH ? WordState.ENGLISH : WordState.BOTH;
    }
    /** Long text scrolls inside this width instead of growing the status bar. */
    private static final int TEXT_WIDTH = 480;
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
     * that component directly. updateWidget() alone leaves the old "添加词表" label in place.
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
        private final JLabel reading = clickable("Word", false, e -> toggleReading());
        private final JLabel previous = clickable("‹", true, e -> step(-1));
        private final JLabel word = new JLabel();
        private final JLabel chinese = new JLabel();
        private final JLabel next = clickable("›", true, e -> step(1));
        private final JLabel mode = clickable("EN+", false, e -> cycleMode());
        private final JLabel remembered = clickable("Got", false, e -> markRemembered());
        private final JLabel progress = new JLabel();
        private final JLabel copy = clickable("Copy", false, e -> copyEnglish());
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
            onClick(middle, e -> speak());
            onClick(word, e -> speak());
            onClick(chinese, e -> speak());
            bindScroll(word);
            bindScroll(chinese);
            bindScroll(middle);
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
                if (hit == reading || hit == auto || hit == previous || hit == next
                        || hit == mode || hit == remembered || hit == copy) {
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
            previous.setVisible(!empty);
            next.setVisible(!empty);
            remembered.setVisible(!empty && !sentenceMode);
            copy.setVisible(!empty);
            progress.setText(store.currentNumber() + "/" + store.size());
            word.setVisible(true);
            chinese.setVisible(!empty && display != WordState.ENGLISH);
            float align = sentenceMode ? Component.LEFT_ALIGNMENT : Component.CENTER_ALIGNMENT;
            word.setAlignmentX(align);
            chinese.setAlignmentX(align);
            int horizontal = sentenceMode ? javax.swing.SwingConstants.LEFT : javax.swing.SwingConstants.CENTER;
            word.setHorizontalAlignment(horizontal);
            chinese.setHorizontalAlignment(horizontal);
            if (empty) {
                word.setText(sentenceMode ? "添加句子" : "添加词表");
                word.setToolTipText("打开 RWord 设置");
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

        private void toggleAuto() {
            WordState state = WordState.getInstance();
            state.autoSpeak = !state.autoSpeak;
            refreshAll();
        }

        private void speak() {
            if (dragged) {
                return;
            }
            Entry entry = WordService.getInstance().store().current();
            if (entry == null) {
                RiderWordConfigurable.open();
                return;
            }
            if (!Speaker.supported()) {
                com.intellij.openapi.ui.Messages.showInfoMessage(project,
                        "当前系统暂不支持发音，上一个和下一个仍可使用。", "RWord");
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
                private int origin;

                @Override
                public void mousePressed(MouseEvent e) {
                    dragged = false;
                    originX = e.getXOnScreen();
                    origin = scroller.getHorizontalScrollBar().getValue();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!overflows()) {
                        return;
                    }
                    int dx = e.getXOnScreen() - originX;
                    if (Math.abs(dx) > 3) {
                        dragged = true;
                    }
                    scroller.getHorizontalScrollBar().setValue(origin - dx);
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
