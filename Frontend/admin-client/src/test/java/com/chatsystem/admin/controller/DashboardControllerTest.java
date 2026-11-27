package com.chatsystem.admin.controller;

import com.chatsystem.admin.*; import com.chatsystem.admin.navigation.*;
import javafx.stage.Stage; import org.junit.jupiter.api.Test; import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest extends JavaFxTestSupport {
 @Test void changingRangeUpdatesChartAfterTrendProjectionChanges() throws Exception {
  onFx(()->{var context=new AdminApplicationContext();var stage=new Stage();try {
   var nav=new AdminNavigator(stage,context);nav.start();
   var range=(javafx.scene.control.MenuButton)stage.getScene().lookup("#rangeButton");
   range.getItems().stream().filter(item->item.getText().equals("Last 7 days")).findFirst().orElseThrow().fire();
   var chart=(javafx.scene.chart.BarChart<?,?>)stage.getScene().lookup("#activityTrend");
   assertEquals(context.admin().getActivityTrend().size(),chart.getData().get(0).getData().size());
   assertEquals(context.admin().getActivityTrend().get(0).date().toString().substring(5),chart.getData().get(0).getData().get(0).getXValue());
  }finally{stage.close();}return null;});
 }
 @Test void rangeRecomputesProjectionAndLinksNavigate() throws Exception { onFx(()->{var c=new AdminApplicationContext();var s=new Stage();try{var n=new AdminNavigator(s,c);n.start();var d=new DashboardController(c.admin(),n);assertTrue(d.selectRange(30, ChronoUnit.DAYS).isSuccess(),"range accepted");long narrow=d.metrics().loginCount();assertTrue(d.selectRange(365, ChronoUnit.DAYS).isSuccess(),"larger range accepted");assertTrue(d.metrics().loginCount()>=narrow,"larger range includes narrow results");d.showUsers();assertEquals(AdminRoute.USERS,n.currentRequest().route());d.showGroups();assertEquals(AdminRoute.GROUPS,n.currentRequest().route());d.showModeration();assertEquals(AdminRoute.MODERATION,n.currentRequest().route());assertEquals("Overview export is unavailable in the frontend-only demo.",d.exportReason());}finally{s.close();}return null;});}
 @Test void activityTrendChartReflectsSelectedDateRange() throws Exception {onFx(()->{var c=new AdminApplicationContext();var s=new Stage();try{var n=new AdminNavigator(s,c);n.start();s.show();s.getScene().getRoot().applyCss();s.getScene().getRoot().layout();var chart=(javafx.scene.chart.BarChart<?,?>)s.getScene().lookup("#activityTrend");assertEquals(c.admin().getActivityTrend().size(),chart.getData().get(0).getData().size());assertEquals(c.admin().getActivityTrend().stream().mapToLong(day->day.messages()+day.appOpens()).sum(),chart.getData().get(0).getData().stream().mapToLong(point->((Number)point.getYValue()).longValue()).sum());}finally{s.close();}return null;});}
}
