package com.chatsystem.admin.controller;

import com.chatsystem.admin.*; import com.chatsystem.admin.navigation.*; import com.chatsystem.shared.model.status.*;
import javafx.stage.Stage; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;

class UserManagementControllerTest extends JavaFxTestSupport {
 @Test void searchSortPageDetailAndLockPropagate() throws Exception { onFx(()->{var c=new AdminApplicationContext();var s=new Stage();try{var n=new AdminNavigator(s,c);n.start();var users=new UsersController(c.admin(),n);users.setQuery("Mia");assertEquals(1,users.results().size());long id=users.results().get(0).getId();users.open(id);assertEquals(id,n.currentRequest().entityId().orElseThrow().value());var detail=new UserDetailsController(c.admin(),n);detail.present(id);assertTrue(detail.toggleRestriction(true).isSuccess());assertEquals(UserStatus.LOCKED,users.results().get(0).getStatus());assertTrue(detail.toggleRestriction(true).isSuccess());assertEquals(UserStatus.ACTIVE,c.admin().getUser(id).orElseThrow().getStatus());users.setQuery("");users.setStatus(UserStatus.ACTIVE);users.setSort(com.chatsystem.shared.service.AdminService.UserSort.NAME);assertFalse(users.results().isEmpty());assertFalse(users.previous());assertTrue(users.next());}finally{s.close();}return null;});}
}
