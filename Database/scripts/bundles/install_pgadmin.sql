-- GENERATED from 01-08. Run ONCE in a new database. PostgreSQL 16+.
-- Use this OR 00_install.sql, never both. Do not modify this bundle alone.
BEGIN;

-- SOURCE: 01_schema_types.sql
-- PostgreSQL 16+. Initial migration: intentionally fails if chat already exists.
DO $$
BEGIN
    IF current_setting('server_version_num')::integer < 160000 THEN
        RAISE EXCEPTION 'PostgreSQL 16 or newer is required';
    END IF;
END $$;

CREATE SCHEMA chat;
COMMENT ON SCHEMA chat IS 'RELAY chat system; initial PostgreSQL implementation of the 14-table ERD.';
REVOKE ALL ON SCHEMA chat FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA chat REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TYPE chat.system_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE chat.user_status AS ENUM ('ACTIVE', 'LOCKED', 'DELETED');
CREATE TYPE chat.friend_request_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED');
CREATE TYPE chat.conversation_type AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE chat.encryption_mode AS ENUM ('NONE', 'E2EE');
CREATE TYPE chat.group_role AS ENUM ('ADMIN', 'MEMBER');
CREATE TYPE chat.activity_event_type AS ENUM ('APP_OPEN', 'MESSAGE_SENT');
CREATE TYPE chat.report_status AS ENUM ('PENDING', 'REVIEWED', 'DISMISSED');


-- SOURCE: 02_accounts_social.sql
CREATE TABLE chat.users (
    user_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar(64) NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name     varchar(150) NOT NULL,
    address       varchar(500),
    date_of_birth date,
    gender        varchar(32),
    system_role   chat.system_role NOT NULL DEFAULT 'USER',
    status        chat.user_status NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    timestamptz,
    CONSTRAINT ck_users_username CHECK (username = btrim(username) AND length(username) BETWEEN 3 AND 64),
    CONSTRAINT ck_users_email CHECK (email = btrim(email) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'),
    CONSTRAINT ck_users_password_hash CHECK (length(btrim(password_hash)) >= 20),
    CONSTRAINT ck_users_full_name CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT ck_users_deletion CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL)),
    CONSTRAINT ck_users_deleted_at CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);
-- Case-insensitive uniqueness, without requiring the citext extension.
CREATE UNIQUE INDEX uq_users_username_ci ON chat.users (lower(username));
CREATE UNIQUE INDEX uq_users_email_ci ON chat.users (lower(email));
COMMENT ON COLUMN chat.users.password_hash IS 'Backend-produced Argon2id/bcrypt hash; no plaintext password. Length check does not validate the hash algorithm.';

CREATE TABLE chat.friend_requests (
    request_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id    bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    receiver_id  bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    status       chat.friend_request_status NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at timestamptz,
    CONSTRAINT ck_friend_requests_not_self CHECK (sender_id <> receiver_id),
    CONSTRAINT ck_friend_requests_response CHECK ((status = 'PENDING') = (responded_at IS NULL)),
    CONSTRAINT ck_friend_requests_response_time CHECK (responded_at IS NULL OR responded_at >= created_at)
);
CREATE UNIQUE INDEX uq_friend_requests_pending_pair
    ON chat.friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id))
    WHERE status = 'PENDING';

CREATE TABLE chat.friendships (
    user_id_1  bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    user_id_2  bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id_1, user_id_2),
    CONSTRAINT ck_friendships_canonical_pair CHECK (user_id_1 < user_id_2)
);

CREATE TABLE chat.user_blocks (
    blocker_id bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    blocked_id bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);


-- SOURCE: 03_conversations_keys.sql
CREATE TABLE chat.conversations (
    conversation_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            chat.conversation_type NOT NULL,
    name            varchar(150),
    created_by      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encryption_mode chat.encryption_mode NOT NULL DEFAULT 'NONE',
    CONSTRAINT ck_conversations_name CHECK (
        (type = 'DIRECT' AND name IS NULL) OR
        (type = 'GROUP' AND name IS NOT NULL AND length(btrim(name)) > 0)
    ),
    -- The supplied ERD models key distribution for groups, not direct E2EE.
    CONSTRAINT ck_conversations_encryption CHECK (type = 'GROUP' OR encryption_mode = 'NONE')
);

CREATE TABLE chat.conversation_members (
    conversation_id bigint NOT NULL REFERENCES chat.conversations(conversation_id) ON DELETE RESTRICT,
    user_id         bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    group_role      chat.group_role NOT NULL DEFAULT 'MEMBER',
    joined_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at         timestamptz,
    history_cleared_through_seq bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT ck_members_left_at CHECK (left_at IS NULL OR left_at >= joined_at),
    CONSTRAINT ck_members_history_boundary CHECK (history_cleared_through_seq >= 0)
);
COMMENT ON COLUMN chat.conversation_members.history_cleared_through_seq IS 'Per-user visibility watermark, not a foreign key. Future messages remain visible.';

