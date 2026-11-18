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
