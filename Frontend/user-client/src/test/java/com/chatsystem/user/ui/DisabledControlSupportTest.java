package com.chatsystem.user.ui;

import com.chatsystem.user.JavaFxTestSupport;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisabledControlSupportTest extends JavaFxTestSupport {
    @Test
    void hoveringEnabledWrapperShowsReasonForDisabledButton() throws Exception {
        Stage stage = onFx(() -> { return new Stage(); });
        Tooltip tooltip = onFx(() -> {
            Button help = new Button("Help & support");
            StackPane wrapper = new StackPane(help);
            Tooltip reason = DisabledControlSupport.install(help, wrapper, "No support destination is defined.");
            reason.setShowDelay(Duration.ZERO);
            stage.setScene(new Scene(wrapper, 220, 80));
            stage.show();
            assertTrue(help.isDisabled());
            assertTrue(help.isMouseTransparent());
            assertFalse(wrapper.isDisabled());
            assertTrue(wrapper.isPickOnBounds());
            assertEquals("No support destination is defined.", wrapper.getAccessibleText());
            Event.fireEvent(wrapper, new MouseEvent(MouseEvent.MOUSE_MOVED, 20, 20,
                    stage.getX() + 20, stage.getY() + 20, MouseButton.NONE, 0,
                    false, false, false, false, false, false, false, false, false, false, null));
            return reason;
        });
        try {
            // Tooltip activation runs on a following JavaFX pulse.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (!onFx(tooltip::isShowing) && System.nanoTime() < deadline) Thread.sleep(30);
            assertTrue(onFx(tooltip::isShowing));
            assertEquals("No support destination is defined.", onFx(tooltip::getText));
        } finally { onFx(() -> { tooltip.hide(); stage.close(); }); }
    }
}
