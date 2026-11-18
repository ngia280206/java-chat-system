-- RELAY Version 1 relational schema design (PostgreSQL 15+)
-- Design deliverable only. JavaFX demos continue to use MockDataStore in memory.
-- Store passwords as strong one-way hashes in a future backend. No SMTP credentials here.

CREATE TABLE app_users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    bio VARCHAR(500) NOT NULL DEFAULT '',
    address VARCHAR(500) NOT NULL DEFAULT '',
    birth_date DATE,
    gender VARCHAR(20) NOT NULL DEFAULT 'Not specified',
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE','LOCKED','DELETED')),
    profile_visibility VARCHAR(20) NOT NULL DEFAULT 'FRIENDS_ONLY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE friendship (
    user_id_low BIGINT NOT NULL REFERENCES app_users(id),
    user_id_high BIGINT NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id_low, user_id_high),
    CHECK (user_id_low < user_id_high)
);

CREATE TABLE friend_request (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES app_users(id),
    receiver_id BIGINT NOT NULL REFERENCES app_users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CHECK (sender_id <> receiver_id)
);
CREATE INDEX idx_friend_requests_receiver ON friend_request(receiver_id, status);

CREATE TABLE user_block (
    blocker_id BIGINT NOT NULL REFERENCES app_users(id),
    blocked_id BIGINT NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE conversations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type VARCHAR(8) NOT NULL CHECK (type IN ('DIRECT','GROUP')),
    created_by BIGINT NOT NULL REFERENCES app_users(id),
    name VARCHAR(120) NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE conversation_member (
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER','ADMIN')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    PRIMARY KEY (conversation_id,user_id)
);
CREATE TABLE group_invitation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    inviter_id BIGINT NOT NULL REFERENCES app_users(id),
    invitee_id BIGINT NOT NULL REFERENCES app_users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);

CREATE TABLE chat_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    sender_id BIGINT NOT NULL REFERENCES app_users(id),
    sequence_number BIGINT NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, sequence_number)
);
CREATE INDEX idx_messages_conversation_time ON chat_message(conversation_id, sent_at);

CREATE TABLE spam_report (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES app_users(id),
    reported_user_id BIGINT NOT NULL REFERENCES app_users(id),
    message_id BIGINT NOT NULL REFERENCES chat_message(id),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_created ON spam_report(created_at DESC);
CREATE INDEX idx_reports_status ON spam_report(status);

CREATE TABLE login_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    logged_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    device VARCHAR(250),
    location VARCHAR(250)
);
CREATE INDEX idx_login_history_time ON login_history(logged_in_at DESC);
CREATE INDEX idx_login_user_time ON login_history(user_id, logged_in_at DESC);

CREATE TABLE activity_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('APP_OPEN','MESSAGE_SENT')),
    conversation_id BIGINT REFERENCES conversations(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (event_type <> 'MESSAGE_SENT' OR conversation_id IS NOT NULL)
);
CREATE INDEX idx_activity_type_time ON activity_event(event_type, occurred_at DESC);
CREATE INDEX idx_activity_user_time ON activity_event(user_id, occurred_at DESC);

-- Analytics reports use queries/aggregation rather than storing derived friend counts:
-- 1) registrations by month: app_users.created_at
-- 2) monthly active users: COUNT(DISTINCT user_id) on APP_OPEN events
-- 3) direct / two-hop friends: friendship graph
-- 4) chats by person / group: activity_event joined with conversations.type
