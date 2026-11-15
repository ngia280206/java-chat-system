package com.chatsystem.admin.controller;

import com.chatsystem.admin.*; import com.chatsystem.admin.navigation.*; import javafx.stage.Stage; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;

class GroupManagementControllerTest extends JavaFxTestSupport {
 @Test void groupSearchSortDetailAndModerationRouteUseStableIds() throws Exception { onFx(()->{var c=new AdminApplicationContext();var s=new Stage();try{var n=new AdminNavigator(s,c);n.start();var groups=new GroupsController(c.admin(),n);groups.setQuery("Design");assertEquals(1,groups.results().size());long id=groups.results().get(0).getId();groups.open(id);assertEquals(id,n.currentRequest().entityId().orElseThrow().value());var detail=new GroupDetailsController(c.admin(),n);detail.present(id);assertFalse(detail.members().isEmpty());detail.openModerationHistory();assertEquals(AdminRoute.MODERATION,n.currentRequest().route());assertEquals(id,n.moderationGroupFilter().orElseThrow());assertEquals("Group member export is unavailable in the frontend-only demo.",detail.exportReason());}finally{s.close();}return null;});}
}
