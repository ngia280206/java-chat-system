package com.chatsystem.user.controller.social;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.*;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.controller.group.CreateGroupDialogController;
import com.chatsystem.user.ui.cell.FriendCell;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import java.util.*;

public final class FriendsController {

    private final SocialService social;
    private final ChatService chat;
    private final UserNavigator nav;
    @FXML private HBox selectedFriendActions;
    @FXML
    private TextField friendSearchField;
    @FXML
    private ListView<UserAccount> friendList;
    @FXML
    private Label socialFeedbackLabel, confirmationPrompt;
    @FXML
    private HBox socialConfirmationBar;
    @FXML
    private ChoiceBox<String> friendSortChoice;
    @FXML
    private CheckBox onlineOnly;
    @FXML private Label friendsMetric, onlineMetric, requestsMetric;
    private Runnable pending;

    public FriendsController(SocialService s, ChatService c, UserNavigator n) {
        social = s;
        chat = c;
        nav = n;
    }

    @FXML
    private void initialize() {
        com.chatsystem.user.ui.SelectionActions.showWhenSelected(selectedFriendActions,friendList);
        friendsMetric.textProperty().bind(javafx.beans.binding.Bindings.size(social.getFriends()).asString());
        onlineMetric.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> Long.toString(social.getFriends().stream().filter(UserAccount::isOnline).count()), social.getFriends()));
        requestsMetric.textProperty().bind(javafx.beans.binding.Bindings.size(social.getIncomingRequests()).asString());
        var sorted = new SortedList<>(social.getFriends(), Comparator.comparing(UserAccount::getDisplayName));
        var filtered = new FilteredList<>(sorted);
        friendList.setItems(filtered);
        friendList.setCellFactory(x -> new FriendCell(this::message, this::requestBlock, this::requestUnfriend, this::createGroup));
        friendSortChoice.getItems().setAll("Online first", "Name");
        friendSortChoice.getSelectionModel().selectFirst();
        Runnable sort = () -> sorted.setComparator("Name".equals(friendSortChoice.getValue())
                ? Comparator.comparing(UserAccount::getDisplayName)
                : Comparator.comparing((UserAccount u) -> !u.isOnline()).thenComparing(UserAccount::getDisplayName));
        Runnable filter = () -> filtered.setPredicate(u -> (!onlineOnly.isSelected() || u.isOnline())
                && (u.getDisplayName().toLowerCase(Locale.ROOT).contains(friendSearchField.getText().toLowerCase(Locale.ROOT))
                || u.getUsername().toLowerCase(Locale.ROOT).contains(friendSearchField.getText().toLowerCase(Locale.ROOT))));
        friendSortChoice.valueProperty().addListener((o, a, b) -> sort.run());
        onlineOnly.selectedProperty().addListener((o, a, b) -> filter.run());
        friendSearchField.textProperty().addListener((o, a, b) -> filter.run());
        social.getFriends().addListener((javafx.beans.InvalidationListener) o -> {
            sort.run();
            filter.run();
        });
        sort.run();
        filter.run();
        friendList.setPlaceholder(new Label("No friends match the selected filter"));
    }

    @FXML
    private void message() {
        message(selected());
    }

    private void message(UserAccount u) {
        if (u == null) {
            return;
        
        }var r = chat.startConversation(u.getId());
        if (r.isSuccess()) {
            nav.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, r.value().orElseThrow().getId())); 
        }else {
            show(r.errors().get(0).message());
    
        }}

    @FXML
    private void createGroup() {
        createGroup(selected());
    }

    private void createGroup(UserAccount u) {
        if (u == null) {
            show("Choose a friend to create a group");
            return;
        }
        nav.showDialog("/fxml/dialogs/create-group.fxml", "Create group", controller -> {
            if (controller instanceof CreateGroupDialogController c) {
                c.presetMember(u);
            }
        });
    }

    @FXML
    private void block() {
        requestBlock(selected());
    }

    private void requestBlock(UserAccount u) {
        if (u == null) {
            show("Choose a friend to block");
            return;
        }
        pending = () -> {
            var r = social.blockUser(u.getId());
            show(r.isSuccess() ? "User blocked" : r.errors().get(0).message());
        };
        confirmationPrompt.setText("Block @" + u.getUsername() + "?");
        revealConfirmation();
    }

    @FXML
    private void unfriend() {
        requestUnfriend(selected());
    }

    private void requestUnfriend(UserAccount u) {
        if (u == null) {
            show("Choose a friend to remove");
            return;
        }
        pending = () -> {
            var r = social.removeFriend(u.getId());
            show(r.isSuccess() ? "Friend removed" : r.errors().get(0).message());
        };
        confirmationPrompt.setText("Unfriend @" + u.getUsername() + "?");
        revealConfirmation();
    }

    private void revealConfirmation() {
        socialConfirmationBar.setManaged(true);
        socialConfirmationBar.setVisible(true);
    }

    @FXML
    private void confirmSocial() {
        if (pending != null) {
            pending.run();
        
        }cancelSocial();
    }

    @FXML
    private void cancelSocial() {
        pending = null;
        socialConfirmationBar.setManaged(false);
        socialConfirmationBar.setVisible(false);
    }

    @FXML
    private void openAdd() {
        nav.showDialog("/fxml/dialogs/add-friend.fxml", "Find people");
    }

    @FXML
    private void requests() {
        nav.navigate(ViewRequest.of(UserRoute.FRIEND_REQUESTS));
    }

    @FXML
    private void blocked() {
        nav.navigate(ViewRequest.of(UserRoute.BLOCKED_USERS));
    }

    private UserAccount selected() {
        return friendList.getSelectionModel().getSelectedItem();
    }

    private void show(String s) {
        socialFeedbackLabel.setText(s);
    }
}
