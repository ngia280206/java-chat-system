package com.chatsystem.user.ui.cell;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.util.Locale;

/** Common geometry for live rows from the reference design. */
final class CellLayout {
    private CellLayout() { }
    static Label field(String text, double width, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setMinWidth(width); label.setPrefWidth(width); label.setMaxWidth(width);
        return label;
    }
    static StackPane avatar(String name) {
        String initials = java.util.Arrays.stream(name.trim().split("\\s+"))
                .filter(s -> !s.isEmpty()).limit(2).map(s -> s.substring(0, 1))
                .collect(java.util.stream.Collectors.joining()).toUpperCase(Locale.ROOT);
        Label label = new Label(initials); label.getStyleClass().add("avatar-label");
        StackPane avatar = new StackPane(label); avatar.getStyleClass().add("avatar-violet");
        return avatar;
    }
    static HBox identity(String title, String subtitle) {
        Label name = new Label(title); name.getStyleClass().add("list-title");
        Label detail = new Label(subtitle); detail.getStyleClass().add("list-preview");
        detail.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(5, name, detail); text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, avatar(title), text); row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
}
