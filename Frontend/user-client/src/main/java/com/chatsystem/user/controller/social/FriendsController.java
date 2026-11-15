package com.chatsystem.user.controller.social;
import com.chatsystem.shared.model.UserAccount;import com.chatsystem.shared.service.*;import com.chatsystem.user.navigation.*;import com.chatsystem.user.ui.cell.FriendCell;import javafx.collections.transformation.*;import javafx.fxml.FXML;import javafx.scene.control.*;import java.util.*;
public final class FriendsController{
 private final SocialService social;private final ChatService chat;private final UserNavigator nav;@FXML private TextField friendSearchField,addFriendField;@FXML private ListView<UserAccount> friendList;@FXML private Label socialFeedbackLabel;
 public FriendsController(SocialService s,ChatService c,UserNavigator n){social=s;chat=c;nav=n;}
 @FXML private void initialize(){var sorted=new SortedList<>(social.getFriends(),Comparator.comparing(UserAccount::getDisplayName));var filtered=new FilteredList<>(sorted);friendList.setItems(filtered);friendList.setCellFactory(x->new FriendCell());friendSearchField.textProperty().addListener((o,a,t)->filtered.setPredicate(u->u.getDisplayName().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))||u.getUsername().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))));}
 @FXML private void message(){var u=selected();if(u==null)return;var r=chat.startConversation(u.getId());if(r.isSuccess())nav.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE,r.value().orElseThrow().getId()));else show(r.errors().get(0).message());}
 @FXML private void block(){var u=selected();if(u==null){show("Choose a friend to block");return;}var r=social.blockUser(u.getId());show(r.isSuccess()?"User blocked":r.errors().get(0).message());}
 @FXML private void add(){try{var r=social.sendRequest(Long.parseLong(addFriendField.getText().strip()));show(r.isSuccess()?"Friend request sent":r.errors().get(0).message());}catch(RuntimeException e){show("Enter a valid account ID");}}
 @FXML private void requests(){nav.navigate(ViewRequest.of(UserRoute.FRIEND_REQUESTS));}@FXML private void blocked(){nav.navigate(ViewRequest.of(UserRoute.BLOCKED_USERS));}
 private UserAccount selected(){return friendList.getSelectionModel().getSelectedItem();}private void show(String s){socialFeedbackLabel.setText(s);}
}
