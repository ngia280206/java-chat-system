-- PK/UNIQUE indexes already exist; index reverse FK lookups and actual read paths.
CREATE INDEX ix_users_status_created ON chat.users(status, created_at DESC);
CREATE INDEX ix_friend_requests_receiver_pending ON chat.friend_requests(receiver_id, created_at DESC) WHERE status = 'PENDING';
CREATE INDEX ix_friend_requests_sender ON chat.friend_requests(sender_id, created_at DESC);
CREATE INDEX ix_friendships_reverse ON chat.friendships(user_id_2, user_id_1);
CREATE INDEX ix_user_blocks_reverse ON chat.user_blocks(blocked_id, blocker_id);
CREATE INDEX ix_conversations_creator ON chat.conversations(created_by);
CREATE INDEX ix_conversations_groups_created ON chat.conversations(created_at DESC) WHERE type = 'GROUP';
CREATE INDEX ix_members_user_active ON chat.conversation_members(user_id, conversation_id) WHERE left_at IS NULL;
CREATE INDEX ix_members_user_all ON chat.conversation_members(user_id);
CREATE INDEX ix_messages_sender ON chat.messages(sender_id, sent_at DESC);
CREATE INDEX ix_messages_key ON chat.messages(conversation_id, key_version) WHERE key_version IS NOT NULL;
CREATE UNIQUE INDEX uq_messages_nonce_per_key ON chat.messages(conversation_id, key_version, nonce)
    WHERE key_version IS NOT NULL;
CREATE INDEX ix_hidden_message ON chat.message_hidden_for_users(message_id);
CREATE INDEX ix_login_user_time ON chat.login_history(user_id, logged_in_at DESC);
CREATE INDEX ix_login_time ON chat.login_history(logged_in_at DESC);
CREATE INDEX ix_activity_user_time ON chat.activity_logs(user_id, occurred_at DESC);
CREATE INDEX ix_activity_conversation_time ON chat.activity_logs(conversation_id, occurred_at DESC) WHERE conversation_id IS NOT NULL;
CREATE INDEX ix_activity_time_type ON chat.activity_logs(occurred_at, event_type);
CREATE INDEX ix_reports_status_created ON chat.spam_reports(status, created_at DESC);
CREATE INDEX ix_reports_reporter ON chat.spam_reports(reporter_id, created_at DESC);
CREATE INDEX ix_reports_target ON chat.spam_reports(reported_user_id, created_at DESC);
CREATE INDEX ix_reports_message ON chat.spam_reports(message_id);
CREATE INDEX ix_devices_user ON chat.user_devices(user_id);
CREATE INDEX ix_key_versions_creator ON chat.group_key_versions(created_by);
CREATE INDEX ix_envelopes_device ON chat.group_key_envelopes(device_id);
