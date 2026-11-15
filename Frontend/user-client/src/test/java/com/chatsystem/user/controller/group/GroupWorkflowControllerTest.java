package com.chatsystem.user.controller.group;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.GroupRole;
import com.chatsystem.user.*;
import com.chatsystem.user.navigation.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupWorkflowControllerTest extends JavaFxTestSupport {
    @Test void createEditRoleRemoveAndLeaveRespectValidationAndConfirmation() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.GROUP_MANAGEMENT));
        ListView<?> groups = control(stage,"groupList",ListView.class); assertEquals(2, groups.getItems().size());
        click(stage,"createGroupButton"); Window dialog = dialog(stage);
        click(dialog,"submitCreateGroupButton"); assertFalse(label(dialog,"feedbackLabel").getText().isBlank());
        text(dialog,"groupNameField").setText("Frontend Guild"); text(dialog,"descriptionField").setText("Build the client"); click(dialog,"submitCreateGroupButton");
        assertEquals(3, context.groups().getGroups().size()); assertFalse(dialog.isShowing());

        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS, MockFixtures.GROUP_CONVERSATION_ID));
        TextArea description=control(stage,"descriptionField",TextArea.class);description.setText("Updated design notes");click(stage,"saveGroupButton");
        assertEquals("Updated design notes", context.groups().getGroups().stream().filter(g->g.getId()==20).findFirst().orElseThrow().getDescription());
        var promoted=context.groups().getMembers(20).stream().filter(m->m.getUserId()==2).findFirst().orElseThrow();assertTrue(context.groups().changeRole(20,2,GroupRole.ADMIN).isSuccess());assertEquals(GroupRole.ADMIN,promoted.getRole());
        click(stage,"leaveGroupButton");click(stage,"cancelGroupActionButton");assertTrue(context.groups().getGroups().stream().anyMatch(g->g.getId()==20));
        click(stage,"leaveGroupButton");click(stage,"confirmGroupActionButton");assertFalse(context.groups().getGroups().stream().anyMatch(g->g.getId()==20));
    }); }

    @Test void invitationsCanBeAcceptedOrRejectedAndInviteModalValidates() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.GROUP_INVITATIONS));ListView<?> invitations=control(stage,"invitationList",ListView.class);assertEquals(1,invitations.getItems().size());
        invitations.getSelectionModel().selectFirst();click(stage,"joinInvitationButton");assertTrue(context.groups().getInvitations().isEmpty());assertTrue(context.groups().getGroups().stream().anyMatch(g->g.getId()==21));
        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS,MockFixtures.GROUP_CONVERSATION_ID));click(stage,"inviteMemberButton");Window dialog=dialog(stage);
        text(dialog,"memberIdField").setText("1");click(dialog,"submitInviteButton");assertFalse(label(dialog,"feedbackLabel").getText().isBlank());click(dialog,"cancelInviteButton");
    }); }

    @Test void memberRoleControlsArePermissionAware() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.GROUP_MANAGEMENT));assertNotNull(stage.getScene().getRoot().lookup("#viewAllGroupsButton"));assertNotNull(stage.getScene().getRoot().lookup("#manageMembersButton"));
        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS,MockFixtures.GROUP_CONVERSATION_ID));stage.show();stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();assertFalse(stage.getScene().getRoot().lookupAll(".group-role-selector").isEmpty());assertFalse(stage.getScene().getRoot().lookupAll(".group-remove-action").isEmpty());
        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS,22));stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();assertTrue(stage.getScene().getRoot().lookupAll(".group-role-selector").isEmpty());
    }); }

    private void workflow(Action action)throws Exception{onFx(()->{var c=new UserApplicationContext();c.auth().login(MockFixtures.DEMO_EMAIL,MockFixtures.DEMO_PASSWORD,false);var s=new Stage();try{var n=new UserNavigator(s,c);n.start();action.run(c,s,n);}finally{Window.getWindows().stream().filter(w->w!=s).toList().forEach(Window::hide);s.close();}});}
    private interface Action{void run(UserApplicationContext c,Stage s,UserNavigator n);}
    private static Window dialog(Stage owner){return Window.getWindows().stream().filter(w->w!=owner&&w.isShowing()).findFirst().orElseThrow();}
    private static void click(Stage s,String id){control(s,id,Button.class).fire();}private static void click(Window w,String id){((Button)w.getScene().getRoot().lookup("#"+id)).fire();}
    private static TextInputControl text(Window w,String id){return (TextInputControl)w.getScene().getRoot().lookup("#"+id);}private static Label label(Window w,String id){return (Label)w.getScene().getRoot().lookup("#"+id);}
    private static <T extends Node>T control(Stage s,String id,Class<T> type){Node n=s.getScene().getRoot().lookup("#"+id);assertNotNull(n,id);return type.cast(n);}
}
