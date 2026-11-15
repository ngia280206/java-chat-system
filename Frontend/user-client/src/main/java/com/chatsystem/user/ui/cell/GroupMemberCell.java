package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.ConversationMember;
import com.chatsystem.shared.model.status.GroupRole;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.*;

public final class GroupMemberCell extends ListCell<ConversationMember> {
    private final BooleanSupplier canManage;
    private final BiConsumer<ConversationMember, GroupRole> changeRole;
    private final Consumer<ConversationMember> remove;
    private final Predicate<ConversationMember> protectedMember;
    public GroupMemberCell(BooleanSupplier c, BiConsumer<ConversationMember, GroupRole> r,
                           Consumer<ConversationMember> x, Predicate<ConversationMember> p) {
        canManage=c; changeRole=r; remove=x; protectedMember=p;
    }
    @Override protected void updateItem(ConversationMember member, boolean empty) {
        super.updateItem(member, empty);
        if(empty||member==null){setText(null);setGraphic(null);return;}
        Label identity=new Label("Member #"+member.getUserId()+" · joined "+member.getJoinedAt());
        HBox.setHgrow(identity,Priority.ALWAYS); HBox row=new HBox(8,identity);
        if(canManage.getAsBoolean()){
            ChoiceBox<GroupRole> roles=new ChoiceBox<>(); roles.setUserData(member.getUserId());
            roles.getItems().setAll(GroupRole.values()); roles.setValue(member.getRole());
            roles.getStyleClass().add("group-role-selector");
            roles.setOnAction(e->{if(roles.getValue()!=member.getRole())changeRole.accept(member,roles.getValue());});
            Button button=new Button("Remove"); button.getStyleClass().add("group-remove-action");
            StackPane wrapper=new StackPane(button);
            if(protectedMember.test(member)){
                button.setDisable(true);button.setMouseTransparent(true);
                String reason="This protected group admin cannot be removed; the group must retain a valid admin.";
                wrapper.setAccessibleText(reason);Tooltip.install(wrapper,new Tooltip(reason));
            }else button.setOnAction(e->remove.accept(member));
            row.getChildren().addAll(roles,wrapper);
        }
        setText(null);setGraphic(row);
    }
}
