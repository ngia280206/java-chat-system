package com.chatsystem.shared.model;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.validation.*;
import javafx.beans.value.ObservableValue;
import javafx.beans.property.Property;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservableModelTest {
    @Test
    void profileAndStatusEditsNotifyExistingViewsWithoutReplacingIdentity() {
        UserAccount user = MockFixtures.createStore().getUsers().get(0);
        long id = user.getId();
        assertChange(user.displayNameProperty(), () -> user.updateProfile("New name", "newname", "New bio"), "New name");
        assertEquals("newname", user.getUsername());
        assertEquals("New bio", user.getBio());
        assertChange(user.statusProperty(), () -> user.setStatus(UserStatus.LOCKED), UserStatus.LOCKED);
        assertEquals(id, user.getId());
        assertFalse(user.displayNameProperty() instanceof Property<?>);
        assertFalse(user.statusProperty() instanceof Property<?>);
    }

    @Test
    void conversationEditsAndNewMessagePreviewNotifyExistingViews() {
        Conversation conversation = MockFixtures.createStore().getConversations().stream()
                .filter(c -> c.getId() == 20L).findFirst().orElseThrow();
        assertChange(conversation.nameProperty(), () -> conversation.updateDetails("New group", "New description"), "New group");
        assertEquals("New description", conversation.getDescription());
        Instant sent = Instant.parse("2026-11-14T05:00:00Z");
        assertChange(conversation.lastMessagePreviewProperty(), () -> conversation.updatePreview("See you soon", sent), "See you soon");
        assertEquals(sent, conversation.getLastMessageAt());
    }

    @Test
    void membershipTracksPerUserUnreadAndClearBoundaryIndependently() {
        ConversationMember member = new ConversationMember(20, 1, GroupRole.ADMIN, Instant.EPOCH);
        assertChange(member.roleProperty(), () -> member.setRole(GroupRole.MEMBER), GroupRole.MEMBER);
        assertChange(member.unreadCountProperty(), () -> member.setUnreadCount(3), 3);
        member.clearHistoryThrough(7);
        assertEquals(7, member.getHistoryClearedThroughSeq());
        assertEquals(0, new ConversationMember(20, 2, GroupRole.MEMBER, Instant.EPOCH).getHistoryClearedThroughSeq());
        assertChange(member.leftAtProperty(), () -> member.setLeftAt(Instant.EPOCH.plusSeconds(20)), Instant.EPOCH.plusSeconds(20));
        assertThrows(IllegalArgumentException.class, () -> member.setUnreadCount(-1));
        assertThrows(IllegalArgumentException.class, () -> member.clearHistoryThrough(-1));
    }

    @Test
    void requestAndInvitationDecisionsNotifyAndRememberResponseTime() {
        FriendRequest request = new FriendRequest(1, 2, 3, Instant.EPOCH);
        Instant response = Instant.EPOCH.plusSeconds(60);
        assertChange(request.statusProperty(), () -> request.respond(FriendRequestStatus.ACCEPTED, response), FriendRequestStatus.ACCEPTED);
        assertEquals(response, request.getRespondedAt());
        GroupInvitation invitation = new GroupInvitation(2, 20, 1, 3, Instant.EPOCH);
        assertChange(invitation.statusProperty(), () -> invitation.respond(FriendRequestStatus.REJECTED, response), FriendRequestStatus.REJECTED);
        assertEquals(response, invitation.getRespondedAt());
    }

    @Test
    void reportReviewNotifiesViewsAndKeepsModerationSeparateFromUserStatus() {
        SpamReport report = new SpamReport(1, 1, 2, 100, "Unwanted promotion", Instant.EPOCH);
        assertChange(report.statusProperty(), () -> report.review(ReportStatus.REVIEWED, "Warning issued"), ReportStatus.REVIEWED);
        assertEquals("Warning issued", report.getReviewNotes());
        assertEquals(2, report.getReportedUserId());
    }

    @Test
    void settingsChangesNotifyViewsAndDoNotAffectAnotherUser() {
        UserSettings first = new UserSettings(1);
        UserSettings second = new UserSettings(2);
        assertChange(first.soundEnabledProperty(), () -> first.updatePreferences(false, true, false), true);
        assertFalse(first.isDesktopNotifications());
        assertFalse(first.isMessagePreviews());
        assertTrue(second.isDesktopNotifications());
        assertFalse(second.isSoundEnabled());
        assertTrue(second.isMessagePreviews());
    }

    @Test
    void validationResultsSeparateSuccessFromImmutableFieldAndGlobalErrors() {
        ValidationResult<String> success = ValidationResult.success("saved");
        assertTrue(success.isSuccess());
        assertEquals("saved", success.value().orElseThrow());
        assertTrue(success.errors().isEmpty());
        List<ValidationError> errors = new ArrayList<>(List.of(
                ValidationError.field("email", "Email is required"), ValidationError.global("Account is locked")));
        ValidationResult<String> failure = ValidationResult.failure(errors);
        errors.clear();
        assertFalse(failure.isSuccess());
        assertTrue(failure.value().isEmpty());
        assertEquals(2, failure.errors().size());
        assertEquals("email", failure.errors().get(0).field());
        assertTrue(failure.errors().get(1).isGlobal());
        assertThrows(UnsupportedOperationException.class, () -> failure.errors().clear());
        assertThrows(IllegalArgumentException.class, () -> ValidationResult.failure(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ValidationError.field(" ", "Message"));
        assertThrows(IllegalArgumentException.class, () -> ValidationError.global(" "));
        assertTrue(ValidationResult.<Void>success(null).isSuccess());
    }

    private static <T> void assertChange(ObservableValue<T> property, Runnable mutation, T expected) {
        List<T> notifications = new ArrayList<>();
        property.addListener((observable, oldValue, newValue) -> notifications.add(newValue));
        mutation.run();
        assertEquals(List.of(expected), notifications);
    }
}
