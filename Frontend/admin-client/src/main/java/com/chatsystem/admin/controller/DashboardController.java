package com.chatsystem.admin.controller;

import com.chatsystem.admin.navigation.*;
import com.chatsystem.admin.ui.DisabledControlSupport;
import com.chatsystem.admin.ui.cell.SummaryCell;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Objects;

public final class DashboardController implements DisposableController {
    private final AdminService service;
    private final AdminNavigator navigator;
    private final ChangeListener<AdminService.DashboardMetrics> metricsListener = (o,a,b) -> refresh();
    private final ListChangeListener<AdminService.DailyActivity> trendListener = change -> refreshChart();
    @FXML private Label usersMetric, groupsMetric, reportsMetric, messagesMetric, snapshotLabel, activeMetric;
    @FXML private ProgressBar activeProgress;
    @FXML private Button exportButton, viewUsersButton, viewGroupsButton, viewModerationButton;
    @FXML private MenuButton rangeButton;
    @FXML private BarChart<String,Number> activityTrend;
    @FXML private ListView<String> recentActivity;
    @FXML private StackPane exportWrapper;

    public DashboardController(AdminService service, AdminNavigator navigator) {
        this.service=Objects.requireNonNull(service);
        this.navigator=Objects.requireNonNull(navigator);
    }

    @FXML private void initialize() {
        recentActivity.setCellFactory(view -> new SummaryCell());
        DisabledControlSupport.install(exportButton,exportWrapper,exportReason());
        for(int days : new int[]{7,12,30,365}) {
            MenuItem item=new MenuItem("Last "+days+" days");
            item.setOnAction(event -> {
                selectRange(days,ChronoUnit.DAYS);
                rangeButton.setText(item.getText());
            });
            rangeButton.getItems().add(item);
        }
        selectRange(12,ChronoUnit.DAYS);
        rangeButton.setText("Last 12 days");
        refresh();
        refreshChart();
        viewUsersButton.setOnAction(event -> showUsers());
        viewGroupsButton.setOnAction(event -> showGroups());
        viewModerationButton.setOnAction(event -> showModeration());
        service.metricsProperty().addListener(metricsListener);
        // The service publishes its trend after its metrics; observe both projections.
        service.getActivityTrend().addListener(trendListener);
    }

    private void refresh() {
        if(usersMetric==null) return;
        var metrics=metrics();
        usersMetric.setText(Long.toString(metrics.totalUsers()));
        groupsMetric.setText(Long.toString(metrics.totalGroups()));
        reportsMetric.setText(Long.toString(metrics.pendingReports()));
        messagesMetric.setText(Long.toString(metrics.messageCount()));
        activeMetric.setText(Long.toString(metrics.activeUsersInRange()));
        activeProgress.setProgress(metrics.totalUsers()==0?0:(double)metrics.activeUsers()/metrics.totalUsers());
        snapshotLabel.setText(metrics.activeUsers()+" active  ·  "+metrics.lockedUsers()+" restricted  ·  "+metrics.pendingReports()+" pending warnings");
        String latest=service.getUsers().isEmpty()?"No registrations":service.getUsers().get(service.getUsers().size()-1).getDisplayName();
        recentActivity.setItems(FXCollections.observableArrayList(
                "Latest registration: "+latest,
                "Pending warning reports: "+metrics.pendingReports(),
                "Communities created: "+metrics.totalGroups(),
                "Accounts currently restricted: "+metrics.lockedUsers()));
    }

    private void refreshChart() {
        if(activityTrend==null) return;
        var series=new XYChart.Series<String,Number>();
        service.getActivityTrend().forEach(day -> series.getData().add(
                new XYChart.Data<>(day.date().toString().substring(5),day.messages()+day.appOpens())));
        activityTrend.getData().setAll(List.of(series));
    }

    public AdminService.DashboardMetrics metrics() { return service.getMetrics(); }
    public ValidationResult<Void> selectRange(long amount,TemporalUnit unit) {
        Instant end=MockFixtures.REFERENCE_TIME.plusSeconds(1);
        return service.setDateRange(end.minus(amount,unit),end);
    }
    public void showUsers() { navigator.navigate(ViewRequest.of(AdminRoute.USERS)); }
    public void showGroups() { navigator.navigate(ViewRequest.of(AdminRoute.GROUPS)); }
    public void showModeration() { navigator.navigate(ViewRequest.of(AdminRoute.MODERATION)); }
    public String exportReason() { return "Overview export is unavailable in the frontend-only demo."; }
    @Override public void dispose() {
        service.metricsProperty().removeListener(metricsListener);
        service.getActivityTrend().removeListener(trendListener);
    }
}
