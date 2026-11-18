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
