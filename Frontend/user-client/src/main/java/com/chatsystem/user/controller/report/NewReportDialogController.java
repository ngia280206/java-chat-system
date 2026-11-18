package com.chatsystem.user.controller.report;

import com.chatsystem.shared.model.Message;
import com.chatsystem.shared.service.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

public final class NewReportDialogController {

    private final ReportService reports;
    private final ChatService chat;
    @FXML
    private ComboBox<Message> messageChoice;
    @FXML
    private TextArea reasonField;
    @FXML
    private Label feedbackLabel;

    public NewReportDialogController(ReportService r, ChatService c) {
        reports = r;
        chat = c;
    }

    @FXML
    private void initialize() {
        var visible = chat.getConversations().stream().flatMap(c -> chat.getMessages(c.getId()).stream()).toList();
        messageChoice.setItems(FXCollections.observableArrayList(visible));
        messageChoice.setConverter(new StringConverter<>() {
            public String toString(Message m) {
                return m == null ? "" : "Message from member " + m.getSenderId() + ": " + m.getContent() + " (" + m.getSentAt() + ")";
            }

            public Message fromString(String s) {
                return null;
            }
        });
    }

    @FXML
    private void submit() {
        var message = messageChoice.getValue();
        if (message == null) {
            feedbackLabel.setText("Choose a message");
            return;
        }
        var result = reports.submitReport(message.getId(), message.getSenderId(), reasonField.getText());
        if (result.isSuccess()) {
            messageChoice.getScene().getWindow().hide(); 
        }else {
            feedbackLabel.setText(result.errors().get(0).message());
    
        }}

    @FXML
    private void cancel() {
        messageChoice.getScene().getWindow().hide();
    }
}
