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
