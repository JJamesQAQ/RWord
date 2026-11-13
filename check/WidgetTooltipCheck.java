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
            for (String text : new String[]{"Word", "Sentence", "‹", "›", "EN", "EN+", "Got", "OK", "Undo", "Auto", "Off", "Copy"}) {
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
        });
        System.out.println("Button tooltips are disabled; click callbacks still work.");
    }
}
