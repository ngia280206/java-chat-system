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
