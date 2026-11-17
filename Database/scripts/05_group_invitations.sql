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

