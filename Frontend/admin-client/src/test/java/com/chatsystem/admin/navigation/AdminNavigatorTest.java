package com.chatsystem.admin.navigation;

import com.chatsystem.admin.*;
import com.chatsystem.admin.ui.FxmlViewLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AdminNavigatorTest extends JavaFxTestSupport {
    @Test void typedDetailRoutesRejectInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> ViewRequest.of(AdminRoute.USER_DETAILS));
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest.UserId(0));
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest(AdminRoute.GROUP_DETAILS, Optional.of(new ViewRequest.UserId(1))));
        assertEquals(2, ViewRequest.user(2).entityId().orElseThrow().value());
    }

    @Test void dashboardStartsInOneFixedShellWithDisabledUndefinedRoutes() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            try {
                var navigator = new AdminNavigator(stage, new AdminApplicationContext()); navigator.start();
                assertEquals(AdminRoute.DASHBOARD, navigator.currentRequest().route());
                assertEquals(1480, stage.getScene().getWidth()); assertEquals(920, stage.getScene().getHeight());
                assertFalse(stage.isResizable()); assertTrue(stage.getScene().getStylesheets().get(0).endsWith("/css/dark.css"));
                Parent shell = stage.getScene().getRoot(); assertEquals(1, shell.lookupAll(".sidebar").size());
                for (String id : new String[]{"usersButton", "groupsButton", "moderationButton", "dashboardButton"}) ((Button)shell.lookup("#"+id)).fire();
                assertSame(shell, stage.getScene().getRoot());
                assertDisabled(shell, "settingsButton", "Admin settings are unavailable because this demo has no admin settings screen.");
                assertDisabled(shell, "helpButton", "Help and support are unavailable because no support destination is defined.");
            } finally { stage.close(); }
            return null;
        });
    }

    @Test void missingResourcesNameTheirClasspathPath() throws Exception {
        onFx(() -> {
            Stage stage = new Stage(); try {
                var loader = new FxmlViewLoader(new AdminApplicationContext(), new AdminNavigator(stage, new AdminApplicationContext()));
                assertTrue(assertThrows(IllegalArgumentException.class, () -> loader.load("/fxml/missing.fxml")).getMessage().contains("/fxml/missing.fxml"));
                assertTrue(assertThrows(IllegalArgumentException.class, () -> loader.resource("/css/missing.css")).getMessage().contains("/css/missing.css"));
            } finally { stage.close(); } return null;
        });
    }

    private static void assertDisabled(Parent shell, String id, String reason) {
        Button button = (Button)shell.lookup("#"+id); assertTrue(button.isDisabled()); assertFalse(button.getParent().isDisabled());
        assertEquals(reason, button.getParent().getAccessibleText());
    }
}
