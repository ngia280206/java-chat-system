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
