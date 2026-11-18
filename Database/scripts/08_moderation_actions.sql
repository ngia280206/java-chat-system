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

