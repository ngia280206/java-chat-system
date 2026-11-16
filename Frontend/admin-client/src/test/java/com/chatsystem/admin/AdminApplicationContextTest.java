package com.chatsystem.admin;

import com.chatsystem.shared.model.status.UserStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminApplicationContextTest {
    @Test void contextsArePreauthenticatedAndIndependent() {
        var first = new AdminApplicationContext(); var second = new AdminApplicationContext();
        assertFalse(first.admin().getUsers().isEmpty());
        assertTrue(first.admin().lockUser(2).isSuccess());
        assertEquals(UserStatus.LOCKED, first.admin().getUser(2).orElseThrow().getStatus());
        assertEquals(UserStatus.ACTIVE, second.admin().getUser(2).orElseThrow().getStatus());
        assertSame(first.store().getUsers(), first.admin().getUsers());
    }
}
