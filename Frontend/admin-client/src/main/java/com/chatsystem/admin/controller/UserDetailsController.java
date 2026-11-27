package com.chatsystem.admin.controller;
import com.chatsystem.admin.navigation.*;import com.chatsystem.shared.model.*;import com.chatsystem.shared.model.status.UserStatus;import com.chatsystem.shared.service.AdminService;import com.chatsystem.shared.validation.*;import javafx.collections.*;import javafx.fxml.FXML;import javafx.scene.control.*;import java.util.*;
public final class UserDetailsController{
 private final AdminService service;private final AdminNavigator navigator;private long id;@FXML private Label initialsLabel,nameLabel,emailLabel,statusLabel,createdLabel,usernameLabel,addressLabel,birthLabel,genderLabel;@FXML private Button backButton,restrictButton,historyButton,editButton,passwordButton,deleteButton;@FXML private ListView<LoginEvent> historyList;@FXML private ListView<ActivityEvent> activityList;@FXML private ListView<UserAccount> friendsList;
 public UserDetailsController(AdminService s,AdminNavigator n){service=s;navigator=n;}@FXML private void initialize(){activityList.setCellFactory(v->new com.chatsystem.admin.ui.cell.ActivityEventCell());backButton.setOnAction(e->navigator.navigate(ViewRequest.of(AdminRoute.USERS)));restrictButton.setOnAction(e->confirmRestriction());
  editButton.setOnAction(e->{service.getUser(id).ifPresent(u->{com.chatsystem.admin.ui.AdminAccountDialogs.showEditor(service,u);present(id);});});
  passwordButton.setOnAction(e->service.getUser(id).ifPresent(u->com.chatsystem.admin.ui.AdminAccountDialogs.showPassword(service,u)));
  deleteButton.setOnAction(e->confirmDelete());
  historyButton.setOnAction(e->{if(historyList!=null)historyList.setItems(FXCollections.observableArrayList(service.getLoginHistory(id)));});}
 private void confirmDelete(){
  UserAccount user=service.getUser(id).orElse(null); if(user==null)return;
  Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"Delete @"+user.getUsername()+"? The demo will mark this account as deleted.",ButtonType.OK,ButtonType.CANCEL);
  alert.setHeaderText("Confirm account deletion");
  if(alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){service.deleteUser(id);navigator.navigate(ViewRequest.of(AdminRoute.USERS));}
 }
 private void confirmRestriction(){UserAccount user=service.getUser(id).orElse(null);if(user==null)return;String action=user.getStatus()==UserStatus.LOCKED?"unlock":"restrict";Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"Confirm that you want to "+action+" "+user.getDisplayName()+"?",ButtonType.OK,ButtonType.CANCEL);alert.setHeaderText("Confirm account change");if(alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)toggleRestriction(true);}
 public void present(long userId){id=userId;UserAccount u=service.getUser(id).orElseThrow(()->new IllegalArgumentException("Unknown user "+id));if(nameLabel!=null){nameLabel.setText(u.getDisplayName());initialsLabel.setText(com.chatsystem.admin.ui.cell.RowLayout.initials(u.getDisplayName()));emailLabel.setText(u.getEmail());
  addressLabel.setText("Address: "+(u.getAddress().isBlank()?"Not supplied":u.getAddress()));
  birthLabel.setText("Date of birth: "+(u.getDateOfBirth()==null?"Not supplied":u.getDateOfBirth()));
  genderLabel.setText("Gender: "+u.getGender());
  friendsList.setItems(FXCollections.observableArrayList(service.getFriendIds(id).stream().map(fid->service.getUser(fid).orElse(null)).filter(Objects::nonNull).toList()));
  friendsList.setPlaceholder(new Label("No friends to display"));
  friendsList.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(UserAccount friend,boolean empty){super.updateItem(friend,empty);setText(empty||friend==null?null:friend.getDisplayName()+"  @"+friend.getUsername());}});
  boolean deleted=u.getStatus()==UserStatus.DELETED;editButton.setDisable(deleted);passwordButton.setDisable(deleted);deleteButton.setDisable(deleted);restrictButton.setDisable(deleted);
  usernameLabel.setText("@"+u.getUsername()+"  •  ID "+u.getId());createdLabel.setText("Joined "+u.getCreatedAt());statusLabel.setText(u.getStatus().name());restrictButton.setText(u.getStatus()==UserStatus.LOCKED?"Unlock user":"Restrict user");historyList.setItems(FXCollections.observableArrayList(service.getLoginHistory(id).stream().limit(4).toList()));historyList.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(LoginEvent e,boolean empty){super.updateItem(e,empty);setText(empty||e==null?null:e.loggedInAt()+"  •  "+e.device()+"  •  "+e.location());}});activityList.setItems(FXCollections.observableArrayList(service.getUserActivity(id).stream().limit(3).toList()));}}
 public ValidationResult<Void> toggleRestriction(boolean confirmed){if(!confirmed)return ValidationResult.failure(List.of(new ValidationError("confirmation","Confirmation required")));UserAccount u=service.getUser(id).orElse(null);if(u==null)return ValidationResult.failure(List.of(new ValidationError("user","User no longer exists")));var r=u.getStatus()==UserStatus.LOCKED?service.unlockUser(id):service.lockUser(id);present(id);return r;}
}
