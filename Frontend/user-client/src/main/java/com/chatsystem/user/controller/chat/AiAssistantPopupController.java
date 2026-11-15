package com.chatsystem.user.controller.chat;
import com.chatsystem.shared.service.AssistantService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.Objects;
public final class AiAssistantPopupController {
    private final AssistantService assistant; private long conversationId;
    @FXML private TextArea promptField; @FXML private Label responseLabel;
    public AiAssistantPopupController(AssistantService assistant){this.assistant=Objects.requireNonNull(assistant);}
    public void present(long id){conversationId=id;}
    public void prefill(String context){if(context!=null&&!context.isBlank())promptField.setText("Summarize this message: "+context);}
    @FXML private void summarize(){promptField.setText("Summarize the recent conversation");}
    @FXML private void draft(){promptField.setText("Draft a concise, friendly reply");}
    @FXML private void explain(){promptField.setText("Explain the selected message in plain language");}
    @FXML private void send(){var result=assistant.reply(conversationId,promptField.getText()); responseLabel.setText(result.isSuccess()?result.value().orElse(""):result.errors().get(0).message());}
    @FXML private void close(){promptField.getScene().getWindow().hide();}
}
