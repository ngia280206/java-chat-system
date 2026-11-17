-- UPDATE_EXISTING_DATABASE: chay tren database da co bo nen 14 bang.
-- Ho tro 14 bang hoac da them 4 bang (18 bang). Khong DROP, khong xoa du lieu.
BEGIN;
-- Chi dung cho database da cai bo nen trong sqlclone.rar.
SELECT pg_advisory_xact_lock(hashtextextended('relay.sync18.install',0));
DO $$
DECLARE v_name text;
BEGIN
    IF current_setting('server_version_num')::integer<160000 THEN
        RAISE EXCEPTION 'Can PostgreSQL 16 tro len';
    END IF;
    IF current_setting('transaction_isolation')<>'read committed' THEN
        RAISE EXCEPTION 'Chay cap nhat bang READ COMMITTED' USING ERRCODE='25000';
    END IF;
    FOREACH v_name IN ARRAY ARRAY['users','friend_requests','friendships','user_blocks',
        'conversations','conversation_members','user_devices','group_key_versions','group_key_envelopes',
        'messages','message_hidden_for_users','login_history','activity_logs','spam_reports'] LOOP
        IF to_regclass('chat.'||v_name) IS NULL THEN
            RAISE EXCEPTION 'Thieu chat.%. File cap nhat can bo nen 14 bang; database trong hay dung INSTALL_NEW_DATABASE.sql.',v_name;
        END IF;
    END LOOP;
    FOREACH v_name IN ARRAY ARRAY['require_active_user(bigint)','require_group_admin(bigint,bigint)',
        'require_system_admin(bigint)','guard_write_isolation()','deny_history_mutation()',
        'review_spam_report(bigint,bigint,chat.report_status)'] LOOP
        IF to_regprocedure('chat.'||v_name) IS NULL THEN
            RAISE EXCEPTION 'Thieu ham chat.%. Hay cai day du cac ham va trigger nen cua sqlclone.rar.',v_name;
        END IF;
    END LOOP;
END $$;
-- Chay khi backend da dung; khoa ngan du lieu doi trong luc dong bo.
LOCK TABLE chat.users,chat.conversations,chat.conversation_members,chat.spam_reports
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE v_values text[];
BEGIN
    IF to_regtype('chat.moderation_action_type') IS NULL THEN
        CREATE TYPE chat.moderation_action_type AS ENUM('REVIEW','DISMISS','WARN','SUSPEND');
    ELSE
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder) INTO v_values
        FROM pg_enum WHERE enumtypid=to_regtype('chat.moderation_action_type');
        IF v_values IS DISTINCT FROM ARRAY['REVIEW','DISMISS','WARN','SUSPEND']::text[] THEN
            RAISE EXCEPTION 'ENUM moderation_action_type khac bo SQL nay; dung cap nhat de tranh sua nham.';
        END IF;
    END IF;
END $$;


-- SOURCE: 05_group_invitations.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.

