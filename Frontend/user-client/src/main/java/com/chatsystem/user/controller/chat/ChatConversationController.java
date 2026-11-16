package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.DisabledControlSupport;
import com.chatsystem.user.ui.cell.MessageBubble;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;

import java.util.Objects;

abstract class ChatConversationController {
    protected final ChatService chat;
    protected final UserNavigator navigator;
    private final UserSession session;
    private final AdminService directory;
    private long conversationId;
    @FXML private Label conversationTitle;
    @FXML private ListView<Message> messageList;
    @FXML private ListView<Conversation> conversationList;
    @FXML private TextField messageField;
    @FXML private Label feedbackLabel;
    @FXML private Button attachmentButton;
    @FXML private StackPane attachmentWrapper;
    @FXML private Button detailsButton;
    @FXML private StackPane detailsWrapper;
    @FXML private HBox confirmationBar;
    @FXML private Label confirmationLabel;
    private Runnable pendingAction;

    ChatConversationController(ChatService chat, UserSession session, AdminService directory, UserNavigator navigator) {
        this.chat = Objects.requireNonNull(chat); this.session=Objects.requireNonNull(session);
        this.directory=Objects.requireNonNull(directory); this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML private void initialize() {
        messageList.setCellFactory(ignored -> new MessageBubble(session.getCurrentUser().getId(), this::displayName, this::requestHide,
                message -> navigator.showAssistant(conversationId, message.getContent())));
        DisabledControlSupport.install(attachmentButton, attachmentWrapper,
                "Attachments are unavailable in the frontend-only demo.");
        if (detailsButton != null) DisabledControlSupport.install(detailsButton, detailsWrapper,
                "Private contact details are unavailable because no contact details screen is supplied.");
        messageField.setOnAction(event -> send());
        if (conversationList != null) {
            conversationList.setItems(chat.getConversations());
            conversationList.setCellFactory(ignored -> new com.chatsystem.user.ui.cell.ConversationCell());
            conversationList.setOnMouseClicked(event -> { if (event.getClickCount() == 2) openConversation(); });
        }
    }

    private String displayName(long userId) {
        return directory.getUser(userId).map(UserAccount::getDisplayName).orElse("Unknown user");
    }

    public void present(long id) {
        conversationId = id;
        var selected = chat.selectConversation(id);
        if (!selected.isSuccess()) { feedback(selected.errors().get(0).message()); return; }
        Conversation conversation = chat.getSelectedConversation();
        conversationTitle.setText(conversation.getName().isBlank() ? "Direct conversation" : conversation.getName());
        messageList.setItems(chat.getMessages(id));
        if (conversationList != null) conversationList.getSelectionModel().select(conversation);
    }

    private void openConversation() {
        Conversation selected=conversationList.getSelectionModel().getSelectedItem(); if(selected==null)return;
        navigator.navigate(ViewRequest.conversation(selected.getType()==com.chatsystem.shared.model.status.ConversationType.GROUP?UserRoute.CHAT_GROUP:UserRoute.CHAT_PRIVATE,selected.getId()));
    }

    @FXML private void send() {
        var result = chat.sendMessage(conversationId, messageField.getText());
        if (!result.isSuccess()) { feedback(result.errors().get(0).message()); return; }
        messageField.clear(); feedback(""); messageList.scrollTo(result.value().orElseThrow());
    }

    @FXML private void hideSelected() {
        Message message = messageList.getSelectionModel().getSelectedItem();
        if (message == null) { feedback("Choose a message to hide"); return; }
        requestHide(message);
    }
    private void requestHide(Message message) {
        confirm("Hide this message for your account?", () -> { var result = chat.hideMessage(message.getId()); feedback(result.isSuccess() ? "Message hidden for you" : result.errors().get(0).message()); });
    }

    @FXML private void clearHistory() {
        confirm("Clear visible history for your account?", () -> { var result = chat.clearHistory(conversationId); feedback(result.isSuccess() ? "Conversation history cleared for you" : result.errors().get(0).message()); });
    }

    @FXML private void search() { navigator.navigate(ViewRequest.conversation(UserRoute.MESSAGE_SEARCH, conversationId)); }
    @FXML private void assistant() { Message selected=messageList.getSelectionModel().getSelectedItem(); navigator.showAssistant(conversationId, selected==null?"":selected.getContent()); }
    @FXML private void confirmAction(){if(pendingAction!=null)pendingAction.run();cancelAction();}
    @FXML private void cancelAction(){pendingAction=null;confirmationBar.setVisible(false);confirmationBar.setManaged(false);}
    private void confirm(String text,Runnable action){pendingAction=action;confirmationLabel.setText(text);confirmationBar.setManaged(true);confirmationBar.setVisible(true);}
    @FXML private void back() { navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME)); }
    @FXML private void newConversation() { navigator.showDialog("/fxml/dialogs/new-conversation.fxml", "New conversation"); }
    protected long conversationId() { return conversationId; }
    protected void feedback(String text) { feedbackLabel.setText(text); }
}
