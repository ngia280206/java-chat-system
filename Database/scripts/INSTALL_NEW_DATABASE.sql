-- INSTALL_NEW_DATABASE: chi chay tren database TRONG. Khong tu xoa schema/du lieu.
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
ALTER DEFAULT PRIVILEGES
REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

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


-- SOURCE: 05_group_invitations.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.

-- Giao dien co moi vao nhom, chap nhan va tu choi; ERD cu chua luu loi moi.
-- Dung lai ENUM cua friend_requests vi cung 4 gia tri trang thai.
CREATE TABLE chat.group_invitations (
    invitation_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint NOT NULL REFERENCES chat.conversations(conversation_id) ON DELETE RESTRICT,
    inviter_id      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    invitee_id      bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    status          chat.friend_request_status NOT NULL DEFAULT 'PENDING',
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    responded_at    timestamptz,
    CONSTRAINT ck_group_invitations_not_self CHECK (inviter_id <> invitee_id),
    CONSTRAINT ck_group_invitations_response CHECK ((status = 'PENDING') = (responded_at IS NULL)),
    CONSTRAINT ck_group_invitations_time CHECK (responded_at IS NULL OR responded_at >= created_at)
);
-- Mot nguoi chi co mot loi moi dang cho trong cung nhom, du nhieu admin moi.
CREATE UNIQUE INDEX uq_group_invitations_pending ON chat.group_invitations(conversation_id,invitee_id)
    WHERE status = 'PENDING';
CREATE INDEX ix_group_invitations_invitee ON chat.group_invitations(invitee_id,status,created_at DESC);
CREATE INDEX ix_group_invitations_inviter ON chat.group_invitations(inviter_id,created_at DESC);
COMMENT ON TABLE chat.group_invitations IS 'Luu loi moi vao nhom. Trigger dong bo membership khi chap nhan.';



-- SOURCE: 06_user_settings.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.

