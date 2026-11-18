package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.*;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

public final class NewConversationDialogController {

    private final ChatService chat;
    private final AdminService directory;
    private final UserNavigator nav;
    private final SocialService social;
    @FXML
    private ComboBox<UserAccount> recipientChoice;
    @FXML
    private TextField recipientSearchField;
    @FXML
    private Label feedbackLabel;

    public NewConversationDialogController(ChatService c, AdminService d, UserNavigator n) {
        this(c, d, null, n);
    }

    public NewConversationDialogController(ChatService c, AdminService d, SocialService s, UserNavigator n) {
        chat = c;
        directory = d;
        social = s;
        nav = n;
    }

    @FXML
    private void initialize() {
        var filtered = new javafx.collections.transformation.FilteredList<>(social == null ? directory.getUsers() : social.getDiscoverableUsers());
        recipientChoice.setItems(filtered);
        recipientChoice.setConverter(names());
        recipientSearchField.textProperty().addListener((o, a, q) -> {
            String text = q.strip().toLowerCase(java.util.Locale.ROOT).replaceFirst("^@", "");
            filtered.setPredicate(u -> u.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(text)
                    || u.getUsername().toLowerCase(java.util.Locale.ROOT).contains(text));
        });
    }

    @FXML
    private void submit() {
        var user = recipientChoice.getValue();
        if (user == null) {
            feedbackLabel.setText("Choose a recipient");
            return;
        }
        var r = chat.startConversation(user.getId());
        if (r.isSuccess()) {
            var c = r.value().orElseThrow();
            recipientChoice.getScene().getWindow().hide();
            nav.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, c.getId()));
        } else {
            feedbackLabel.setText(r.errors().get(0).message());
    
        }}

    @FXML
    private void cancel() {
        recipientChoice.getScene().getWindow().hide();
    }

    public static StringConverter<UserAccount> names() {
        return new StringConverter<>() {
            public String toString(UserAccount u) {
                return u == null ? "" : u.getDisplayName() + " (@" + u.getUsername() + ")";
            }

            public UserAccount fromString(String s) {
                return null;
            }
        };
    }
}
