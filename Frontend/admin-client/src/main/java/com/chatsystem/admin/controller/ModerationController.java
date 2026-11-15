package com.chatsystem.admin.controller;

import com.chatsystem.admin.navigation.*;
import com.chatsystem.admin.ui.DisabledControlSupport;
import com.chatsystem.admin.ui.cell.ModerationCaseCell;
import com.chatsystem.shared.model.SpamReport;
import com.chatsystem.shared.model.status.ReportStatus;
import com.chatsystem.shared.service.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.util.Objects;

public final class ModerationController implements DisposableController {
    private final AdminService service; private final AdminNavigator navigator;
    private final ObservableList<SpamReport> results=FXCollections.observableArrayList();
    private final javafx.beans.InvalidationListener refreshListener=o->refresh();
    private String query=""; private ReportStatus status; private ReportService.ReportSort sort=ReportService.ReportSort.NEWEST;
    @FXML private TextField searchField; @FXML private ListView<SpamReport> caseList; @FXML private Label emptyLabel,pendingMetric,reviewedMetric,resolvedMetric;
    @FXML private MenuButton statusButton,sortButton; @FXML private Button exportButton; @FXML private StackPane exportWrapper;

    public ModerationController(AdminService service,AdminNavigator navigator){this.service=Objects.requireNonNull(service);this.navigator=Objects.requireNonNull(navigator);service.getReports().addListener(refreshListener);service.getReports().forEach(r->r.statusProperty().addListener(refreshListener));refresh();}
    @FXML private void initialize(){searchField.textProperty().addListener((o,a,b)->setQuery(b));caseList.setItems(results);caseList.setCellFactory(v->new ModerationCaseCell(service,this::open));MenuItem all=new MenuItem("All statuses");all.setOnAction(e->setStatus(null));statusButton.getItems().add(all);for(var value:ReportStatus.values()){MenuItem item=new MenuItem(label(value));item.setOnAction(e->setStatus(value));statusButton.getItems().add(item);}for(var value:ReportService.ReportSort.values()){MenuItem item=new MenuItem(label(value));item.setOnAction(e->setSort(value));sortButton.getItems().add(item);}DisabledControlSupport.install(exportButton,exportWrapper,"Report log export is unavailable in the frontend-only demo.");refresh();}
    public ObservableList<SpamReport> results(){return FXCollections.unmodifiableObservableList(results);}
    public void setQuery(String value){query=value==null?"":value;refresh();} public void setStatus(ReportStatus value){status=value;refresh();} public void setSort(ReportService.ReportSort value){sort=Objects.requireNonNull(value);refresh();}
    public void open(long reportId){navigator.navigate(ViewRequest.report(reportId));}
    private void refresh(){var group=navigator.moderationGroupFilter();results.setAll(group.isPresent()?service.searchReportsForConversation(group.getAsLong(),query,status,sort,0,100):service.searchReports(query,status,sort,0,100));if(emptyLabel!=null){emptyLabel.setVisible(results.isEmpty());emptyLabel.setManaged(results.isEmpty());statusButton.setText(status==null?"All statuses  v":label(status)+"  v");sortButton.setText(label(sort)+" first  v");pendingMetric.setText(Long.toString(service.getReports().stream().filter(r->r.getStatus()==ReportStatus.PENDING).count()));reviewedMetric.setText(Long.toString(service.getReports().stream().filter(r->r.getStatus()==ReportStatus.REVIEWED).count()));resolvedMetric.setText(Long.toString(service.getReports().stream().filter(r->r.getStatus()!=ReportStatus.PENDING).count()));}}
    private static String label(Enum<?> value){String text=value.name().toLowerCase().replace('_',' ');return Character.toUpperCase(text.charAt(0))+text.substring(1);}
    public void dispose(){service.getReports().removeListener(refreshListener);service.getReports().forEach(r->r.statusProperty().removeListener(refreshListener));}
}