CREATE TABLE chat.user_devices (
    device_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    public_key bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz,
    CONSTRAINT ck_devices_public_key CHECK (octet_length(public_key) > 0),
    CONSTRAINT ck_devices_revoked_at CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE TABLE chat.group_key_versions (
    conversation_id bigint NOT NULL REFERENCES chat.conversations(conversation_id) ON DELETE RESTRICT,
    key_version     integer NOT NULL,
    created_by      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at      timestamptz,
    PRIMARY KEY (conversation_id, key_version),
    CONSTRAINT ck_key_versions_positive CHECK (key_version > 0),
    CONSTRAINT ck_key_versions_retired_at CHECK (retired_at IS NULL OR retired_at >= created_at)
);
CREATE UNIQUE INDEX uq_group_key_versions_current ON chat.group_key_versions(conversation_id)
    WHERE retired_at IS NULL;

CREATE TABLE chat.group_key_envelopes (
    conversation_id bigint NOT NULL,
    key_version     integer NOT NULL,
    device_id       bigint NOT NULL REFERENCES chat.user_devices(device_id) ON DELETE RESTRICT,
    wrapped_key     bytea NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, key_version, device_id),
    CONSTRAINT fk_envelopes_version FOREIGN KEY (conversation_id, key_version)
        REFERENCES chat.group_key_versions(conversation_id, key_version) ON DELETE RESTRICT,
    CONSTRAINT ck_envelopes_wrapped_key CHECK (octet_length(wrapped_key) > 0)
);
COMMENT ON TABLE chat.group_key_envelopes IS 'Wrapped group keys only. Private keys and plaintext group keys must never be persisted here.';


-- SOURCE: 04_messages_activity_reports.sql
CREATE TABLE chat.messages (
    message_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint NOT NULL REFERENCES chat.conversations(conversation_id) ON DELETE RESTRICT,
    sender_id       bigint NOT NULL,
    message_seq     bigint NOT NULL,
    content         text,
    ciphertext      bytea,
    nonce           bytea,
    key_version     integer,
    sent_at         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_messages_sequence UNIQUE (conversation_id, message_seq),
    CONSTRAINT fk_messages_sender FOREIGN KEY (conversation_id, sender_id)
        REFERENCES chat.conversation_members(conversation_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_messages_key FOREIGN KEY (conversation_id, key_version)
        REFERENCES chat.group_key_versions(conversation_id, key_version) ON DELETE RESTRICT,
    CONSTRAINT ck_messages_sequence CHECK (message_seq > 0),
    CONSTRAINT ck_messages_payload CHECK (
        (content IS NOT NULL AND length(btrim(content)) > 0 AND
            ciphertext IS NULL AND nonce IS NULL AND key_version IS NULL) OR
        (content IS NULL AND ciphertext IS NOT NULL AND octet_length(ciphertext) > 0 AND
            nonce IS NOT NULL AND octet_length(nonce) > 0 AND key_version IS NOT NULL)
    )
);
COMMENT ON COLUMN chat.messages.message_seq IS 'Allocated by trigger while locking the conversation; omit when inserting.';

CREATE TABLE chat.message_hidden_for_users (
    user_id    bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    message_id bigint NOT NULL REFERENCES chat.messages(message_id) ON DELETE RESTRICT,
    hidden_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, message_id)
);

