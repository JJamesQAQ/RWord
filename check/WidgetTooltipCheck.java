import com.intellij.util.Consumer;
import com.riderword.plugin.RiderWordWidget;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public final class WidgetTooltipCheck {
    public static void main(String[] args) throws Exception {
        Method factory = RiderWordWidget.class.getDeclaredMethod(
                "clickable", String.class, boolean.class, Consumer.class);
        factory.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            for (String text : new String[]{"Set", "Word", "Sentence", "‹", "›", "EN", "EN+", "Got", "OK", "Undo", "Auto", "Off", "Copy", "Fav", "Unfav"}) {
                AtomicInteger clicks = new AtomicInteger();
                try {
                    JLabel label = (JLabel) factory.invoke(null, text,
                            text.equals("‹") || text.equals("›"),
                            (Consumer<MouseEvent>) event -> clicks.incrementAndGet());
                    if (label.getToolTipText() != null) {
                        throw new AssertionError("Tooltip remains on " + text);
                    }
                    label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1));
                    if (clicks.get() != 1) {
                        throw new AssertionError("Click stopped working for " + text);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
            try {
                checkDragging();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        System.out.println("Button tooltips are disabled; click callbacks, scrolling and drag suppression still work.");
    }

    private static void checkDragging() throws ReflectiveOperationException {
        RiderWordWidget widget = new RiderWordWidget(null);
        try {
            Class<?> type = Class.forName("com.riderword.plugin.RiderWordWidget$WidgetComponent");
            var constructor = type.getDeclaredConstructor(RiderWordWidget.class);
            constructor.setAccessible(true);
            Object panel = constructor.newInstance(widget);
            var scroll = (javax.swing.JScrollPane) field(panel, "scroller");
            scroll.getViewport().setExtentSize(new java.awt.Dimension(100, 40));
            for (String name : new String[]{"word", "chinese", "middle", "viewport"}) {
                var target = name.equals("viewport") ? scroll.getViewport()
                        : (javax.swing.JComponent) field(panel, name);
                scroll.getViewport().setViewSize(new java.awt.Dimension(1000, 40));
                scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
                mouse(target, MouseEvent.MOUSE_PRESSED, 100, 100);
                mouse(target, MouseEvent.MOUSE_DRAGGED, 60, 100);
                if (!(boolean) field(panel, "dragged") || scroll.getHorizontalScrollBar().getValue() != 40) {
                    throw new AssertionError("Drag failed on " + name + ": " + scroll.getHorizontalScrollBar().getValue());
                }
                // Incorrect copy/speech would access IDE services, absent in this check.
                mouse(target, MouseEvent.MOUSE_CLICKED, 60, 100);
                target.dispatchEvent(new java.awt.event.MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL,
                        0, 0, 1, 1, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
                if (scroll.getHorizontalScrollBar().getValue() <= 40) {
                    throw new AssertionError("Wheel failed on " + name);
                }
                scroll.getViewport().setViewSize(new java.awt.Dimension(100, 40));
                mouse(target, MouseEvent.MOUSE_PRESSED, 100, 100);
                mouse(target, MouseEvent.MOUSE_DRAGGED, 100, 120);
                if (!(boolean) field(panel, "dragged")) {
                    throw new AssertionError("Short-text drag must suppress clicks");
                }
                mouse(target, MouseEvent.MOUSE_CLICKED, 100, 120);
                mouse(target, MouseEvent.MOUSE_PRESSED, 100, 100);
                if ((boolean) field(panel, "dragged")) {
                    throw new AssertionError("New press must reset drag state");
                }
            }
        } finally {
            widget.dispose();
        }
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void mouse(javax.swing.JComponent target, int id, int x, int y) {
        target.dispatchEvent(new MouseEvent(target, id, 0, 0, 1, 1, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
