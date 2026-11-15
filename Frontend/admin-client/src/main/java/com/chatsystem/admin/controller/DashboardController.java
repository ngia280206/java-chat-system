package com.chatsystem.admin.controller;

import com.chatsystem.admin.navigation.*; import com.chatsystem.admin.ui.DisabledControlSupport; import com.chatsystem.shared.service.AdminService; import com.chatsystem.shared.validation.ValidationResult;
import javafx.fxml.FXML; import javafx.scene.control.*; import javafx.scene.layout.StackPane; import java.time.*; import java.time.temporal.TemporalUnit; import java.util.Objects;
public final class DashboardController {
 private final AdminService service; private final AdminNavigator navigator;
 @FXML private Label usersMetric,groupsMetric,reportsMetric,messagesMetric; @FXML private Button exportButton,rangeButton,viewUsersButton,viewGroupsButton,viewModerationButton; @FXML private StackPane exportWrapper;
 public DashboardController(AdminService service,AdminNavigator navigator){this.service=Objects.requireNonNull(service);this.navigator=Objects.requireNonNull(navigator);}
 @FXML private void initialize(){ if(exportButton!=null)DisabledControlSupport.install(exportButton,exportWrapper,exportReason()); refresh(); if(viewUsersButton!=null){viewUsersButton.setOnAction(e->showUsers());viewGroupsButton.setOnAction(e->showGroups());viewModerationButton.setOnAction(e->showModeration());rangeButton.setOnAction(e->selectRange(30,java.time.temporal.ChronoUnit.DAYS));} service.metricsProperty().addListener((o,a,b)->refresh()); }
 private void refresh(){if(usersMetric!=null){var m=metrics();usersMetric.setText(Long.toString(m.totalUsers()));groupsMetric.setText(Long.toString(m.totalGroups()));reportsMetric.setText(Long.toString(m.pendingReports()));messagesMetric.setText(Long.toString(m.messageCount()));}}
 public AdminService.DashboardMetrics metrics(){return service.getMetrics();}
 public ValidationResult<Void> selectRange(long amount,TemporalUnit unit){Instant end=com.chatsystem.shared.mock.MockFixtures.REFERENCE_TIME.plusSeconds(1);return service.setDateRange(end.minus(amount,unit),end);}
 public void showUsers(){navigator.navigate(ViewRequest.of(AdminRoute.USERS));} public void showGroups(){navigator.navigate(ViewRequest.of(AdminRoute.GROUPS));} public void showModeration(){navigator.navigate(ViewRequest.of(AdminRoute.MODERATION));}
 public String exportReason(){return "Overview export is unavailable in the frontend-only demo.";}
}