-- Giao dien co cac tuy chon thong bao, am thanh va xem truoc tin nhan.
CREATE TABLE chat.user_settings (
    user_id               bigint PRIMARY KEY REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    desktop_notifications boolean NOT NULL DEFAULT true,
    sound_enabled         boolean NOT NULL DEFAULT false,
    message_previews      boolean NOT NULL DEFAULT true,
    updated_at            timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE chat.user_settings IS 'Moi user co toi da mot bo cai dat. Du lieu mac dinh va updated_at duoc dong bo boi file 19 va 21.';



-- SOURCE: 07_password_reset_requests.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.

-- Giao dien co quen mat khau va nhap ma; can luu yeu cau, han dung va so lan sai.
CREATE TABLE chat.password_reset_requests (
    reset_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    token_hash      bytea NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at      timestamptz NOT NULL,
    verified_at     timestamptz,
    consumed_at     timestamptz,
    revoked_at      timestamptz,
    failed_attempts integer NOT NULL DEFAULT 0,
    attempt_limit   smallint NOT NULL DEFAULT 5,
    CONSTRAINT ck_reset_hash CHECK (octet_length(token_hash) BETWEEN 32 AND 128),
    CONSTRAINT ck_reset_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_reset_attempts CHECK (attempt_limit BETWEEN 1 AND 20 AND failed_attempts BETWEEN 0 AND attempt_limit),
    CONSTRAINT ck_reset_verified_time CHECK (verified_at IS NULL OR verified_at >= created_at),
    CONSTRAINT ck_reset_consumed_time CHECK (consumed_at IS NULL OR (verified_at IS NOT NULL AND consumed_at >= verified_at)),
    CONSTRAINT ck_reset_revoked_time CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_reset_terminal CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);
-- Tao yeu cau moi phai thu hoi yeu cau cu chua dung, ke ca yeu cau da het han.
-- Khong dung CURRENT_TIMESTAMP trong dieu kien index.
CREATE UNIQUE INDEX uq_reset_open_user ON chat.password_reset_requests(user_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX ix_reset_user_history ON chat.password_reset_requests(user_id,created_at DESC);
CREATE INDEX ix_reset_expiry ON chat.password_reset_requests(expires_at)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
COMMENT ON COLUMN chat.password_reset_requests.token_hash IS
    'Hash/HMAC do backend tao; khong luu token hay OTP goc. Voi OTP ngan, can bien phap bao ve phu hop va gioi han thu.';



-- SOURCE: 08_moderation_actions.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.
-- ENUM moi duoc tao cung bang trong file nay.

-- Giao dien admin co ghi chu, canh cao va khoa tai khoan; status report chua du.
CREATE TYPE chat.moderation_action_type AS ENUM ('REVIEW','DISMISS','WARN','SUSPEND');
CREATE TABLE chat.moderation_actions (
    action_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id   bigint NOT NULL UNIQUE REFERENCES chat.spam_reports(report_id) ON DELETE RESTRICT,
    admin_id    bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    action_type chat.moderation_action_type NOT NULL,
    notes       text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_moderation_notes CHECK (length(btrim(notes)) > 0)
);
CREATE INDEX ix_moderation_admin_time ON chat.moderation_actions(admin_id,created_at DESC);
COMMENT ON TABLE chat.moderation_actions IS
    'Luu admin, hanh dong va ghi chu xu ly; moi report co toi da mot quyet dinh.';



-- SOURCE: 09_indexes.sql
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


-- SOURCE: 10_integrity_triggers.sql
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


-- SOURCE: 11_service_functions.sql
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


-- SOURCE: 12_sync_triggers.sql
-- 1. Tu dong tao cai dat khi dang ky; cap nhat moc thoi gian khi luu.
CREATE OR REPLACE FUNCTION chat.create_default_settings() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    INSERT INTO chat.user_settings(user_id) VALUES(NEW.user_id);
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER users_default_settings AFTER INSERT ON chat.users
FOR EACH ROW EXECUTE FUNCTION chat.create_default_settings();

CREATE OR REPLACE FUNCTION chat.guard_settings() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.user_id <> OLD.user_id THEN
        RAISE EXCEPTION 'Khong duoc doi chu so huu cai dat' USING ERRCODE = '23514';
    END IF;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER settings_guard BEFORE INSERT OR UPDATE ON chat.user_settings
FOR EACH ROW EXECUTE FUNCTION chat.guard_settings();
CREATE OR REPLACE TRIGGER settings_no_delete BEFORE DELETE ON chat.user_settings
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

-- 2. Loi moi: khoa dong hoi thoai de dong bo voi thay doi thanh vien.
CREATE OR REPLACE FUNCTION chat.guard_group_invitation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = NEW.conversation_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Loi moi phai thuoc mot GROUP' USING ERRCODE = '23514'; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.invitation_id,NEW.conversation_id,NEW.inviter_id,NEW.invitee_id,NEW.created_at)
            IS DISTINCT FROM (OLD.invitation_id,OLD.conversation_id,OLD.inviter_id,OLD.invitee_id,OLD.created_at)
            OR OLD.status <> 'PENDING' OR NEW.status = 'PENDING' THEN
            RAISE EXCEPTION 'Chi loi moi dang cho moi duoc xu ly mot lan' USING ERRCODE = '23514';
        END IF;
        NEW.responded_at := clock_timestamp();
    ELSIF NEW.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Loi moi moi phai o trang thai PENDING' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' OR NEW.status = 'ACCEPTED' THEN
        PERFORM chat.require_group_admin(NEW.inviter_id,NEW.conversation_id);
        PERFORM chat.require_active_user(NEW.invitee_id);
        IF EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id
            AND user_id = NEW.invitee_id AND left_at IS NULL) THEN
            RAISE EXCEPTION 'Nguoi nhan da la thanh vien' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER invitations_guard BEFORE INSERT OR UPDATE ON chat.group_invitations
FOR EACH ROW EXECUTE FUNCTION chat.guard_group_invitation();

CREATE OR REPLACE FUNCTION chat.apply_accepted_invitation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF NEW.status = 'ACCEPTED' THEN
        INSERT INTO chat.conversation_members(conversation_id,user_id,group_role)
        VALUES(NEW.conversation_id,NEW.invitee_id,'MEMBER')
        ON CONFLICT (conversation_id,user_id) DO UPDATE SET left_at = NULL,group_role = 'MEMBER';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER invitations_accept AFTER UPDATE ON chat.group_invitations
FOR EACH ROW EXECUTE FUNCTION chat.apply_accepted_invitation();
CREATE OR REPLACE TRIGGER invitations_no_delete BEFORE DELETE ON chat.group_invitations
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

-- 3. Reset: giu thong tin goc va khong cho dat lai bo dem/hoi sinh yeu cau cu.
CREATE OR REPLACE FUNCTION chat.guard_password_reset() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM chat.require_active_user(NEW.user_id);
        IF NEW.failed_attempts <> 0 OR NEW.verified_at IS NOT NULL
            OR NEW.consumed_at IS NOT NULL OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'Yeu cau reset moi phai chua duoc xu ly' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF (NEW.reset_id,NEW.user_id,NEW.token_hash,NEW.created_at,NEW.expires_at,NEW.attempt_limit)
            IS DISTINCT FROM (OLD.reset_id,OLD.user_id,OLD.token_hash,OLD.created_at,OLD.expires_at,OLD.attempt_limit)
            OR NEW.failed_attempts < OLD.failed_attempts
            OR (OLD.verified_at IS NOT NULL AND NEW.verified_at IS DISTINCT FROM OLD.verified_at)
            OR OLD.consumed_at IS NOT NULL OR OLD.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'Khong duoc sua thong tin goc hay dung lai yeu cau reset da dong' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER reset_guard BEFORE INSERT OR UPDATE ON chat.password_reset_requests
FOR EACH ROW EXECUTE FUNCTION chat.guard_password_reset();
CREATE OR REPLACE TRIGGER reset_no_delete BEFORE DELETE ON chat.password_reset_requests
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

-- 4. Quyet dinh kiem duyet va ket qua phai duoc luu trong cung transaction.
CREATE OR REPLACE FUNCTION chat.guard_moderation_action() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_status chat.report_status;
BEGIN
    SELECT status INTO v_status FROM chat.spam_reports WHERE report_id = NEW.report_id FOR UPDATE;
    IF NOT FOUND OR v_status <> 'PENDING' THEN
        RAISE EXCEPTION 'Bao cao khong ton tai hoac da xu ly' USING ERRCODE = '23514';
    END IF;
    PERFORM chat.require_system_admin(NEW.admin_id);
    NEW.notes := btrim(NEW.notes);
    NEW.created_at := clock_timestamp();
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER moderation_guard BEFORE INSERT ON chat.moderation_actions
FOR EACH ROW EXECUTE FUNCTION chat.guard_moderation_action();

CREATE OR REPLACE FUNCTION chat.apply_moderation_action() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF NEW.action_type = 'SUSPEND' THEN
        UPDATE chat.users SET status = 'LOCKED'
        WHERE user_id = (SELECT reported_user_id FROM chat.spam_reports WHERE report_id = NEW.report_id)
            AND status <> 'DELETED';
        IF NOT FOUND THEN RAISE EXCEPTION 'Khong the khoa tai khoan da xoa' USING ERRCODE = '23514'; END IF;
    END IF;
    UPDATE chat.spam_reports
    SET status = CASE WHEN NEW.action_type = 'DISMISS' THEN 'DISMISSED'::chat.report_status ELSE 'REVIEWED'::chat.report_status END
    WHERE report_id = NEW.report_id;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER moderation_apply AFTER INSERT ON chat.moderation_actions
FOR EACH ROW EXECUTE FUNCTION chat.apply_moderation_action();
CREATE OR REPLACE TRIGGER moderation_immutable BEFORE UPDATE OR DELETE ON chat.moderation_actions
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE OR REPLACE FUNCTION chat.require_report_decision() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT EXISTS (
        SELECT 1 FROM chat.moderation_actions WHERE report_id = NEW.report_id
            AND CASE WHEN action_type = 'DISMISS' THEN NEW.status = 'DISMISSED' ELSE NEW.status = 'REVIEWED' END
    ) THEN
        RAISE EXCEPTION 'Hay ghi moderation_actions hoac goi moderate_report de xu ly bao cao' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER reports_require_decision BEFORE UPDATE ON chat.spam_reports
FOR EACH ROW EXECUTE FUNCTION chat.require_report_decision();

-- Giu cung quy tac isolation voi bo SQL nen.
DO $$
DECLARE v_table text;
BEGIN
    FOREACH v_table IN ARRAY ARRAY['group_invitations','user_settings','password_reset_requests','moderation_actions'] LOOP
        EXECUTE format('CREATE OR REPLACE TRIGGER write_isolation BEFORE INSERT OR UPDATE OR DELETE ON chat.%I '
            'FOR EACH STATEMENT EXECUTE FUNCTION chat.guard_write_isolation()',v_table);
    END LOOP;
END $$;

-- Tai khoan bi khoa/xoa thi thu hoi reset; xoa mem thi huy cac loi moi dang cho.
CREATE OR REPLACE FUNCTION chat.sync_inactive_account() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status AND NEW.status<>'ACTIVE' THEN
        UPDATE chat.password_reset_requests SET revoked_at=clock_timestamp()
        WHERE user_id=NEW.user_id AND consumed_at IS NULL AND revoked_at IS NULL;
        IF NEW.status='DELETED' THEN
            UPDATE chat.friend_requests SET status='CANCELLED'
            WHERE status='PENDING' AND NEW.user_id IN(sender_id,receiver_id);
            UPDATE chat.group_invitations SET status='CANCELLED'
            WHERE status='PENDING' AND NEW.user_id IN(inviter_id,invitee_id);
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER users_sync_inactive AFTER UPDATE OF status ON chat.users
FOR EACH ROW EXECUTE FUNCTION chat.sync_inactive_account();

-- Them thanh vien bang API admin cu thi loi moi cho nguoi do khong con can thiet.
-- Huy loi moi cu, khong gia lap viec nguoi nhan da bam chap nhan.
CREATE OR REPLACE FUNCTION chat.sync_member_invitations() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF NEW.left_at IS NULL THEN
        UPDATE chat.group_invitations SET status='CANCELLED'
        WHERE conversation_id=NEW.conversation_id AND invitee_id=NEW.user_id AND status='PENDING';
    END IF;
    IF NEW.left_at IS NOT NULL OR NEW.group_role<>'ADMIN' THEN
        UPDATE chat.group_invitations SET status='CANCELLED'
        WHERE conversation_id=NEW.conversation_id AND inviter_id=NEW.user_id AND status='PENDING';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER members_sync_invitations AFTER INSERT OR UPDATE OF left_at,group_role ON chat.conversation_members
FOR EACH ROW EXECUTE FUNCTION chat.sync_member_invitations();


-- SOURCE: 13_sync_functions.sql
-- Ham SECURITY INVOKER: chi server tin cay duoc ket noi DB.
-- actor_id lay tu session server, khong lay truc tiep tu du lieu client gui len.

CREATE OR REPLACE FUNCTION chat.save_user_settings(p_actor_id bigint,p_notifications boolean,p_sound boolean,p_previews boolean) RETURNS void
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    INSERT INTO chat.user_settings(user_id,desktop_notifications,sound_enabled,message_previews)
    VALUES(p_actor_id,p_notifications,p_sound,p_previews)
    ON CONFLICT (user_id) DO UPDATE SET desktop_notifications=EXCLUDED.desktop_notifications,
        sound_enabled=EXCLUDED.sound_enabled,message_previews=EXCLUDED.message_previews;
END $$;

CREATE OR REPLACE FUNCTION chat.create_group_invitation(p_actor_id bigint,p_group_id bigint,p_invitee_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.group_invitations(conversation_id,inviter_id,invitee_id)
    VALUES(p_group_id,p_actor_id,p_invitee_id) RETURNING invitation_id INTO v_id;
    RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION chat.respond_group_invitation(p_actor_id bigint,p_invitation_id bigint,p_status chat.friend_request_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_invitation chat.group_invitations%ROWTYPE;
BEGIN
    SELECT * INTO v_invitation FROM chat.group_invitations WHERE invitation_id=p_invitation_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Khong tim thay loi moi' USING ERRCODE='23514'; END IF;
    -- Thu tu khoa: hoi thoai truoc, loi moi sau.
    PERFORM 1 FROM chat.conversations WHERE conversation_id=v_invitation.conversation_id FOR UPDATE;
    SELECT * INTO v_invitation FROM chat.group_invitations WHERE invitation_id=p_invitation_id FOR UPDATE;
    PERFORM chat.require_active_user(p_actor_id);
    IF v_invitation.status <> 'PENDING' THEN RAISE EXCEPTION 'Loi moi da duoc xu ly' USING ERRCODE='23514'; END IF;
    IF p_status IS NULL THEN RAISE EXCEPTION 'Can chon trang thai' USING ERRCODE='23514'; END IF;
    IF p_actor_id=v_invitation.invitee_id AND p_status IN ('ACCEPTED','REJECTED') THEN
        NULL;
    ELSIF p_status='CANCELLED' THEN
        IF p_actor_id<>v_invitation.inviter_id THEN
            PERFORM chat.require_group_admin(p_actor_id,v_invitation.conversation_id);
        END IF;
    ELSE
        RAISE EXCEPTION 'Ban khong co quyen thuc hien thao tac nay' USING ERRCODE='42501';
    END IF;
    UPDATE chat.group_invitations SET status=p_status WHERE invitation_id=p_invitation_id;
END $$;

-- Reset luon khoa user truoc reset row de tranh khoa nang cap/dao thu tu.
CREATE OR REPLACE FUNCTION chat.lock_reset_account(p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_status chat.user_status;
BEGIN
    SELECT status INTO v_status FROM chat.users WHERE user_id=p_user_id FOR UPDATE;
    IF NOT FOUND OR v_status<>'ACTIVE' THEN RAISE EXCEPTION 'Tai khoan khong hoat dong' USING ERRCODE='23514'; END IF;
END $$;

CREATE OR REPLACE FUNCTION chat.create_password_reset(p_user_id bigint,p_token_hash bytea,
    p_ttl interval DEFAULT interval '15 minutes',p_attempt_limit integer DEFAULT 5) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_id bigint; v_now timestamptz;
BEGIN
    IF p_ttl IS NULL OR p_ttl<=interval '0 seconds' OR p_ttl>interval '1 day'
        OR p_attempt_limit IS NULL OR p_attempt_limit NOT BETWEEN 1 AND 20 THEN
        RAISE EXCEPTION 'Han dung hoac gioi han thu khong hop le' USING ERRCODE='23514';
    END IF;
    PERFORM chat.lock_reset_account(p_user_id);
    v_now:=clock_timestamp();
    UPDATE chat.password_reset_requests SET revoked_at=v_now
    WHERE user_id=p_user_id AND consumed_at IS NULL AND revoked_at IS NULL;
    INSERT INTO chat.password_reset_requests(user_id,token_hash,created_at,expires_at,attempt_limit)
    VALUES(p_user_id,p_token_hash,v_now,v_now+p_ttl,p_attempt_limit::smallint) RETURNING reset_id INTO v_id;
    RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION chat.verify_password_reset(p_reset_id bigint,p_candidate_hash bytea) RETURNS boolean
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_reset chat.password_reset_requests%ROWTYPE; v_user bigint; v_now timestamptz;
BEGIN
    SELECT user_id INTO v_user FROM chat.password_reset_requests WHERE reset_id=p_reset_id;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM chat.lock_reset_account(v_user);
    SELECT * INTO v_reset FROM chat.password_reset_requests WHERE reset_id=p_reset_id FOR UPDATE;
    v_now:=clock_timestamp();
    IF v_reset.consumed_at IS NOT NULL OR v_reset.revoked_at IS NOT NULL
        OR v_reset.expires_at<=v_now OR v_reset.failed_attempts>=v_reset.attempt_limit THEN RETURN false; END IF;
    IF p_candidate_hash IS NULL OR p_candidate_hash<>v_reset.token_hash THEN
        UPDATE chat.password_reset_requests SET failed_attempts=failed_attempts+1,
            revoked_at=CASE WHEN failed_attempts+1>=attempt_limit THEN v_now ELSE NULL END
        WHERE reset_id=p_reset_id;
        -- Tra false thay vi RAISE: can COMMIT de giu bo dem nhap sai.
        RETURN false;
    END IF;
    IF v_reset.verified_at IS NULL THEN
        UPDATE chat.password_reset_requests SET verified_at=v_now WHERE reset_id=p_reset_id;
    END IF;
    RETURN true;
END $$;

CREATE OR REPLACE FUNCTION chat.complete_password_reset(p_reset_id bigint,p_candidate_hash bytea,p_new_password_hash text) RETURNS boolean
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_reset chat.password_reset_requests%ROWTYPE; v_user bigint; v_now timestamptz;
BEGIN
    SELECT user_id INTO v_user FROM chat.password_reset_requests WHERE reset_id=p_reset_id;
    IF NOT FOUND THEN RETURN false; END IF;
    PERFORM chat.lock_reset_account(v_user);
    SELECT * INTO v_reset FROM chat.password_reset_requests WHERE reset_id=p_reset_id FOR UPDATE;
    v_now:=clock_timestamp();
    IF v_reset.verified_at IS NULL OR v_reset.consumed_at IS NOT NULL OR v_reset.revoked_at IS NOT NULL
        OR v_reset.expires_at<=v_now OR v_reset.failed_attempts>=v_reset.attempt_limit THEN RETURN false; END IF;
    IF p_candidate_hash IS NULL OR p_candidate_hash<>v_reset.token_hash THEN
        PERFORM chat.verify_password_reset(p_reset_id,p_candidate_hash);
        RETURN false;
    END IF;
    -- Day chi la kiem tra hinh dang. Backend phai bam bang thu vien thuc su.
    IF p_new_password_hash IS NULL OR length(p_new_password_hash)<20
        OR p_new_password_hash !~ '^[$](argon2id|2[aby])[$]' THEN
        RAISE EXCEPTION 'Can password hash Argon2id/bcrypt do backend tao' USING ERRCODE='23514';
    END IF;
    UPDATE chat.users SET password_hash=p_new_password_hash WHERE user_id=v_user;
    UPDATE chat.password_reset_requests SET consumed_at=v_now WHERE reset_id=p_reset_id;
    RETURN true;
END $$;

CREATE OR REPLACE FUNCTION chat.moderate_report(p_actor_id bigint,p_report_id bigint,
    p_action chat.moderation_action_type,p_notes text) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.moderation_actions(report_id,admin_id,action_type,notes)
    VALUES(p_report_id,p_actor_id,p_action,p_notes) RETURNING action_id INTO v_id;
    RETURN v_id;
END $$;

-- Giu API cu, nhung nay cung ghi nguoi xu ly va quyet dinh.
CREATE OR REPLACE FUNCTION chat.review_spam_report(p_actor_id bigint,p_report_id bigint,p_status chat.report_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat,pg_temp AS $$
BEGIN
    IF p_status IS NULL OR p_status NOT IN ('REVIEWED','DISMISSED') THEN
        RAISE EXCEPTION 'Chon REVIEWED hoac DISMISSED' USING ERRCODE='23514';
    END IF;
    PERFORM chat.moderate_report(p_actor_id,p_report_id,
        CASE WHEN p_status='DISMISSED' THEN 'DISMISS'::chat.moderation_action_type ELSE 'REVIEW'::chat.moderation_action_type END,
        'Xu ly qua API review_spam_report cu');
END $$;


-- SOURCE: 14_views.sql
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


-- SOURCE: 15_sync_data_permissions.sql
-- Bo sung du lieu con thieu, giu nguyen nhung gia tri da luu.
INSERT INTO chat.user_settings(user_id) SELECT user_id FROM chat.users ON CONFLICT DO NOTHING;

-- Dong yeu cau reset da het han/het luot; khong sua token da dung hoac da thu hoi.
UPDATE chat.password_reset_requests SET revoked_at=clock_timestamp()
WHERE consumed_at IS NULL AND revoked_at IS NULL
    AND (expires_at<=clock_timestamp() OR failed_attempts>=attempt_limit);

-- Du lieu da luu truoc day phai phu hop; khong am tham xoa/sua nham lich su.
DO $$
DECLARE v_bad bigint;
BEGIN
    SELECT i.invitation_id INTO v_bad FROM chat.group_invitations i
    JOIN chat.conversations c USING(conversation_id) WHERE c.type<>'GROUP' LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Loi moi % dang tro vao DIRECT. Can kiem tra lai du lieu; migration se rollback.',v_bad;
    END IF;
    SELECT a.action_id INTO v_bad FROM chat.moderation_actions a JOIN chat.spam_reports r USING(report_id)
    WHERE r.status<>'PENDING' AND r.status<>
        CASE WHEN a.action_type='DISMISS' THEN 'DISMISSED'::chat.report_status ELSE 'REVIEWED'::chat.report_status END
    LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Quyet dinh % khong khop trang thai bao cao da luu; can kiem tra du lieu.',v_bad;
    END IF;
    SELECT a.action_id INTO v_bad FROM chat.moderation_actions a
    JOIN chat.spam_reports r USING(report_id) JOIN chat.users u ON u.user_id=a.admin_id
    WHERE r.status='PENDING' AND (u.system_role<>'ADMIN' OR u.status<>'ACTIVE') LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Quyet dinh % chua ap dung nhung admin_id khong co quyen hoat dong; migration rollback.',v_bad;
    END IF;
END $$;

-- Phuc hoi membership neu da ghi ACCEPTED nhung code cu chua them nguoi vao nhom.
-- Khong cho vao lai neu nguoi dung da roi nhom SAU lan chap nhan do.
DO $$
DECLARE v record; v_left timestamptz; v_exists boolean; v_status chat.user_status;
BEGIN
    FOR v IN SELECT DISTINCT ON (conversation_id,invitee_id)
        conversation_id,invitee_id,responded_at,invitation_id
        FROM chat.group_invitations WHERE status='ACCEPTED'
        ORDER BY conversation_id,invitee_id,responded_at DESC,invitation_id DESC LOOP
        PERFORM 1 FROM chat.conversations WHERE conversation_id=v.conversation_id FOR UPDATE;
        SELECT left_at INTO v_left FROM chat.conversation_members
        WHERE conversation_id=v.conversation_id AND user_id=v.invitee_id;
        v_exists:=FOUND;
        IF NOT v_exists OR (v_left IS NOT NULL AND v.responded_at>v_left) THEN
            SELECT status INTO v_status FROM chat.users WHERE user_id=v.invitee_id FOR SHARE;
            IF v_status<>'ACTIVE' THEN
                RAISE EXCEPTION 'Loi moi ACCEPTED % thieu membership nhung tai khoan khong ACTIVE; can kiem tra thu cong.',v.invitation_id;
            END IF;
            IF v_exists THEN
                UPDATE chat.conversation_members SET left_at=NULL,group_role='MEMBER'
                WHERE conversation_id=v.conversation_id AND user_id=v.invitee_id;
            ELSE
                INSERT INTO chat.conversation_members(conversation_id,user_id,group_role,joined_at)
                VALUES(v.conversation_id,v.invitee_id,'MEMBER',v.responded_at);
            END IF;
        END IF;
    END LOOP;
END $$;

-- Ap dung quyet dinh da ghi nhung bao cao van PENDING (ban chi CREATE TABLE cu).
-- Bao cao da xu ly duoc giu nguyen; khong khoa lai user da duoc mo khoa sau do.
DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT a.*,r.reported_user_id FROM chat.moderation_actions a
        JOIN chat.spam_reports r USING(report_id) WHERE r.status='PENDING' ORDER BY a.report_id LOOP
        PERFORM 1 FROM chat.spam_reports WHERE report_id=v.report_id FOR UPDATE;
        IF v.action_type='SUSPEND' THEN
            UPDATE chat.users SET status='LOCKED' WHERE user_id=v.reported_user_id AND status<>'DELETED';
            IF NOT FOUND THEN RAISE EXCEPTION 'Quyet dinh % khoa tai khoan da xoa; can kiem tra du lieu.',v.action_id; END IF;
        END IF;
        UPDATE chat.spam_reports SET status=CASE WHEN v.action_type='DISMISS' THEN 'DISMISSED'::chat.report_status ELSE 'REVIEWED'::chat.report_status END
        WHERE report_id=v.report_id;
    END LOOP;
END $$;

-- Neu user da bi xoa/khoa truoc migration, khong giu ma reset con hieu luc.
UPDATE chat.password_reset_requests p SET revoked_at=clock_timestamp()
FROM chat.users u WHERE u.user_id=p.user_id AND u.status<>'ACTIVE'
    AND p.consumed_at IS NULL AND p.revoked_at IS NULL;
UPDATE chat.group_invitations i SET status='CANCELLED'
WHERE i.status='PENDING' AND EXISTS(SELECT 1 FROM chat.users u
    WHERE u.user_id IN(i.inviter_id,i.invitee_id) AND u.status='DELETED');

-- Khong tao quyet dinh gia cho nhung report da xu ly tu truoc ma thieu admin/notes.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA chat FROM PUBLIC;
REVOKE ALL ON chat.group_invitations,chat.user_settings,chat.password_reset_requests,chat.moderation_actions FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='relay_backend') THEN
        GRANT USAGE ON SCHEMA chat TO relay_backend;
        GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA chat TO relay_backend;
        GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA chat TO relay_backend;
        GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA chat TO relay_backend;
    END IF;
END $$;

COMMIT;
SELECT count(*) AS total_chat_tables FROM information_schema.tables WHERE table_schema='chat' AND table_type='BASE TABLE';
