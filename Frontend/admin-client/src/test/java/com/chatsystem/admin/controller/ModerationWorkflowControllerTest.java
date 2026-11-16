package com.chatsystem.admin.controller;

import com.chatsystem.admin.*;
import com.chatsystem.admin.navigation.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ReportService;
import com.chatsystem.admin.ui.FxmlViewLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModerationWorkflowControllerTest extends JavaFxTestSupport {
    @Test void queueSearchFilterSortAndTypedDetailUseLiveReports() throws Exception {
        onFx(() -> { var context=new AdminApplicationContext(); var stage=new Stage(); try {
            var navigator=new AdminNavigator(stage,context); navigator.start();
            var queue=new ModerationController(context.admin(),navigator);
            queue.setQuery("501"); assertEquals(501,queue.results().get(0).getId());
            queue.setQuery("Jordan"); assertTrue(queue.results().stream().anyMatch(r->r.getReportedUserId()==3));
            queue.setQuery(""); queue.setStatus(ReportStatus.PENDING); queue.setSort(ReportService.ReportSort.OLDEST);
            assertTrue(queue.results().stream().allMatch(r->r.getStatus()==ReportStatus.PENDING));
            queue.open(501); assertEquals(ViewRequest.report(501),navigator.currentRequest());
        } finally { stage.close(); } return null; });
    }

    @Test void cancelAndValidationDoNotMutateWhileDismissOnlyResolvesReport() throws Exception {
        onFx(() -> { var context=new AdminApplicationContext(); var navigator=new AdminNavigator(new Stage(),context);
            var detail=new WarningDetailController(context.admin(),navigator); detail.present(501);
            assertEquals("Try this promotional offer today",detail.messageText());
            assertFalse(detail.decide(WarningDetailController.Decision.DISMISS,"",true).isSuccess());
            assertEquals(ReportStatus.PENDING,context.admin().getReport(501).orElseThrow().getStatus());
            assertFalse(detail.decide(WarningDetailController.Decision.DISMISS,"reviewed",false).isSuccess());
            assertEquals(ReportStatus.PENDING,context.admin().getReport(501).orElseThrow().getStatus());
            assertTrue(detail.decide(WarningDetailController.Decision.DISMISS,"not a violation",true).isSuccess());
            assertEquals(ReportStatus.DISMISSED,context.admin().getReport(501).orElseThrow().getStatus());
            assertEquals(UserStatus.LOCKED,context.admin().getUser(3).orElseThrow().getStatus()); return null; });
    }

    @Test void warnDoesNotSanctionAndSuspendLocksWhileAllProjectionsRefresh() throws Exception {
        onFx(() -> { var warnContext=new AdminApplicationContext(); var warn=new WarningDetailController(warnContext.admin(),new AdminNavigator(new Stage(),warnContext));
            warnContext.admin().unlockUser(3); warn.present(501); assertTrue(warn.decide(WarningDetailController.Decision.WARN,"send reminder",true).isSuccess());
            assertEquals(UserStatus.ACTIVE,warnContext.admin().getUser(3).orElseThrow().getStatus());
            assertTrue(warnContext.admin().getReport(501).orElseThrow().getReviewNotes().startsWith("Warning:"));

            var context=new AdminApplicationContext(); context.admin().unlockUser(3); var navigator=new AdminNavigator(new Stage(),context);
            var queue=new ModerationController(context.admin(),navigator); queue.setStatus(ReportStatus.PENDING);
            long pending=context.admin().getMetrics().pendingReports(); var detail=new WarningDetailController(context.admin(),navigator); detail.present(501);
            assertTrue(detail.decide(WarningDetailController.Decision.SUSPEND,"repeated abuse",true).isSuccess());
            assertEquals(UserStatus.LOCKED,context.admin().getUser(3).orElseThrow().getStatus());
            assertEquals(pending-1,context.admin().getMetrics().pendingReports());
            assertTrue(queue.results().stream().noneMatch(r->r.getId()==501));
            assertEquals(ReportStatus.REVIEWED,detail.report().getStatus()); return null; });
    }

    @Test void staleReportFailsSafelyAndGroupContextFiltersQueue() throws Exception {
        onFx(() -> { var context=new AdminApplicationContext(); var navigator=new AdminNavigator(new Stage(),context);
            navigator.showModerationForGroup(20); var queue=new ModerationController(context.admin(),navigator);
            assertTrue(queue.results().stream().allMatch(r->context.store().getMessages().stream().anyMatch(m->m.getId()==r.getMessageId()&&m.getConversationId()==20)));
            var detail=new WarningDetailController(context.admin(),navigator); detail.present(501); assertTrue(context.admin().dismissReport(501,"resolved elsewhere").isSuccess());
            assertFalse(detail.decide(WarningDetailController.Decision.WARN,"reviewed",true).isSuccess());
            assertTrue(detail.isUnavailable()); return null; });
    }

    @Test void loadedDetailReactsToExternalResolutionAndDisplaysRecordedDecision() throws Exception {
        onFx(() -> { var context=new AdminApplicationContext(); var stage=new Stage(); try { var navigator=new AdminNavigator(stage,context); var loaded=new FxmlViewLoader(context,navigator).load("/fxml/views/warning-detail.fxml"); var controller=(WarningDetailController)loaded.controller(); controller.present(501);
            var status=(Label)loaded.root().lookup("#statusLabel"); var notes=(TextArea)loaded.root().lookup("#notesArea"); var warn=(Button)loaded.root().lookup("#warnButton");
            assertEquals("PENDING",status.getText()); assertTrue(context.admin().warnUser(501,"external decision").isSuccess());
            assertEquals("REVIEWED",status.getText()); assertEquals("Warning: external decision",notes.getText()); assertFalse(notes.isEditable()); assertTrue(warn.isDisabled());
            controller.present(999); assertTrue(controller.isUnavailable()); assertEquals("Unavailable",status.getText());
        } finally { stage.close(); } return null; });
    }

    @Test void loadedQueueRendersDarkStructuredColumns() throws Exception {
        onFx(() -> { var context=new AdminApplicationContext(); var stage=new Stage(); try { var navigator=new AdminNavigator(stage,context); var loaded=new FxmlViewLoader(context,navigator).load("/fxml/views/moderation.fxml"); var scene=new Scene(loaded.root(),1240,800); scene.getStylesheets().add(new FxmlViewLoader(context,navigator).resource("/css/dark.css").toExternalForm()); stage.setScene(scene); stage.show(); loaded.root().applyCss(); loaded.root().layout();
            var list=(ListView<?>)loaded.root().lookup("#caseList"); assertNotNull(loaded.root().lookup("#contextHeading")); assertFalse(list.lookupAll(".list-cell").isEmpty());
            var populated=list.lookupAll(".list-cell").stream().filter(n->n instanceof ListCell<?> cell&&cell.getGraphic()!=null).findFirst().orElseThrow();
            assertTrue(((javafx.scene.layout.HBox)((ListCell<?>)populated).getGraphic()).getChildren().size()>=7);
            String css=java.nio.file.Files.readString(java.nio.file.Path.of(new FxmlViewLoader(context,navigator).resource("/css/dark.css").toURI())); assertTrue(css.contains(".list-cell"));
        } finally { stage.close(); } return null; });
    }
}
