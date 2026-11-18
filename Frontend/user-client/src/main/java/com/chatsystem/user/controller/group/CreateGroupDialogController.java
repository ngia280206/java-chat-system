package com.chatsystem.user.controller.group;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.*;
import com.chatsystem.user.navigation.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.*;
import java.util.stream.Collectors;

public final class CreateGroupDialogController {
    private final GroupService groups;private final UserNavigator nav;
    private final SocialService social;
    @FXML private TextField groupNameField,memberSearchField;
    @FXML private TextArea descriptionField;
    @FXML private ListView<UserAccount> memberList;
    @FXML private Label feedbackLabel,chosenMembersLabel;
    public CreateGroupDialogController(GroupService g,UserNavigator n){this(g,null,n);}
    public CreateGroupDialogController(GroupService g,SocialService s,UserNavigator n){groups=g;social=s;nav=n;}
    @FXML private void initialize(){
        if(social==null)return;
        FilteredList<UserAccount> people=new FilteredList<>(social.getDiscoverableUsers());
        memberList.setItems(people);
        memberList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        memberList.setCellFactory(x->new ListCell<>(){
            @Override protected void updateItem(UserAccount user,boolean empty){super.updateItem(user,empty);
                setText(empty||user==null?null:user.getDisplayName()+" (@"+user.getUsername()+")");}
        });
        memberList.setPlaceholder(new Label("No accounts found"));
        memberSearchField.textProperty().addListener((o,a,q)->people.setPredicate(u-> {
            String needle=q.strip().toLowerCase(Locale.ROOT).replaceFirst("^@","");
            return u.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                    ||u.getUsername().toLowerCase(Locale.ROOT).contains(needle);
        }));
        memberList.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<UserAccount>)change->updateSelection());
    }
    /** Preselect a friend or search result when launched from its contextual action. */
    public void presetMember(UserAccount user){
        if(user==null||memberList==null)return;
        memberList.getSelectionModel().select(user);updateSelection();
    }
    private void updateSelection(){
        if(chosenMembersLabel==null)return;
        var selected=memberList.getSelectionModel().getSelectedItems();
        chosenMembersLabel.setText(selected.isEmpty()?"No members selected":"Selected: "+selected.stream()
                .map(u->"@"+u.getUsername()).collect(Collectors.joining(", ")));
    }
    @FXML private void submit(){
        var chosen=List.copyOf(memberList.getSelectionModel().getSelectedItems());
        if(chosen.isEmpty()){feedbackLabel.setText("Select at least one initial member");return;}
        var result=groups.createGroup(groupNameField.getText(),descriptionField.getText());
        if(!result.isSuccess()){feedbackLabel.setText(result.errors().get(0).message());return;}
        var group=result.value().orElseThrow();
        int invited=0;
        for(UserAccount u:chosen){if(groups.inviteMember(group.getId(),u.getId()).isSuccess())invited++;}
        if(invited==0){feedbackLabel.setText("Group created, but no invitations could be sent; review members in group details");return;}
        groupNameField.getScene().getWindow().hide();
        nav.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS,group.getId()));
    }
    @FXML private void cancel(){groupNameField.getScene().getWindow().hide();}
}
