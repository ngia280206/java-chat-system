package com.chatsystem.user.controller.chat;
import com.chatsystem.shared.service.ChatService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.Objects;
public final class NewConversationDialogController {
    private final ChatService chat;
    @FXML private TextField recipientField; @FXML private Label feedbackLabel;
    public NewConversationDialogController(ChatService chat){this.chat=Objects.requireNonNull(chat);}
    @FXML private void submit(){try{var r=chat.startConversation(Long.parseLong(recipientField.getText().strip())); feedbackLabel.setText(r.isSuccess()?"Conversation ready":r.errors().get(0).message());}catch(RuntimeException e){feedbackLabel.setText("Enter a valid recipient ID");}}
    @FXML private void cancel(){recipientField.getScene().getWindow().hide();}
}
