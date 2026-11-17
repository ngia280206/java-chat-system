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
