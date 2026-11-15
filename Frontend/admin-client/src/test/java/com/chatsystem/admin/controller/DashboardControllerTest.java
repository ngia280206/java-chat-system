package com.chatsystem.admin.controller;

import com.chatsystem.admin.*; import com.chatsystem.admin.navigation.*;
import javafx.stage.Stage; import org.junit.jupiter.api.Test; import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest extends JavaFxTestSupport {
 @Test void rangeRecomputesProjectionAndLinksNavigate() throws Exception { onFx(()->{var c=new AdminApplicationContext();var s=new Stage();try{var n=new AdminNavigator(s,c);n.start();var d=new DashboardController(c.admin(),n);assertTrue(d.selectRange(30, ChronoUnit.DAYS).isSuccess(),"range accepted");long narrow=d.metrics().loginCount();assertTrue(d.selectRange(365, ChronoUnit.DAYS).isSuccess(),"larger range accepted");assertTrue(d.metrics().loginCount()>=narrow,"larger range includes narrow results");d.showUsers();assertEquals(AdminRoute.USERS,n.currentRequest().route());d.showGroups();assertEquals(AdminRoute.GROUPS,n.currentRequest().route());d.showModeration();assertEquals(AdminRoute.MODERATION,n.currentRequest().route());assertEquals("Overview export is unavailable in the frontend-only demo.",d.exportReason());}finally{s.close();}return null;});}
}
