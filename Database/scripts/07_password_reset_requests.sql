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

