package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.UserStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class V1FeatureServiceTest {
    @Test void adminCrudAndPersonalDetailsAreAvailableInTheDemo() {
        var service = new InMemoryAdminService(MockFixtures.createStore());
        var created = service.createUser("Student Demo", "student_demo", "student@example.com",
                "District 1", LocalDate.of(2004, 5, 12), "Other");
        assertTrue(created.isSuccess());
        var user = created.value().orElseThrow();
        assertEquals("District 1", user.getAddress());
        assertEquals(LocalDate.of(2004,5,12), user.getDateOfBirth());
        assertEquals("Other", user.getGender());
        assertTrue(service.updateUser(user.getId(), "Student Updated", "student_new", "student2@example.com",
                "District 7", LocalDate.of(2004, 5, 12), "Not specified").isSuccess());
        assertEquals("student2@example.com", user.getEmail());
        assertTrue(service.setUserPassword(user.getId(), "ChangedPass123!").isSuccess());
        assertTrue(service.deleteUser(user.getId()).isSuccess());
        assertEquals(UserStatus.DELETED, user.getStatus());
        assertFalse(service.setUserPassword(user.getId(), "OtherPass123!").isSuccess());
    }

    @Test void adminCanQuerySystemLoginsAndDirectFriends() {
        var service = new InMemoryAdminService(MockFixtures.createStore());
        assertFalse(service.getAllLoginHistory().isEmpty());
        assertEquals(3, service.getFriendIds(MockFixtures.DEMO_USER_ID).size());
        assertTrue(service.getAllLoginHistory().stream().allMatch(e -> service.getUser(e.userId()).isPresent()));
    }

    @Test void friendRemovalUpdatesTheLiveList() {
        var store = MockFixtures.createStore();
        var auth = new InMemoryAuthService(store);
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL,MockFixtures.DEMO_PASSWORD,false).isSuccess());
        var social = new InMemorySocialService(store,auth.getSession());
        assertEquals(3,social.getFriends().size());
        assertTrue(social.removeFriend(2).isSuccess());
        assertEquals(2,social.getFriends().size());
        assertFalse(social.removeFriend(2).isSuccess());
        assertFalse(social.getDiscoverableUsers().stream().anyMatch(u -> u.getId()==MockFixtures.DEMO_USER_ID));
        assertFalse(social.getDiscoverableUsers().stream().anyMatch(u -> u.getId()==MockFixtures.LOCKED_USER_ID));
    }
}
