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

