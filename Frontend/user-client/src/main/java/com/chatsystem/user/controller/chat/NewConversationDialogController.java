package com.chatsystem.user.controller.chat;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.Objects;
public final class NewConversationDialogController {
    private final ChatService chat; private final UserNavigator navigator;
    @FXML private TextField recipientField; @FXML private Label feedbackLabel;
    public NewConversationDialogController(ChatService chat, UserNavigator navigator){this.chat=Objects.requireNonNull(chat);this.navigator=Objects.requireNonNull(navigator);}
    @FXML private void submit(){try{var r=chat.startConversation(Long.parseLong(recipientField.getText().strip()));if(r.isSuccess()){var c=r.value().orElseThrow();recipientField.getScene().getWindow().hide();navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE,c.getId()));}else feedbackLabel.setText(r.errors().get(0).message());}catch(RuntimeException e){feedbackLabel.setText("Enter a valid recipient ID");}}
    @FXML private void cancel(){recipientField.getScene().getWindow().hide();}
}