CREATE TABLE chat.login_history (
    login_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    logged_in_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat.activity_logs (
    activity_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    event_type      chat.activity_event_type NOT NULL,
    conversation_id bigint REFERENCES chat.conversations(conversation_id) ON DELETE RESTRICT,
    occurred_at     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_activity_context CHECK (
        (event_type = 'APP_OPEN' AND conversation_id IS NULL) OR
        (event_type = 'MESSAGE_SENT' AND conversation_id IS NOT NULL)
    )
);

CREATE TABLE chat.spam_reports (
    report_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    reported_user_id bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    message_id       bigint NOT NULL REFERENCES chat.messages(message_id) ON DELETE RESTRICT,
    reason           text NOT NULL,
    status           chat.report_status NOT NULL DEFAULT 'PENDING',
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reports_not_self CHECK (reporter_id <> reported_user_id),
    CONSTRAINT ck_reports_reason CHECK (length(btrim(reason)) > 0)
);


-- SOURCE: 05_indexes.sql
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


-- SOURCE: 06_integrity_triggers.sql
-- These are integrity checks, not an authentication boundary. Functions are
-- SECURITY INVOKER. Only a trusted backend should hold database credentials.

CREATE FUNCTION chat.require_active_user(p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_status chat.user_status;
BEGIN
    SELECT status INTO v_status FROM chat.users WHERE user_id = p_user_id FOR SHARE;
    IF NOT FOUND OR v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Account % is missing or inactive', p_user_id USING ERRCODE = '23514';
    END IF;
END $$;

CREATE FUNCTION chat.lock_user_pair(p_first bigint, p_second bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    -- A collision only serializes unrelated pairs; it cannot corrupt data.
    PERFORM pg_advisory_xact_lock(hashtextextended(
        'chat.pair:' || LEAST(p_first, p_second) || ':' || GREATEST(p_first, p_second), 0));
END $$;

CREATE FUNCTION chat.pair_is_blocked(p_first bigint, p_second bigint) RETURNS boolean
LANGUAGE sql STABLE SET search_path = chat, pg_temp AS $$
    SELECT EXISTS (SELECT 1 FROM chat.user_blocks
        WHERE (blocker_id = p_first AND blocked_id = p_second)
           OR (blocker_id = p_second AND blocked_id = p_first));
$$;

CREATE FUNCTION chat.guard_user() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Use soft deletion: status=DELETED, deleted_at=clock_timestamp()' USING ERRCODE = '23514';
    END IF;
    IF NEW.user_id <> OLD.user_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Account identity and creation time are immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'DELETED' AND NEW.status <> 'DELETED' THEN
        RAISE EXCEPTION 'A deleted account cannot be reactivated' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER users_guard BEFORE UPDATE OR DELETE ON chat.users
FOR EACH ROW EXECUTE FUNCTION chat.guard_user();

CREATE FUNCTION chat.guard_social() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_first bigint; v_second bigint; v_needs_active boolean := true; v_pending boolean := false;
BEGIN
    IF TG_TABLE_NAME = 'friend_requests' THEN
        v_first := NEW.sender_id; v_second := NEW.receiver_id;
        v_needs_active := NEW.status IN ('PENDING', 'ACCEPTED');
        v_pending := NEW.status = 'PENDING';
        IF TG_OP = 'UPDATE' THEN
            IF (NEW.request_id, NEW.sender_id, NEW.receiver_id, NEW.created_at)
                IS DISTINCT FROM (OLD.request_id, OLD.sender_id, OLD.receiver_id, OLD.created_at)
                OR OLD.status <> 'PENDING' OR NEW.status = 'PENDING' THEN
                RAISE EXCEPTION 'Only a pending request can transition once to a final status' USING ERRCODE = '23514';
            END IF;
            NEW.responded_at := clock_timestamp();
        ELSIF NEW.status <> 'PENDING' THEN
            RAISE EXCEPTION 'New requests must be pending' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'friendships' THEN
        v_first := NEW.user_id_1; v_second := NEW.user_id_2;
    ELSE
        v_first := NEW.blocker_id; v_second := NEW.blocked_id;
    END IF;
    PERFORM chat.lock_user_pair(v_first, v_second);
    IF v_needs_active THEN
        PERFORM chat.require_active_user(LEAST(v_first, v_second));
        PERFORM chat.require_active_user(GREATEST(v_first, v_second));
        IF TG_TABLE_NAME <> 'user_blocks' AND chat.pair_is_blocked(v_first, v_second) THEN
            RAISE EXCEPTION 'This pair is blocked' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF v_pending
        AND EXISTS (SELECT 1 FROM chat.friendships
            WHERE user_id_1 = LEAST(v_first, v_second) AND user_id_2 = GREATEST(v_first, v_second)) THEN
        RAISE EXCEPTION 'These accounts are already friends' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER friend_requests_guard BEFORE INSERT OR UPDATE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();
CREATE TRIGGER friendships_guard BEFORE INSERT ON chat.friendships
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();
CREATE TRIGGER user_blocks_guard BEFORE INSERT ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();

CREATE FUNCTION chat.apply_social_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_TABLE_NAME = 'friend_requests' THEN
        IF NEW.status = 'ACCEPTED' THEN
            INSERT INTO chat.friendships(user_id_1, user_id_2)
            VALUES (LEAST(NEW.sender_id, NEW.receiver_id), GREATEST(NEW.sender_id, NEW.receiver_id))
            ON CONFLICT DO NOTHING;
        END IF;
    ELSE
        DELETE FROM chat.friendships
        WHERE user_id_1 = LEAST(NEW.blocker_id, NEW.blocked_id)
            AND user_id_2 = GREATEST(NEW.blocker_id, NEW.blocked_id);
        UPDATE chat.friend_requests SET status = 'CANCELLED'
        WHERE status = 'PENDING' AND LEAST(sender_id, receiver_id) = LEAST(NEW.blocker_id, NEW.blocked_id)
            AND GREATEST(sender_id, receiver_id) = GREATEST(NEW.blocker_id, NEW.blocked_id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER friend_requests_apply AFTER UPDATE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.apply_social_change();
CREATE TRIGGER user_blocks_apply AFTER INSERT ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.apply_social_change();

CREATE FUNCTION chat.guard_conversation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM chat.require_active_user(NEW.created_by);
    ELSIF (NEW.conversation_id, NEW.type, NEW.created_by, NEW.created_at, NEW.encryption_mode)
        IS DISTINCT FROM (OLD.conversation_id, OLD.type, OLD.created_by, OLD.created_at, OLD.encryption_mode) THEN
        RAISE EXCEPTION 'Conversation identity, participants type and encryption mode are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER conversations_guard BEFORE INSERT OR UPDATE ON chat.conversations
FOR EACH ROW EXECUTE FUNCTION chat.guard_conversation();

CREATE FUNCTION chat.guard_member() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_type chat.conversation_type; v_peer bigint; v_max bigint;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Keep memberships for history; use left_at to leave a group' USING ERRCODE = '23514';
    END IF;
    SELECT type INTO v_type FROM chat.conversations WHERE conversation_id = NEW.conversation_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conversation does not exist' USING ERRCODE = '23503';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.conversation_id, NEW.user_id, NEW.joined_at)
            IS DISTINCT FROM (OLD.conversation_id, OLD.user_id, OLD.joined_at) THEN
            RAISE EXCEPTION 'Membership identity and original join time are immutable' USING ERRCODE = '23514';
        END IF;
        IF NEW.history_cleared_through_seq < OLD.history_cleared_through_seq THEN
            RAISE EXCEPTION 'History watermark cannot move backwards' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'INSERT' OR (OLD.left_at IS NOT NULL AND NEW.left_at IS NULL) THEN
        PERFORM chat.require_active_user(NEW.user_id);
    END IF;
    SELECT COALESCE(max(message_seq), 0) INTO v_max FROM chat.messages WHERE conversation_id = NEW.conversation_id;
    IF NEW.history_cleared_through_seq > v_max THEN
        RAISE EXCEPTION 'History watermark exceeds the latest message' USING ERRCODE = '23514';
    END IF;
    IF v_type = 'DIRECT' THEN
        IF NEW.left_at IS NOT NULL OR NEW.group_role <> 'MEMBER' THEN
            RAISE EXCEPTION 'Direct participants cannot leave or hold group roles' USING ERRCODE = '23514';
        END IF;
        IF TG_OP = 'INSERT' THEN
            IF (SELECT count(*) FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id) >= 2 THEN
                RAISE EXCEPTION 'A direct conversation has exactly two participants' USING ERRCODE = '23514';
            END IF;
            SELECT user_id INTO v_peer FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id;
            IF FOUND THEN
                PERFORM chat.lock_user_pair(NEW.user_id, v_peer);
                IF EXISTS (
                    SELECT 1 FROM chat.conversation_members a
                    JOIN chat.conversation_members b USING (conversation_id)
                    JOIN chat.conversations c USING (conversation_id)
                    WHERE c.type = 'DIRECT' AND c.conversation_id <> NEW.conversation_id
                        AND a.user_id = NEW.user_id AND b.user_id = v_peer
                ) THEN
                    RAISE EXCEPTION 'A direct conversation already exists for this pair' USING ERRCODE = '23505';
                END IF;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER members_guard BEFORE INSERT OR UPDATE OR DELETE ON chat.conversation_members
FOR EACH ROW EXECUTE FUNCTION chat.guard_member();

CREATE FUNCTION chat.check_conversation_members() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint; v_conversation chat.conversations%ROWTYPE; v_total bigint; v_admins bigint;
BEGIN
    v_id := NEW.conversation_id;
    SELECT * INTO v_conversation FROM chat.conversations WHERE conversation_id = v_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*), count(*) FILTER (WHERE group_role = 'ADMIN') INTO v_total, v_admins
    FROM chat.conversation_members WHERE conversation_id = v_id AND left_at IS NULL;
    IF v_conversation.type = 'DIRECT' THEN
        IF v_total <> 2 THEN
            RAISE EXCEPTION 'Direct conversation % needs exactly two participants at commit', v_id USING ERRCODE = '23514';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = v_id
            AND user_id = v_conversation.created_by) THEN
            RAISE EXCEPTION 'Direct creator must be a participant' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF v_total < 1 OR v_admins < 1 THEN
            RAISE EXCEPTION 'Group % must retain a current admin', v_id USING ERRCODE = '23514';
        END IF;
        IF TG_TABLE_NAME = 'conversations' THEN
            IF v_total < 2 OR NOT EXISTS (SELECT 1 FROM chat.conversation_members
                WHERE conversation_id = v_id AND user_id = v_conversation.created_by
                    AND group_role = 'ADMIN' AND left_at IS NULL) THEN
                RAISE EXCEPTION 'New group needs its creator as admin and at least one other member' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END $$;
-- Deferred: a conversation and its initial members must be inserted in ONE transaction.
CREATE CONSTRAINT TRIGGER conversations_members_at_commit AFTER INSERT ON chat.conversations
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION chat.check_conversation_members();
CREATE CONSTRAINT TRIGGER members_at_commit AFTER INSERT OR UPDATE ON chat.conversation_members
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION chat.check_conversation_members();

CREATE FUNCTION chat.guard_message() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_conversation chat.conversations%ROWTYPE; v_peer bigint; v_next bigint;
BEGIN
    SELECT * INTO v_conversation FROM chat.conversations WHERE conversation_id = NEW.conversation_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Conversation does not exist' USING ERRCODE = '23503'; END IF;
    IF v_conversation.type = 'DIRECT' THEN
        SELECT user_id INTO v_peer FROM chat.conversation_members
        WHERE conversation_id = NEW.conversation_id AND user_id <> NEW.sender_id;
        IF v_peer IS NULL THEN RAISE EXCEPTION 'Direct conversation is incomplete' USING ERRCODE = '23514'; END IF;
        PERFORM chat.lock_user_pair(NEW.sender_id, v_peer);
        PERFORM chat.require_active_user(LEAST(NEW.sender_id, v_peer));
        PERFORM chat.require_active_user(GREATEST(NEW.sender_id, v_peer));
        IF chat.pair_is_blocked(NEW.sender_id, v_peer) THEN
            RAISE EXCEPTION 'Direct messages are unavailable for a blocked pair' USING ERRCODE = '23514';
        END IF;
    ELSE
        PERFORM chat.require_active_user(NEW.sender_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members
        WHERE conversation_id = NEW.conversation_id AND user_id = NEW.sender_id AND left_at IS NULL) THEN
        RAISE EXCEPTION 'Sender must be a current member' USING ERRCODE = '23514';
    END IF;
    IF v_conversation.encryption_mode = 'NONE' AND (NEW.content IS NULL OR NEW.key_version IS NOT NULL) THEN
        RAISE EXCEPTION 'This conversation requires a plaintext payload' USING ERRCODE = '23514';
    ELSIF v_conversation.encryption_mode = 'E2EE' THEN
        IF NEW.content IS NOT NULL OR NEW.key_version IS NULL OR NOT EXISTS (
            SELECT 1 FROM chat.group_key_versions WHERE conversation_id = NEW.conversation_id
                AND key_version = NEW.key_version AND retired_at IS NULL) THEN
            RAISE EXCEPTION 'E2EE requires ciphertext and the current group key version' USING ERRCODE = '23514';
        END IF;
    END IF;
    SELECT COALESCE(max(message_seq), 0) + 1 INTO v_next FROM chat.messages WHERE conversation_id = NEW.conversation_id;
    IF NEW.message_seq IS NOT NULL AND NEW.message_seq <> v_next THEN
        RAISE EXCEPTION 'message_seq must be the next sequence (%)', v_next USING ERRCODE = '23514';
    END IF;
    NEW.message_seq := v_next;
    NEW.sent_at := clock_timestamp();
    RETURN NEW;
END $$;
CREATE TRIGGER messages_guard BEFORE INSERT ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.guard_message();

CREATE FUNCTION chat.log_message_sent() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.activity_logs(user_id, event_type, conversation_id, occurred_at)
    VALUES (NEW.sender_id, 'MESSAGE_SENT', NEW.conversation_id, NEW.sent_at);
    RETURN NEW;
END $$;
CREATE TRIGGER messages_log AFTER INSERT ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.log_message_sent();

CREATE FUNCTION chat.deny_history_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; mutation % is unavailable', TG_TABLE_NAME, TG_OP USING ERRCODE = '23514';
END $$;
CREATE TRIGGER messages_immutable BEFORE UPDATE OR DELETE ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER activity_immutable BEFORE UPDATE OR DELETE ON chat.activity_logs
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER login_immutable BEFORE UPDATE OR DELETE ON chat.login_history
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER friendships_no_update BEFORE UPDATE ON chat.friendships
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER blocks_no_update BEFORE UPDATE ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER friend_requests_no_delete BEFORE DELETE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_activity() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_TABLE_NAME = 'activity_logs' THEN
        IF NEW.event_type = 'MESSAGE_SENT' THEN
            IF pg_trigger_depth() < 2 THEN
                RAISE EXCEPTION 'MESSAGE_SENT is written automatically by the messages trigger' USING ERRCODE = '23514';
            END IF;
        ELSE
            PERFORM chat.require_active_user(NEW.user_id);
        END IF;
    ELSE
        PERFORM chat.require_active_user(NEW.user_id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER activity_guard BEFORE INSERT ON chat.activity_logs
FOR EACH ROW EXECUTE FUNCTION chat.guard_activity();
CREATE TRIGGER login_guard BEFORE INSERT ON chat.login_history
FOR EACH ROW EXECUTE FUNCTION chat.guard_activity();

CREATE FUNCTION chat.guard_visibility_record() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_message chat.messages%ROWTYPE; v_actor bigint;
BEGIN
    IF TG_TABLE_NAME = 'spam_reports' AND TG_OP = 'UPDATE' THEN
        IF (NEW.report_id, NEW.reporter_id, NEW.reported_user_id, NEW.message_id, NEW.reason, NEW.created_at)
            IS DISTINCT FROM (OLD.report_id, OLD.reporter_id, OLD.reported_user_id, OLD.message_id, OLD.reason, OLD.created_at)
            OR OLD.status <> 'PENDING' OR NEW.status = 'PENDING' THEN
            RAISE EXCEPTION 'Only pending report status can change; original evidence is immutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO v_message FROM chat.messages WHERE message_id = NEW.message_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Message does not exist' USING ERRCODE = '23503'; END IF;
    PERFORM 1 FROM chat.conversations WHERE conversation_id = v_message.conversation_id FOR UPDATE;
    IF TG_TABLE_NAME = 'spam_reports' THEN
        v_actor := NEW.reporter_id;
        IF NEW.status <> 'PENDING' OR NEW.reported_user_id <> v_message.sender_id THEN
            RAISE EXCEPTION 'Report must be pending and target the actual sender' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM chat.message_hidden_for_users WHERE user_id = v_actor AND message_id = NEW.message_id) THEN
            RAISE EXCEPTION 'Hidden messages cannot be reported' USING ERRCODE = '23514';
        END IF;
    ELSE
        v_actor := NEW.user_id;
    END IF;
    PERFORM chat.require_active_user(v_actor);
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = v_message.conversation_id
        AND user_id = v_actor AND left_at IS NULL AND history_cleared_through_seq < v_message.message_seq) THEN
        RAISE EXCEPTION 'Message is not visible to this account' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER hidden_guard BEFORE INSERT ON chat.message_hidden_for_users
FOR EACH ROW EXECUTE FUNCTION chat.guard_visibility_record();
CREATE TRIGGER reports_guard BEFORE INSERT OR UPDATE ON chat.spam_reports
FOR EACH ROW EXECUTE FUNCTION chat.guard_visibility_record();
CREATE TRIGGER hidden_immutable BEFORE UPDATE OR DELETE ON chat.message_hidden_for_users
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER reports_no_delete BEFORE DELETE ON chat.spam_reports
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_crypto_record() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_mode chat.encryption_mode; v_owner bigint; v_revoked timestamptz;
BEGIN
    IF TG_TABLE_NAME = 'user_devices' THEN
        IF TG_OP = 'INSERT' THEN
            PERFORM chat.require_active_user(NEW.user_id);
        ELSIF (NEW.device_id, NEW.user_id, NEW.public_key, NEW.created_at)
            IS DISTINCT FROM (OLD.device_id, OLD.user_id, OLD.public_key, OLD.created_at)
            OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
            RAISE EXCEPTION 'Device identity/key are immutable and revocation is final' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT encryption_mode INTO v_mode FROM chat.conversations
    WHERE conversation_id = NEW.conversation_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND OR v_mode <> 'E2EE' THEN
        RAISE EXCEPTION 'Group keys require an E2EE group conversation' USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'group_key_versions' THEN
        IF TG_OP = 'UPDATE' THEN
            IF (NEW.conversation_id, NEW.key_version, NEW.created_by, NEW.created_at)
                IS DISTINCT FROM (OLD.conversation_id, OLD.key_version, OLD.created_by, OLD.created_at)
                OR (OLD.retired_at IS NOT NULL AND NEW.retired_at IS DISTINCT FROM OLD.retired_at) THEN
                RAISE EXCEPTION 'Key identity is immutable and retirement is final' USING ERRCODE = '23514';
            END IF;
        ELSE
            PERFORM chat.require_active_user(NEW.created_by);
            IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id
                AND user_id = NEW.created_by AND left_at IS NULL) THEN
                RAISE EXCEPTION 'Key creator must be a current group member' USING ERRCODE = '23514';
            END IF;
        END IF;
    ELSE
        SELECT user_id, revoked_at INTO v_owner, v_revoked FROM chat.user_devices WHERE device_id = NEW.device_id FOR SHARE;
        IF NOT FOUND OR v_revoked IS NOT NULL THEN
            RAISE EXCEPTION 'Envelope device is missing or revoked' USING ERRCODE = '23514';
        END IF;
        PERFORM chat.require_active_user(v_owner);
        IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id
            AND user_id = v_owner AND left_at IS NULL) OR NOT EXISTS (
            SELECT 1 FROM chat.group_key_versions WHERE conversation_id = NEW.conversation_id
                AND key_version = NEW.key_version AND retired_at IS NULL) THEN
            RAISE EXCEPTION 'Device owner needs current membership and an active key version' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER devices_guard BEFORE INSERT OR UPDATE ON chat.user_devices
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER versions_guard BEFORE INSERT OR UPDATE ON chat.group_key_versions
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER envelopes_guard BEFORE INSERT ON chat.group_key_envelopes
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER devices_no_delete BEFORE DELETE ON chat.user_devices
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER versions_no_delete BEFORE DELETE ON chat.group_key_versions
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER envelopes_immutable BEFORE UPDATE OR DELETE ON chat.group_key_envelopes
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_write_isolation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    -- The advisory-lock + fresh-query checks intentionally use READ COMMITTED.
    -- A repeatable snapshot could miss a pair committed while waiting for a lock.
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Chat writes require READ COMMITTED isolation (PostgreSQL default)'
            USING ERRCODE = '25000';
    END IF;
    RETURN NULL;
