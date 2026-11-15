package com.chatsystem.admin.controller;

import com.chatsystem.admin.*;
import com.chatsystem.admin.navigation.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ReportService;
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
}
