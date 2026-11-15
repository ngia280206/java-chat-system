package com.chatsystem.admin;

import com.chatsystem.admin.navigation.AdminNavigator;
import com.chatsystem.admin.ui.FxmlViewLoader;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FxmlSmokeTest extends JavaFxTestSupport {
    @Test void shellLoadsWithExplicitController() throws Exception {
        onFx(() -> { Stage stage = new Stage(); try {
            var context = new AdminApplicationContext(); var loaded = new FxmlViewLoader(context, new AdminNavigator(stage, context)).load("/fxml/shell/admin-shell.fxml");
            assertNotNull(loaded.root()); assertNotNull(loaded.controller());
        } finally { stage.close(); } return null; });
    }
}
