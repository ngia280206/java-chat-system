package com.chatsystem.admin.controller;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.ActivityType;
import com.chatsystem.shared.service.AdminService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

/** V1 administrator reporting UI; all projections use the existing in-memory demo fixtures. */
public final class AnalyticsController {
    private final AdminService service;
    @FXML private TabPane analyticsTabs;
    public AnalyticsController(AdminService service) { this.service = Objects.requireNonNull(service); }

    @FXML private void initialize() {
        analyticsTabs.getTabs().setAll(
                tab("New registrations", registrations()),
                tab("Friend counts", friends()),
                tab("User activity", activity()),
                tab("Annual charts", charts()),
                tab("System logins", logins()));
    }
    private static Tab tab(String label, Node node) { Tab tab = new Tab(label, node); tab.setClosable(false); return tab; }
    private static VBox panel(String subtitle, Node filters, Node content) {
        VBox box = new VBox(14, new Label(subtitle), filters, content);
        box.getStyleClass().add("card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }
    private static TextField search(String prompt) { TextField t = new TextField(); t.setPromptText(prompt); t.setPrefWidth(220); return t; }
    private static ChoiceBox<String> order() {
        ChoiceBox<String> c = new ChoiceBox<>(FXCollections.observableArrayList("Name A-Z", "Newest first", "Oldest first"));
        c.setValue("Newest first"); return c;
    }
    private static ChoiceBox<String> compare() {
        ChoiceBox<String> c = new ChoiceBox<>(FXCollections.observableArrayList("Any", "=", "<", ">"));
        c.setValue("Any"); return c;
    }
    private static boolean includes(String name, String filter) {
        return name.toLowerCase(Locale.ROOT).contains(filter.strip().toLowerCase(Locale.ROOT));
    }
    private static boolean inDates(Instant moment, DatePicker from, DatePicker to) {
        LocalDate date = moment.atZone(ZoneOffset.UTC).toLocalDate();
        return (from.getValue() == null || !date.isBefore(from.getValue()))
                && (to.getValue() == null || !date.isAfter(to.getValue()));
    }
    private static boolean matches(long value, ChoiceBox<String> mode, TextField number) {
        if ("Any".equals(mode.getValue())) return true;
        try { long target = Long.parseLong(number.getText().strip());
            return switch(mode.getValue()) { case "=" -> value == target; case "<" -> value < target; case ">" -> value > target; default -> true; };
        } catch (NumberFormatException ex) { return false; }
    }
    private static <T> TableColumn<T,String> column(String title, double width, Function<T,String> value) {
        TableColumn<T,String> result = new TableColumn<>(title);
        result.setPrefWidth(width);
        result.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        return result;
    }
    private static <T> TableView<T> table(double height, TableColumn<T,String>... cols) {
        TableView<T> t = new TableView<>(); t.setPrefHeight(height); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.getColumns().addAll(cols); return t;
    }
    private Comparator<UserAccount> userOrder(ChoiceBox<String> order) {
        return switch(order.getValue()) {
            case "Name A-Z" -> Comparator.comparing(UserAccount::getDisplayName, String.CASE_INSENSITIVE_ORDER);
            case "Oldest first" -> Comparator.comparing(UserAccount::getCreatedAt);
            default -> Comparator.comparing(UserAccount::getCreatedAt).reversed();
        };
    }
    private Node registrations() {
        DatePicker from = new DatePicker(), to = new DatePicker();
        TextField name = search("Filter by display name"); ChoiceBox<String> sort = order();
        HBox filters = new HBox(10, new Label("From"), from, new Label("To"), to, name, sort);
        TableView<UserAccount> list = table(580,
                column("Full name", 200, UserAccount::getDisplayName),
                column("Username", 180, u -> "@" + u.getUsername()),
                column("Email", 280, UserAccount::getEmail),
                column("Registration (UTC)", 210, u -> u.getCreatedAt().toString()));
        Runnable update = () -> list.setItems(FXCollections.observableArrayList(service.getUsers().stream()
                .filter(u -> inDates(u.getCreatedAt(), from, to) && includes(u.getDisplayName(), name.getText()))
                .sorted(userOrder(sort)).toList()));
        name.textProperty().addListener((o,a,b)->update.run()); from.valueProperty().addListener((o,a,b)->update.run());
        to.valueProperty().addListener((o,a,b)->update.run()); sort.valueProperty().addListener((o,a,b)->update.run()); update.run();
        return panel("Registered users in the selected date range", filters, list);
    }
    private long extendedCount(long userId) {
        Set<Long> connected = new HashSet<>(service.getFriendIds(userId));
        for (long direct : service.getFriendIds(userId)) connected.addAll(service.getFriendIds(direct));
        connected.remove(userId);
        return connected.size();
    }
    private Node friends() {
        TextField name = search("Filter by full name"); ChoiceBox<String> sort = order();
        ChoiceBox<String> mode = compare(); TextField number = search("Friend count"); number.setPrefWidth(130);
        HBox filters = new HBox(10, name, sort, new Label("Direct friends"), mode, number);
        TableView<UserAccount> list = table(580,
                column("Full name", 250, UserAccount::getDisplayName),
                column("Username", 210, u -> "@" + u.getUsername()),
                column("Direct friends", 160, u -> String.valueOf(service.getFriendIds(u.getId()).size())),
                column("Including friends-of-friends", 240, u -> String.valueOf(extendedCount(u.getId()))),
                column("Registration (UTC)", 210, u -> u.getCreatedAt().toString()));
        Runnable update = () -> list.setItems(FXCollections.observableArrayList(service.getUsers().stream()
                .filter(u -> includes(u.getDisplayName(), name.getText()))
                .filter(u -> matches(service.getFriendIds(u.getId()).size(), mode, number))
                .sorted(userOrder(sort)).toList()));
        name.textProperty().addListener((o,a,b)->update.run()); number.textProperty().addListener((o,a,b)->update.run());
        sort.valueProperty().addListener((o,a,b)->update.run()); mode.valueProperty().addListener((o,a,b)->update.run()); update.run();
        return panel("Two-hop friend counts exclude the user and count distinct accounts", filters, list);
    }
    private record ActivityCounts(long opens, long directChats, long groupChats) {}
    private ActivityCounts counts(UserAccount user, DatePicker from, DatePicker to) {
        Set<Long> groupIds = service.getGroups().stream().map(Conversation::getId).collect(Collectors.toSet());
        var events = service.getUserActivity(user.getId()).stream().filter(e -> inDates(e.occurredAt(), from, to)).toList();
        long opens = events.stream().filter(e -> e.type() == ActivityType.APP_OPEN).count();
        long direct = events.stream().filter(e -> e.type() == ActivityType.MESSAGE_SENT && e.conversationId() != null && !groupIds.contains(e.conversationId()))
                .map(ActivityEvent::conversationId).distinct().count();
        long groups = events.stream().filter(e -> e.type() == ActivityType.MESSAGE_SENT && e.conversationId() != null && groupIds.contains(e.conversationId()))
                .map(ActivityEvent::conversationId).distinct().count();
        return new ActivityCounts(opens, direct, groups);
    }
    private Node activity() {
        DatePicker from = new DatePicker(), to = new DatePicker(); TextField name = search("Filter by full name");
        ChoiceBox<String> sort = order(), metric = new ChoiceBox<>(FXCollections.observableArrayList("App opens", "Private chats", "Group chats")), mode = compare();
        metric.setValue("App opens"); TextField number = search("Count"); number.setPrefWidth(90);
        FlowPane filters = new FlowPane(10, 10, new Label("From"), from, new Label("To"), to, name, sort, metric, mode, number);
        TableView<UserAccount> list = table(550,
                column("Full name", 220, UserAccount::getDisplayName),
                column("Username", 200, u -> "@" + u.getUsername()),
                column("App opens", 150, u -> String.valueOf(counts(u,from,to).opens())),
                column("People chatted with*", 190, u -> String.valueOf(counts(u,from,to).directChats())),
                column("Groups chatted in", 160, u -> String.valueOf(counts(u,from,to).groupChats())));
        Runnable update = () -> list.setItems(FXCollections.observableArrayList(service.getUsers().stream()
                .filter(u -> includes(u.getDisplayName(), name.getText()))
                .filter(u -> { var c = counts(u,from,to); long v = switch(metric.getValue()) {
                    case "Private chats" -> c.directChats(); case "Group chats" -> c.groupChats(); default -> c.opens(); };
                    return matches(v,mode,number); })
                .sorted(userOrder(sort)).toList()));
        name.textProperty().addListener((o,a,b)->update.run());number.textProperty().addListener((o,a,b)->update.run());
        from.valueProperty().addListener((o,a,b)->update.run());to.valueProperty().addListener((o,a,b)->update.run());
        sort.valueProperty().addListener((o,a,b)->update.run());metric.valueProperty().addListener((o,a,b)->update.run());
        mode.valueProperty().addListener((o,a,b)->update.run());update.run();
        VBox content = panel("*Distinct private conversations in demo event history", filters, list);
        return content;
    }
    private Node charts() {
        int current = com.chatsystem.shared.mock.MockFixtures.REFERENCE_TIME.atZone(ZoneOffset.UTC).getYear();
        ChoiceBox<Integer> years = new ChoiceBox<>(FXCollections.observableArrayList(current - 2, current - 1, current, current + 1));
        years.setValue(current);
        VBox charts = new VBox(12);
        Runnable update = () -> {
            int year = years.getValue();
            BarChart<String,Number> registered = bar("New registrations by month", "New users");
            BarChart<String,Number> opened = bar("Users who opened the app by month", "Unique users");
            XYChart.Series<String,Number> regData = new XYChart.Series<>(), activeData = new XYChart.Series<>();
            for (int month = 1; month <= 12; month++) {
                final int m = month;
                long n = service.getUsers().stream().filter(u -> {
                    var d = u.getCreatedAt().atZone(ZoneOffset.UTC); return d.getYear() == year && d.getMonthValue() == m;
                }).count();
                long active = service.getUsers().stream().filter(u -> service.getUserActivity(u.getId()).stream()
                        .anyMatch(e -> e.type() == ActivityType.APP_OPEN &&
                            e.occurredAt().atZone(ZoneOffset.UTC).getYear() == year &&
                            e.occurredAt().atZone(ZoneOffset.UTC).getMonthValue() == m)).count();
                regData.getData().add(new XYChart.Data<>(String.valueOf(month), n));
                activeData.getData().add(new XYChart.Data<>(String.valueOf(month), active));
            }
            registered.getData().setAll(regData); opened.getData().setAll(activeData);
            charts.getChildren().setAll(registered, opened);
        };
        years.valueProperty().addListener((o,a,b)->update.run()); update.run();
        ScrollPane scroll = new ScrollPane(charts); scroll.setFitToWidth(true);
        VBox root = new VBox(10, new HBox(10, new Label("Year"), years), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS); return root;
    }
    private static BarChart<String,Number> bar(String title, String quantity) {
        CategoryAxis x = new CategoryAxis(); x.setLabel("Month");
        NumberAxis y = new NumberAxis(); y.setLabel(quantity); y.setForceZeroInRange(true); y.setTickUnit(1);
        BarChart<String,Number> chart = new BarChart<>(x,y); chart.setTitle(title); chart.setLegendVisible(false);
        chart.setPrefHeight(300); chart.setAnimated(false); return chart;
    }
    private Node logins() {
        DatePicker from = new DatePicker(), to = new DatePicker(); TextField name = search("Username or full name");
        ChoiceBox<String> sort = new ChoiceBox<>(FXCollections.observableArrayList("Newest first", "Oldest first", "Username A-Z"));
        sort.setValue("Newest first");
        HBox filters = new HBox(10, new Label("From"), from, new Label("To"), to, name, sort);
        TableView<LoginEvent> list = table(580,
                column("Login time (UTC)", 290, e -> e.loggedInAt().toString()),
                column("Username", 230, e -> service.getUser(e.userId()).map(UserAccount::getUsername).orElse("Unknown")),
                column("Full name", 290, e -> service.getUser(e.userId()).map(UserAccount::getDisplayName).orElse("Unknown")));
        Runnable update = () -> {
            Comparator<LoginEvent> comparator = switch (sort.getValue()) {
                case "Oldest first" -> Comparator.comparing(LoginEvent::loggedInAt);
                case "Username A-Z" -> Comparator.comparing((LoginEvent e) -> service.getUser(e.userId()).map(UserAccount::getUsername).orElse(""), String.CASE_INSENSITIVE_ORDER);
                default -> Comparator.comparing(LoginEvent::loggedInAt).reversed();
            };
            list.setItems(FXCollections.observableArrayList(service.getAllLoginHistory().stream()
                .filter(e -> inDates(e.loggedInAt(), from, to))
                .filter(e -> service.getUser(e.userId()).map(u -> includes(u.getUsername(),name.getText()) || includes(u.getDisplayName(),name.getText())).orElse(false))
                .sorted(comparator).toList()));
        };
        name.textProperty().addListener((o,a,b)->update.run()); from.valueProperty().addListener((o,a,b)->update.run());
        to.valueProperty().addListener((o,a,b)->update.run()); sort.valueProperty().addListener((o,a,b)->update.run()); update.run();
        return panel("System-wide login history, showing event time and identity", filters, list);
    }
}
