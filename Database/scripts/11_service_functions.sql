-- Backend use-case helpers. p_actor_id MUST come from an authenticated server
-- session, never from an untrusted client request. All functions are invoker-rights.

CREATE FUNCTION chat.create_direct_conversation(p_actor_id bigint, p_other_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    IF p_actor_id IS NULL OR p_other_id IS NULL OR p_actor_id = p_other_id THEN
        RAISE EXCEPTION 'Choose two different accounts' USING ERRCODE = '23514';
    END IF;
    PERFORM chat.lock_user_pair(p_actor_id, p_other_id);
    PERFORM chat.require_active_user(LEAST(p_actor_id, p_other_id));
    PERFORM chat.require_active_user(GREATEST(p_actor_id, p_other_id));
    IF chat.pair_is_blocked(p_actor_id, p_other_id) THEN
        RAISE EXCEPTION 'This pair is blocked' USING ERRCODE = '23514';
    END IF;
    SELECT c.conversation_id INTO v_id FROM chat.conversations c
    JOIN chat.conversation_members a ON a.conversation_id = c.conversation_id AND a.user_id = p_actor_id
    JOIN chat.conversation_members b ON b.conversation_id = c.conversation_id AND b.user_id = p_other_id
    WHERE c.type = 'DIRECT';
    IF FOUND THEN RETURN v_id; END IF;
    INSERT INTO chat.conversations(type, created_by) VALUES ('DIRECT', p_actor_id) RETURNING conversation_id INTO v_id;
    INSERT INTO chat.conversation_members(conversation_id, user_id)
    VALUES (v_id, p_actor_id), (v_id, p_other_id);
    RETURN v_id;
END $$;

CREATE FUNCTION chat.create_group(p_actor_id bigint, p_name text, p_member_ids bigint[],
    p_encryption_mode chat.encryption_mode DEFAULT 'NONE') RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint; v_members bigint[]; v_member bigint;
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    SELECT array_agg(DISTINCT id ORDER BY id) INTO v_members
    FROM unnest(COALESCE(p_member_ids, ARRAY[]::bigint[]) || p_actor_id) id WHERE id IS NOT NULL;
    IF cardinality(v_members) < 2 THEN
        RAISE EXCEPTION 'A new group needs the creator and another member' USING ERRCODE = '23514';
    END IF;
    INSERT INTO chat.conversations(type, name, created_by, encryption_mode)
    VALUES ('GROUP', btrim(p_name), p_actor_id, p_encryption_mode) RETURNING conversation_id INTO v_id;
    FOREACH v_member IN ARRAY v_members LOOP
        INSERT INTO chat.conversation_members(conversation_id, user_id, group_role)
        VALUES (v_id, v_member, CASE WHEN v_member = p_actor_id THEN 'ADMIN'::chat.group_role ELSE 'MEMBER'::chat.group_role END);
    END LOOP;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.require_group_admin(p_actor_id bigint, p_group_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_group_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Group does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.require_active_user(p_actor_id);
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = p_group_id
        AND user_id = p_actor_id AND group_role = 'ADMIN' AND left_at IS NULL) THEN
        RAISE EXCEPTION 'Only a current group admin can perform this action' USING ERRCODE = '42501';
    END IF;
END $$;

CREATE FUNCTION chat.add_group_member(p_actor_id bigint, p_group_id bigint, p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    -- Invitation acceptance, if required, must first be verified by the backend;
    -- the supplied ERD has no persistent group_invitations table.
    INSERT INTO chat.conversation_members(conversation_id, user_id)
    VALUES (p_group_id, p_user_id)
    ON CONFLICT (conversation_id, user_id) DO UPDATE SET left_at = NULL, group_role = 'MEMBER'
    WHERE chat.conversation_members.left_at IS NOT NULL;
END $$;

CREATE FUNCTION chat.remove_group_member(p_actor_id bigint, p_group_id bigint, p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    IF p_actor_id = p_user_id THEN RAISE EXCEPTION 'Use leave_group for yourself' USING ERRCODE = '23514'; END IF;
    UPDATE chat.conversation_members SET left_at = clock_timestamp()
    WHERE conversation_id = p_group_id AND user_id = p_user_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Target is not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.change_group_role(p_actor_id bigint, p_group_id bigint, p_user_id bigint, p_role chat.group_role) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_group_admin(p_actor_id, p_group_id);
    UPDATE chat.conversation_members SET group_role = p_role
    WHERE conversation_id = p_group_id AND user_id = p_user_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Target is not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.leave_group(p_actor_id bigint, p_group_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_group_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Group does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.require_active_user(p_actor_id);
    UPDATE chat.conversation_members SET left_at = clock_timestamp()
    WHERE conversation_id = p_group_id AND user_id = p_actor_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'You are not a current member' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.send_text_message(p_actor_id bigint, p_conversation_id bigint, p_content text) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.messages(conversation_id, sender_id, content)
    VALUES (p_conversation_id, p_actor_id, btrim(p_content)) RETURNING message_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.send_encrypted_message(p_actor_id bigint, p_conversation_id bigint,
    p_ciphertext bytea, p_nonce bytea, p_key_version integer) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.messages(conversation_id, sender_id, ciphertext, nonce, key_version)
    VALUES (p_conversation_id, p_actor_id, p_ciphertext, p_nonce, p_key_version) RETURNING message_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.hide_message(p_actor_id bigint, p_message_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.message_hidden_for_users(user_id, message_id)
    VALUES (p_actor_id, p_message_id) ON CONFLICT DO NOTHING;
END $$;

CREATE FUNCTION chat.clear_history(p_actor_id bigint, p_conversation_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_boundary bigint;
BEGIN
    PERFORM 1 FROM chat.conversations WHERE conversation_id = p_conversation_id FOR UPDATE;
    PERFORM chat.require_active_user(p_actor_id);
    SELECT COALESCE(max(message_seq), 0) INTO v_boundary FROM chat.messages WHERE conversation_id = p_conversation_id;
    UPDATE chat.conversation_members SET history_cleared_through_seq = v_boundary
    WHERE conversation_id = p_conversation_id AND user_id = p_actor_id AND left_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'Current membership is required' USING ERRCODE = '23514'; END IF;
    RETURN v_boundary;
END $$;

CREATE FUNCTION chat.get_visible_messages(p_actor_id bigint, p_conversation_id bigint,
    p_limit integer DEFAULT 50, p_before_seq bigint DEFAULT NULL) RETURNS SETOF chat.messages
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'Page size must be 1..200' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY SELECT m.* FROM chat.messages m
    JOIN chat.conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = p_actor_id
    WHERE m.conversation_id = p_conversation_id AND cm.left_at IS NULL
        AND m.message_seq > cm.history_cleared_through_seq
        AND (p_before_seq IS NULL OR m.message_seq < p_before_seq)
        AND NOT EXISTS (SELECT 1 FROM chat.message_hidden_for_users h WHERE h.user_id = p_actor_id AND h.message_id = m.message_id)
    ORDER BY m.message_seq DESC LIMIT p_limit;
END $$;

CREATE FUNCTION chat.search_visible_messages(p_actor_id bigint, p_conversation_id bigint,
    p_query text, p_limit integer DEFAULT 50) RETURNS SETOF chat.messages
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF p_query IS NULL OR length(btrim(p_query)) = 0 OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION 'Nonempty search text and limit 1..200 are required' USING ERRCODE = '23514';
    END IF;
    -- Literal substring search: % and _ do not act as wildcard characters.
    -- Encrypted content is deliberately not searchable on the server.
    RETURN QUERY SELECT m.* FROM chat.messages m
    JOIN chat.conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = p_actor_id
    WHERE m.conversation_id = p_conversation_id AND cm.left_at IS NULL
        AND m.message_seq > cm.history_cleared_through_seq AND m.content IS NOT NULL
        AND strpos(lower(m.content), lower(btrim(p_query))) > 0
        AND NOT EXISTS (SELECT 1 FROM chat.message_hidden_for_users h WHERE h.user_id = p_actor_id AND h.message_id = m.message_id)
    ORDER BY m.message_seq DESC LIMIT p_limit;
END $$;

CREATE FUNCTION chat.send_friend_request(p_actor_id bigint, p_receiver_id bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.friend_requests(sender_id, receiver_id) VALUES (p_actor_id, p_receiver_id)
    RETURNING request_id INTO v_id;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.respond_friend_request(p_actor_id bigint, p_request_id bigint,
    p_status chat.friend_request_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_request chat.friend_requests%ROWTYPE;
BEGIN
    SELECT * INTO v_request FROM chat.friend_requests WHERE request_id = p_request_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Request does not exist' USING ERRCODE = '23514'; END IF;
    PERFORM chat.lock_user_pair(v_request.sender_id, v_request.receiver_id);
    SELECT * INTO v_request FROM chat.friend_requests WHERE request_id = p_request_id FOR UPDATE;
    PERFORM chat.require_active_user(p_actor_id);
    IF v_request.status <> 'PENDING' OR p_status IS NULL OR NOT (
        (p_actor_id = v_request.receiver_id AND p_status IN ('ACCEPTED', 'REJECTED')) OR
        (p_actor_id = v_request.sender_id AND p_status = 'CANCELLED')) THEN
        RAISE EXCEPTION 'This account cannot perform the requested transition' USING ERRCODE = '42501';
    END IF;
    UPDATE chat.friend_requests SET status = p_status WHERE request_id = p_request_id;
END $$;

CREATE FUNCTION chat.block_user(p_actor_id bigint, p_target_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.user_blocks(blocker_id, blocked_id) VALUES (p_actor_id, p_target_id) ON CONFLICT DO NOTHING;
END $$;

CREATE FUNCTION chat.unblock_user(p_actor_id bigint, p_target_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.lock_user_pair(p_actor_id, p_target_id);
    PERFORM chat.require_active_user(p_actor_id);
    DELETE FROM chat.user_blocks WHERE blocker_id = p_actor_id AND blocked_id = p_target_id;
END $$;

CREATE FUNCTION chat.submit_spam_report(p_actor_id bigint, p_message_id bigint, p_reason text) RETURNS bigint
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO chat.spam_reports(reporter_id, reported_user_id, message_id, reason)
    SELECT p_actor_id, sender_id, message_id, btrim(p_reason) FROM chat.messages WHERE message_id = p_message_id
    RETURNING report_id INTO v_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Message does not exist' USING ERRCODE = '23514'; END IF;
    RETURN v_id;
END $$;

CREATE FUNCTION chat.require_system_admin(p_actor_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_active_user(p_actor_id);
    IF NOT EXISTS (SELECT 1 FROM chat.users WHERE user_id = p_actor_id AND system_role = 'ADMIN') THEN
        RAISE EXCEPTION 'System administrator permission is required' USING ERRCODE = '42501';
    END IF;
END $$;

CREATE FUNCTION chat.review_spam_report(p_actor_id bigint, p_report_id bigint, p_status chat.report_status) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    PERFORM chat.require_system_admin(p_actor_id);
    IF p_status IS NULL OR p_status NOT IN ('REVIEWED', 'DISMISSED') THEN
        RAISE EXCEPTION 'Choose REVIEWED or DISMISSED' USING ERRCODE = '23514';
    END IF;
    UPDATE chat.spam_reports SET status = p_status WHERE report_id = p_report_id AND status = 'PENDING';
    IF NOT FOUND THEN RAISE EXCEPTION 'Report is missing or already resolved' USING ERRCODE = '23514'; END IF;
END $$;

CREATE FUNCTION chat.set_account_locked(p_actor_id bigint, p_target_id bigint, p_locked boolean) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF p_locked IS NULL OR p_actor_id = p_target_id THEN
        RAISE EXCEPTION 'Choose another account and an explicit lock state' USING ERRCODE = '23514';
    END IF;
    PERFORM chat.require_system_admin(p_actor_id);
    UPDATE chat.users SET status = CASE WHEN p_locked THEN 'LOCKED'::chat.user_status ELSE 'ACTIVE'::chat.user_status END
    WHERE user_id = p_target_id AND status <> 'DELETED';
    IF NOT FOUND THEN RAISE EXCEPTION 'Account is missing or deleted' USING ERRCODE = '23514'; END IF;
END $$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA chat FROM PUBLIC;
