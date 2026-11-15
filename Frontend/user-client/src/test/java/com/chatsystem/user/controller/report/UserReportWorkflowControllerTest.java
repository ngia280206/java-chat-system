package com.chatsystem.user.controller.report;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.ReportStatus;
import com.chatsystem.user.*;
import com.chatsystem.user.navigation.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserReportWorkflowControllerTest extends JavaFxTestSupport {
    @Test void reportSubmissionSearchAndDetailNavigationAreInteractive() throws Exception { onFx(() -> {
        var context=new UserApplicationContext();context.auth().login(MockFixtures.DEMO_EMAIL,MockFixtures.DEMO_PASSWORD,false);var stage=new Stage();
        try { var navigator=new UserNavigator(stage,context);navigator.start();navigator.navigate(ViewRequest.of(UserRoute.SPAM_REPORTS));
            ListView<?> list=control(stage,"reportList",ListView.class);assertEquals(2,list.getItems().size());click(stage,"newReportButton");
            Window dialog=Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()).findFirst().orElseThrow();click(dialog,"submitReportButton");assertFalse(label(dialog,"feedbackLabel").getText().isBlank());
            text(dialog,"messageIdField").setText("1001");text(dialog,"userIdField").setText("2");text(dialog,"reasonField").setText("Repeated unwanted message");click(dialog,"submitReportButton");assertEquals(3,context.reports().getReports().size());
            text(stage,"reportSearchField").setText("unwanted");click(stage,"searchReportsButton");assertFalse(list.getItems().isEmpty());list.getSelectionModel().selectFirst();click(stage,"openReportButton");
            long reportId=((ViewRequest.ReportId)navigator.currentRequest().entityId().orElseThrow()).value();var report=context.reports().getReports().stream().filter(r->r.getId()==reportId).findFirst().orElseThrow();
            report.review(ReportStatus.REVIEWED,"Reviewed in test");assertEquals("REVIEWED",label(stage,"reportStatusLabel").getText());click(stage,"backToReportsButton");
            text(stage,"reportSearchField").setText("never-match");click(stage,"searchReportsButton");assertTrue(control(stage,"reportList",ListView.class).getItems().isEmpty());
            assertTrue(context.reports().submitReport(1001,2,"never-match newly submitted").isSuccess());assertEquals(1,control(stage,"reportList",ListView.class).getItems().size());
        } finally {Window.getWindows().stream().filter(w->w!=stage).toList().forEach(Window::hide);stage.close();}
    }); }
    private static void click(Stage s,String id){control(s,id,Button.class).fire();}private static void click(Window w,String id){((Button)w.getScene().getRoot().lookup("#"+id)).fire();}
    private static TextInputControl text(Stage s,String id){return control(s,id,TextInputControl.class);}private static TextInputControl text(Window w,String id){return(TextInputControl)w.getScene().getRoot().lookup("#"+id);}private static Label label(Stage s,String id){return control(s,id,Label.class);}private static Label label(Window w,String id){return(Label)w.getScene().getRoot().lookup("#"+id);}private static <T extends Node>T control(Stage s,String id,Class<T> type){Node n=s.getScene().getRoot().lookup("#"+id);assertNotNull(n,id);return type.cast(n);}
}
