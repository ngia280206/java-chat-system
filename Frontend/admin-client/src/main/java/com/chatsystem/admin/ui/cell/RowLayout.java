package com.chatsystem.admin.ui.cell;

import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.util.Locale;

public final class RowLayout {
    private RowLayout() { }
    public static String initials(String name) {
        return java.util.Arrays.stream(name.trim().split("\\s+"))
                .filter(s -> !s.isEmpty()).limit(2).map(s -> s.substring(0, 1))
                .collect(java.util.stream.Collectors.joining()).toUpperCase(Locale.ROOT);
    }
    static Label field(String text, double width, String style) {
        Label label = new Label(text); label.getStyleClass().add(style);
        label.setMinWidth(width); label.setPrefWidth(width); label.setMaxWidth(width);
        return label;
    }
    static VBox identity(String name, String detail, double width) {
        VBox box = new VBox(5, field(name,width,"list-title"), field(detail,width,"list-preview"));
        box.setMinWidth(width); box.setPrefWidth(width); box.setMaxWidth(width);
        return box;
    }
}