END $$;
DO $$
DECLARE v_table text;
BEGIN
    FOREACH v_table IN ARRAY ARRAY['users','friend_requests','friendships','user_blocks',
        'conversations','conversation_members','user_devices','group_key_versions',
        'group_key_envelopes','messages','message_hidden_for_users','login_history',
        'activity_logs','spam_reports'] LOOP
        EXECUTE format('CREATE TRIGGER write_isolation BEFORE INSERT OR UPDATE OR DELETE ON chat.%I '
            'FOR EACH STATEMENT EXECUTE FUNCTION chat.guard_write_isolation()',v_table);
    END LOOP;
END $$;

-- No PUBLIC execution grants. Grant only to a trusted server-side role later.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA chat FROM PUBLIC;


-- SOURCE: 07_service_functions.sql
-- Backend use-case helpers. p_actor_id MUST come from an authenticated server
-- session, never from an untrusted client request. All functions are invoker-rights.

CREATE FUNCTION chat.create_direct_conversation(p_actor_id bigint, p_other_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    IF p_actor_id IS NULL OR p_other_id IS NULL OR p_actor_id = p_other_id THEN
        RAISE EXCEPTION 'Choose two different accounts' USING ERRCODE = '23514';
    END IF;
    PERFORM chat.lock_user_pair(p_actor_id, p_other_id);
    PERFORM chat.require_active_user(LEAST(p_actor_id, p_other_id));
    PERFORM chat.require_active_user(GREATEST(p_actor_id, p_other_id));
    IF chat.pair_is_blocked(p_actor_id, p_other_id) THEN
        RAISE EXCEPTION 'This pair is blocked' USING ERRCODE = '23514';
    END IF;
    SELECT c.conversation_id INTO v_id FROM chat.conversations c
    JOIN chat.conversation_members a ON a.conversation_id = c.conversation_id AND a.user_id = p_actor_id
    JOIN chat.conversation_members b ON b.conversation_id = c.conversation_id AND b.user_id = p_other_id
    WHERE c.type = 'DIRECT';
    IF FOUND THEN RETURN v_id; END IF;
    INSERT INTO chat.conversations(type, created_by) VALUES ('DIRECT', p_actor_id) RETURNING conversation_id INTO v_id;
    INSERT INTO chat.conversation_members(conversation_id, user_id)
    VALUES (v_id, p_actor_id), (v_id, p_other_id);
    RETURN v_id;
END $$;

CREATE FUNCTION chat.create_group(p_actor_id bigint, p_name text, p_member_ids bigint[],
    p_encryption_mode chat.encryption_mode DEFAULT 'NONE') RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint; v_members bigint[]; v_member bigint;
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    SELECT array_agg(DISTINCT id ORDER BY id) INTO v_members
    FROM unnest(COALESCE(p_member_ids, ARRAY[]::bigint[]) || p_actor_id) id WHERE id IS NOT NULL;
    IF cardinality(v_members) < 2 THEN
        RAISE EXCEPTION 'A new group needs the creator and another member' USING ERRCODE = '23514';
    END IF;
    INSERT INTO chat.conversations(type, name, created_by, encryption_mode)
    VALUES ('GROUP', btrim(p_name), p_actor_id, p_encryption_mode) RETURNING conversation_id INTO v_id;
    FOREACH v_member IN ARRAY v_members LOOP
        INSERT INTO chat.conversation_members(conversation_id, user_id, group_role)
        VALUES (v_id, v_member, CASE WHEN v_member = p_actor_id THEN 'ADMIN'::chat.group_role ELSE 'MEMBER'::chat.group_role END);
    END LOOP;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.require_group_admin(p_actor_id bigint, p_group_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_group_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Group does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.require_active_user(p_actor_id);
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = p_group_id
        AND user_id = p_actor_id AND group_role = 'ADMIN' AND left_at IS NULL) THEN
        RAISE EXCEPTION 'Only a current group admin can perform this action' USING ERRCODE = '42501';
    END IF;
END $$;

CREATE FUNCTION chat.add_group_member(p_actor_id bigint, p_group_id bigint, p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    -- Invitation acceptance, if required, must first be verified by the backend;
    -- the supplied ERD has no persistent group_invitations table.
    INSERT INTO chat.conversation_members(conversation_id, user_id)
    VALUES (p_group_id, p_user_id)
    ON CONFLICT (conversation_id, user_id) DO UPDATE SET left_at = NULL, group_role = 'MEMBER'
    WHERE chat.conversation_members.left_at IS NOT NULL;
END $$;

CREATE FUNCTION chat.remove_group_member(p_actor_id bigint, p_group_id bigint, p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    IF p_actor_id = p_user_id THEN RAISE EXCEPTION 'Use leave_group for yourself' USING ERRCODE = '23514'; END IF;
    UPDATE chat.conversation_members SET left_at = clock_timestamp()
    WHERE conversation_id = p_group_id AND user_id = p_user_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Target is not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.change_group_role(p_actor_id bigint, p_group_id bigint, p_user_id bigint, p_role chat.group_role) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    UPDATE chat.conversation_members SET group_role = p_role
    WHERE conversation_id = p_group_id AND user_id = p_user_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Target is not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.leave_group(p_actor_id bigint, p_group_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_group_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Group does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.require_active_user(p_actor_id);
    UPDATE chat.conversation_members SET left_at = clock_timestamp()
    WHERE conversation_id = p_group_id AND user_id = p_actor_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'You are not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.send_text_message(p_actor_id bigint, p_conversation_id bigint, p_content text) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.messages(conversation_id, sender_id, content)
    VALUES (p_conversation_id, p_actor_id, btrim(p_content)) RETURNING message_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.send_encrypted_message(p_actor_id bigint, p_conversation_id bigint,
    p_ciphertext bytea, p_nonce bytea, p_key_version integer) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.messages(conversation_id, sender_id, ciphertext, nonce, key_version)
    VALUES (p_conversation_id, p_actor_id, p_ciphertext, p_nonce, p_key_version) RETURNING message_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.hide_message(p_actor_id bigint, p_message_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.message_hidden_for_users(user_id, message_id)
    VALUES (p_actor_id, p_message_id) ON CONFLICT DO NOTHING;
END $$;

CREATE FUNCTION chat.clear_history(p_actor_id bigint, p_conversation_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_boundary bigint;
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_conversation_id FOR UPDATE;
    PERFORM chat.require_active_user(p_actor_id);
    SELECT COALESCE(max(message_seq), 0) INTO v_boundary FROM chat.messages WHERE conversation_id = p_conversation_id;
    UPDATE chat.conversation_members SET history_cleared_through_seq = v_boundary
    WHERE conversation_id = p_conversation_id AND user_id = p_actor_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Current membership is required' USING ERRCODE = '23514'; END IF;
    RETURN v_boundary;
END $$;

CREATE FUNCTION chat.get_visible_messages(p_actor_id bigint, p_conversation_id bigint,
    p_limit integer DEFAULT 50, p_before_seq bigint DEFAULT NULL) RETURNS SETOF chat.messages
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'Page size must be 1..200' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY SELECT m.* FROM chat.messages m
    JOIN chat.conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = p_actor_id
    WHERE m.conversation_id = p_conversation_id AND cm.left_at IS NULL
        AND m.message_seq > cm.history_cleared_through_seq
        AND (p_before_seq IS NULL OR m.message_seq < p_before_seq)
        AND NOT EXISTS (SELECT 1 FROM chat.message_hidden_for_users h WHERE h.user_id = p_actor_id AND h.message_id = m.message_id)
    ORDER BY m.message_seq DESC LIMIT p_limit;
END $$;

CREATE FUNCTION chat.search_visible_messages(p_actor_id bigint, p_conversation_id bigint,
    p_query text, p_limit integer DEFAULT 50) RETURNS SETOF chat.messages
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF p_query IS NULL OR length(btrim(p_query)) = 0 OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'Nonempty search text and limit 1..200 are required' USING ERRCODE = '23514';
    END IF;
    -- Literal substring search: % and _ do not act as wildcard characters.
    -- Encrypted content is deliberately not searchable on the server.
    RETURN QUERY SELECT m.* FROM chat.messages m
    JOIN chat.conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = p_actor_id
    WHERE m.conversation_id = p_conversation_id AND cm.left_at IS NULL
        AND m.message_seq > cm.history_cleared_through_seq AND m.content IS NOT NULL
        AND strpos(lower(m.content), lower(btrim(p_query))) > 0
        AND NOT EXISTS (SELECT 1 FROM chat.message_hidden_for_users h WHERE h.user_id = p_actor_id AND h.message_id = m.message_id)
    ORDER BY m.message_seq DESC LIMIT p_limit;
END $$;

CREATE FUNCTION chat.send_friend_request(p_actor_id bigint, p_receiver_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.friend_requests(sender_id, receiver_id) VALUES (p_actor_id, p_receiver_id)
    RETURNING request_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.respond_friend_request(p_actor_id bigint, p_request_id bigint,
    p_status chat.friend_request_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_request chat.friend_requests%ROWTYPE;
BEGIN
    SELECT * INTO v_request FROM chat.friend_requests WHERE request_id = p_request_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Request does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.lock_user_pair(v_request.sender_id, v_request.receiver_id);
    SELECT * INTO v_request FROM chat.friend_requests WHERE request_id = p_request_id FOR UPDATE;
    PERFORM chat.require_active_user(p_actor_id);
    IF v_request.status <> 'PENDING' OR p_status IS NULL OR NOT (
        (p_actor_id = v_request.receiver_id AND p_status IN ('ACCEPTED', 'REJECTED')) OR
        (p_actor_id = v_request.sender_id AND p_status = 'CANCELLED')) THEN
        RAISE EXCEPTION 'This account cannot perform the requested transition' USING ERRCODE = '42501';
    END IF;
    UPDATE chat.friend_requests SET status = p_status WHERE request_id = p_request_id;
END $$;

CREATE FUNCTION chat.block_user(p_actor_id bigint, p_target_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.user_blocks(blocker_id, blocked_id) VALUES (p_actor_id, p_target_id) ON CONFLICT DO NOTHING;
END $$;

CREATE FUNCTION chat.unblock_user(p_actor_id bigint, p_target_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.lock_user_pair(p_actor_id, p_target_id);
    PERFORM chat.require_active_user(p_actor_id);
    DELETE FROM chat.user_blocks WHERE blocker_id = p_actor_id AND blocked_id = p_target_id;
END $$;

CREATE FUNCTION chat.submit_spam_report(p_actor_id bigint, p_message_id bigint, p_reason text) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.spam_reports(reporter_id, reported_user_id, message_id, reason)
    SELECT p_actor_id, sender_id, message_id, btrim(p_reason) FROM chat.messages WHERE message_id = p_message_id
    RETURNING report_id INTO v_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Message does not exist' USING ERRCODE = '23514'; END IF;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.require_system_admin(p_actor_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF NOT EXISTS (SELECT 1 FROM chat.users WHERE user_id = p_actor_id AND system_role = 'ADMIN') THEN
        RAISE EXCEPTION 'System administrator permission is required' USING ERRCODE = '42501';
    END IF;
END $$;

CREATE FUNCTION chat.review_spam_report(p_actor_id bigint, p_report_id bigint, p_status chat.report_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_system_admin(p_actor_id);
    IF p_status IS NULL OR p_status NOT IN ('REVIEWED', 'DISMISSED') THEN
        RAISE EXCEPTION 'Choose REVIEWED or DISMISSED' USING ERRCODE = '23514';
    END IF;
    UPDATE chat.spam_reports SET status = p_status WHERE report_id = p_report_id AND status = 'PENDING';
    IF NOT FOUND THEN RAISE EXCEPTION 'Report is missing or already resolved' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.set_account_locked(p_actor_id bigint, p_target_id bigint, p_locked boolean) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF p_locked IS NULL OR p_actor_id = p_target_id THEN
        RAISE EXCEPTION 'Choose another account and an explicit lock state' USING ERRCODE = '23514';
    END IF;
    PERFORM chat.require_system_admin(p_actor_id);
    UPDATE chat.users SET status = CASE WHEN p_locked THEN 'LOCKED'::chat.user_status ELSE 'ACTIVE'::chat.user_status END
    WHERE user_id = p_target_id AND status <> 'DELETED';
    IF NOT FOUND THEN RAISE EXCEPTION 'Account is missing or deleted' USING ERRCODE = '23514'; END IF;
END $$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA chat FROM PUBLIC;


-- SOURCE: 08_views.sql
-- Administrative projections. These are NOT row-level access-control policies.
CREATE VIEW chat.v_group_summary AS
SELECT c.conversation_id, c.name, c.created_by, c.created_at, c.encryption_mode,
    count(cm.user_id) FILTER (WHERE cm.left_at IS NULL) AS member_count,
    count(cm.user_id) FILTER (WHERE cm.left_at IS NULL AND cm.group_role = 'ADMIN') AS admin_count
FROM chat.conversations c LEFT JOIN chat.conversation_members cm USING (conversation_id)
WHERE c.type = 'GROUP'
GROUP BY c.conversation_id;

CREATE VIEW chat.v_user_last_login AS
SELECT user_id, max(logged_in_at) AS last_login_at, count(*) AS successful_login_count
FROM chat.login_history GROUP BY user_id;

CREATE VIEW chat.v_daily_activity AS
SELECT (occurred_at AT TIME ZONE 'UTC')::date AS activity_date_utc,
    count(*) FILTER (WHERE event_type = 'APP_OPEN') AS app_open_count,
    count(*) FILTER (WHERE event_type = 'MESSAGE_SENT') AS message_count,
    count(DISTINCT user_id) AS active_user_count,
    count(DISTINCT user_id) FILTER (WHERE event_type = 'MESSAGE_SENT') AS chatting_user_count,
    count(DISTINCT conversation_id) FILTER (WHERE event_type = 'MESSAGE_SENT') AS chatting_conversation_count
FROM chat.activity_logs GROUP BY (occurred_at AT TIME ZONE 'UTC')::date;

COMMENT ON VIEW chat.v_daily_activity IS 'Daily DISTINCT counts cannot be summed to obtain unique users/conversations for a multi-day range.';

COMMIT;
