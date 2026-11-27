package com.chatsystem.admin;

import com.chatsystem.admin.navigation.*;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignLayoutTest extends JavaFxTestSupport {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RELAY_CAPTURE", matches="true")
    void captureReferenceReviewScreens() throws Exception {
        onFx(() -> {
            var context=new AdminApplicationContext();var stage=new Stage();
            try {
                var nav=new AdminNavigator(stage,context);nav.start();
                for(var route:AdminRoute.values()) {
                    var request=switch(route) {
                        case USER_DETAILS -> ViewRequest.user(2);
                        case GROUP_DETAILS -> ViewRequest.group(20);
                        case WARNING_DETAILS -> ViewRequest.report(501);
                        default -> ViewRequest.of(route);
                    };
                    nav.navigate(request);Parent root=stage.getScene().getRoot();root.applyCss();root.layout();
                    var image=root.snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();
                    var pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
                    var output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);output.setRGB(0,0,w,h,pixels,0,w);
                    var dir=java.nio.file.Path.of("build/ui-snapshots");java.nio.file.Files.createDirectories(dir);
                    javax.imageio.ImageIO.write(output,"png",dir.resolve(route.name().toLowerCase()+".png").toFile());
                }
            } finally {stage.close();}return null;
        });
    }
    @Test void dashboardMetricsFillRowAndSidebarRetainsBrandAndIdentity() throws Exception {
        onFx(() -> {
            var context = new AdminApplicationContext(); var stage = new Stage();
            try {
                var nav = new AdminNavigator(stage, context); nav.start();
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                assertNotNull(root.lookup(".brand-badge"));
                assertNotNull(root.lookup(".sidebar-footer"));
                Region first = (Region) root.lookup("#usersMetric").getParent();
                Region last = (Region) root.lookup("#reportsMetric").getParent();
                assertTrue(first.getWidth() >= 250, "Metric cards must fill the available row");
                assertEquals(first.getWidth(), last.getWidth(), 2);
                assertNotNull(root.lookup(".chart"), "Dashboard needs the reference vertical activity chart");
            } finally { stage.close(); } return null;
        });
    }
}
