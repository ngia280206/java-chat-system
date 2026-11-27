package com.chatsystem.user.ui;

import javafx.scene.Node;
import javafx.scene.control.ListView;

/** Keep keyboard-accessible selected-row actions out of the empty page layout. */
public final class SelectionActions {
    private SelectionActions() { }
    public static void showWhenSelected(Node actions, ListView<?> list) {
        actions.visibleProperty().bind(list.getSelectionModel().selectedItemProperty().isNotNull());
        actions.managedProperty().bind(actions.visibleProperty());
    }
}
