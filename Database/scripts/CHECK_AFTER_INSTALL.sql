-- Chi doc, chay sau INSTALL_NEW_DATABASE hoac UPDATE_EXISTING_DATABASE.
SELECT count(*) AS total_chat_tables
FROM information_schema.tables WHERE table_schema='chat' AND table_type='BASE TABLE';

SELECT count(*) AS users_missing_settings
FROM chat.users u LEFT JOIN chat.user_settings s USING(user_id) WHERE s.user_id IS NULL;

SELECT count(*) AS group_invitations_pointing_to_direct
FROM chat.group_invitations i JOIN chat.conversations c USING(conversation_id) WHERE c.type<>'GROUP';

SELECT count(*) AS accepted_invites_without_membership_history
FROM chat.group_invitations i WHERE i.status='ACCEPTED' AND NOT EXISTS(
    SELECT 1 FROM chat.conversation_members m WHERE m.conversation_id=i.conversation_id AND m.user_id=i.invitee_id);

SELECT count(*) AS decisions_still_pending
FROM chat.moderation_actions a JOIN chat.spam_reports r USING(report_id) WHERE r.status='PENDING';

-- Cac report da xu ly tu truoc co the chua co admin/notes; day la thong tin lich su, khong tu bia du lieu.
SELECT r.report_id,r.status,a.action_type,a.admin_id,a.notes
FROM chat.spam_reports r LEFT JOIN chat.moderation_actions a USING(report_id) ORDER BY r.report_id;
