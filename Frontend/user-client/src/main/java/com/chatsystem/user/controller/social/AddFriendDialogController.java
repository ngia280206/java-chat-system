package com.chatsystem.user.controller.social;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.*;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.controller.group.CreateGroupDialogController;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.Locale;

public final class AddFriendDialogController {
    private final SocialService social;private final AdminService directory;
    private final ChatService chat;private final UserNavigator nav;
    @FXML private TextField findUserField;
    @FXML private ListView<UserAccount> resultsList;
    @FXML private Label feedbackLabel;
    public AddFriendDialogController(SocialService s,AdminService d){this(s,d,null,null);}
    public AddFriendDialogController(SocialService s,AdminService d,ChatService c,UserNavigator n){social=s;directory=d;chat=c;nav=n;}
    @FXML private void initialize(){
        FilteredList<UserAccount> filtered=new FilteredList<>(social.getDiscoverableUsers());
        resultsList.setItems(filtered);
        resultsList.setCellFactory(x->new ListCell<>(){
            @Override protected void updateItem(UserAccount u,boolean empty){super.updateItem(u,empty);
                setText(empty||u==null?null:u.getDisplayName()+"  (@"+u.getUsername()+")");}
        });
        resultsList.setPlaceholder(new Label("No people found"));
        findUserField.textProperty().addListener((o,a,text)->filtered.setPredicate(u-> {
            String q=text.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
            return u.getDisplayName().toLowerCase(Locale.ROOT).contains(q)||u.getUsername().toLowerCase(Locale.ROOT).contains(q);
        }));
    }
    @FXML private void submit(){UserAccount target=resultsList.getSelectionModel().getSelectedItem();
        if(target==null){feedbackLabel.setText("Choose an account from search results");return;}
        var r=social.sendRequest(target.getId());feedbackLabel.setText(r.isSuccess()?"Friend request sent":r.errors().get(0).message());}
    @FXML private void chat(){UserAccount target=resultsList.getSelectionModel().getSelectedItem();
        if(target==null){feedbackLabel.setText("Choose an account");return;}
        if(chat==null||nav==null){feedbackLabel.setText("Chat is unavailable in this preview");return;}
        var r=chat.startConversation(target.getId());
        if(!r.isSuccess()){feedbackLabel.setText(r.errors().get(0).message());return;}
        resultsList.getScene().getWindow().hide();
        nav.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE,r.value().orElseThrow().getId()));
    }
    @FXML private void group(){UserAccount target=resultsList.getSelectionModel().getSelectedItem();
        if(target==null){feedbackLabel.setText("Choose an account");return;}
        if(nav==null){feedbackLabel.setText("Create group is unavailable in this preview");return;}
        nav.showDialog("/fxml/dialogs/create-group.fxml","Create group",controller->{
            if(controller instanceof CreateGroupDialogController c)c.presetMember(target);
        });
        resultsList.getScene().getWindow().hide();
    }
    @FXML private void cancel(){resultsList.getScene().getWindow().hide();}
}