-- Giao dien co moi vao nhom, chap nhan va tu choi; ERD cu chua luu loi moi.
-- Dung lai ENUM cua friend_requests vi cung 4 gia tri trang thai.
CREATE TABLE IF NOT EXISTS chat.group_invitations (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_group_invitations_pending ON chat.group_invitations(conversation_id,invitee_id)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_group_invitations_invitee ON chat.group_invitations(invitee_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_group_invitations_inviter ON chat.group_invitations(inviter_id,created_at DESC);
COMMENT ON TABLE chat.group_invitations IS 'Luu loi moi vao nhom. Trigger dong bo membership khi chap nhan.';



-- SOURCE: 06_user_settings.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.

-- Giao dien co cac tuy chon thong bao, am thanh va xem truoc tin nhan.
CREATE TABLE IF NOT EXISTS chat.user_settings (
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
CREATE TABLE IF NOT EXISTS chat.password_reset_requests (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_reset_open_user ON chat.password_reset_requests(user_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_reset_user_history ON chat.password_reset_requests(user_id,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reset_expiry ON chat.password_reset_requests(expires_at)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
COMMENT ON COLUMN chat.password_reset_requests.token_hash IS
    'Hash/HMAC do backend tao; khong luu token hay OTP goc. Voi OTP ngan, can bien phap bao ve phu hop va gioi han thu.';



-- SOURCE: 08_moderation_actions.sql
-- Dinh nghia bang; thu tu chay duoc quan ly boi 00_install.sql.
-- ENUM moi duoc tao cung bang trong file nay.

-- Giao dien admin co ghi chu, canh cao va khoa tai khoan; status report chua du.
CREATE TABLE IF NOT EXISTS chat.moderation_actions (
    action_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id   bigint NOT NULL UNIQUE REFERENCES chat.spam_reports(report_id) ON DELETE RESTRICT,
    admin_id    bigint NOT NULL REFERENCES chat.users(user_id) ON DELETE RESTRICT,
    action_type chat.moderation_action_type NOT NULL,
    notes       text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_moderation_notes CHECK (length(btrim(notes)) > 0)
);
CREATE INDEX IF NOT EXISTS ix_moderation_admin_time ON chat.moderation_actions(admin_id,created_at DESC);
COMMENT ON TABLE chat.moderation_actions IS
    'Luu admin, hanh dong va ghi chu xu ly; moi report co toi da mot quyet dinh.';


-- IF NOT EXISTS khong tu kiem tra cau truc; doan nay kiem tra ro rang truoc khi tao ham.
DO $$
DECLARE e record; v_type oid; v_notnull boolean; v_pk text[];
BEGIN
    FOR e IN SELECT * FROM (VALUES
        ('group_invitations','invitation_id','bigint',true),
        ('group_invitations','conversation_id','bigint',true),
        ('group_invitations','inviter_id','bigint',true),
        ('group_invitations','invitee_id','bigint',true),
        ('group_invitations','status','chat.friend_request_status',true),
        ('group_invitations','created_at','timestamptz',true),
        ('group_invitations','responded_at','timestamptz',false),
        ('user_settings','user_id','bigint',true),
        ('user_settings','desktop_notifications','boolean',true),
        ('user_settings','sound_enabled','boolean',true),
        ('user_settings','message_previews','boolean',true),
        ('user_settings','updated_at','timestamptz',true),
        ('password_reset_requests','reset_id','bigint',true),
        ('password_reset_requests','user_id','bigint',true),
        ('password_reset_requests','token_hash','bytea',true),
        ('password_reset_requests','created_at','timestamptz',true),
        ('password_reset_requests','expires_at','timestamptz',true),
        ('password_reset_requests','verified_at','timestamptz',false),
        ('password_reset_requests','consumed_at','timestamptz',false),
        ('password_reset_requests','revoked_at','timestamptz',false),
        ('password_reset_requests','failed_attempts','integer',true),
        ('password_reset_requests','attempt_limit','smallint',true),
        ('moderation_actions','action_id','bigint',true),
        ('moderation_actions','report_id','bigint',true),
        ('moderation_actions','admin_id','bigint',true),
        ('moderation_actions','action_type','chat.moderation_action_type',true),
        ('moderation_actions','notes','text',true),
        ('moderation_actions','created_at','timestamptz',true)
    ) AS expected(tbl,col,typ,nn) LOOP
        SELECT atttypid,attnotnull INTO v_type,v_notnull FROM pg_attribute
        WHERE attrelid=to_regclass('chat.'||e.tbl) AND attname=e.col AND attnum>0 AND NOT attisdropped;
        IF NOT FOUND OR v_type IS DISTINCT FROM to_regtype(e.typ)::oid OR v_notnull IS DISTINCT FROM e.nn THEN
            RAISE EXCEPTION 'Cau truc chat.%.% khong dung: can % / NOT NULL=%. Cap nhat da rollback.',e.tbl,e.col,e.typ,e.nn;
        END IF;
    END LOOP;
    FOR e IN SELECT * FROM (VALUES('group_invitations','invitation_id'),('user_settings','user_id'),
        ('password_reset_requests','reset_id'),('moderation_actions','action_id')) AS expected(tbl,col) LOOP
        SELECT array_agg(a.attname::text ORDER BY k.ord) INTO v_pk
        FROM pg_constraint p CROSS JOIN LATERAL unnest(p.conkey) WITH ORDINALITY k(num,ord)
        JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=k.num
        WHERE p.conrelid=to_regclass('chat.'||e.tbl) AND p.contype='p';
        IF v_pk IS DISTINCT FROM ARRAY[e.col]::text[] THEN
            RAISE EXCEPTION 'Khoa chinh chat.% khong dung cau truc du kien.',e.tbl;
        END IF;
    END LOOP;
    FOR e IN SELECT * FROM (VALUES
        ('group_invitations','conversation_id','conversations','conversation_id'),
        ('group_invitations','inviter_id','users','user_id'),('group_invitations','invitee_id','users','user_id'),
        ('user_settings','user_id','users','user_id'),('password_reset_requests','user_id','users','user_id'),
        ('moderation_actions','report_id','spam_reports','report_id'),('moderation_actions','admin_id','users','user_id')
    ) AS expected(tbl,col,parent,parent_col) LOOP
        IF NOT EXISTS(SELECT 1 FROM pg_constraint k
            JOIN pg_attribute a ON a.attrelid=k.conrelid AND k.conkey=ARRAY[a.attnum]::smallint[]
            JOIN pg_attribute b ON b.attrelid=k.confrelid AND k.confkey=ARRAY[b.attnum]::smallint[]
            WHERE k.contype='f' AND k.conrelid=to_regclass('chat.'||e.tbl) AND a.attname=e.col
                AND k.confrelid=to_regclass('chat.'||e.parent) AND b.attname=e.parent_col AND k.convalidated) THEN
            RAISE EXCEPTION 'Thieu FK hop le: chat.%.% -> %.%',e.tbl,e.col,e.parent,e.parent_col;
        END IF;
    END LOOP;
END $$;


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
