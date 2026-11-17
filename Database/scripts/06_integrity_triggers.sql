-- These are integrity checks, not an authentication boundary. Functions are
-- SECURITY INVOKER. Only a trusted backend should hold database credentials.

CREATE FUNCTION chat.require_active_user(p_user_id bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_status chat.user_status;
BEGIN
    SELECT status INTO v_status FROM chat.users WHERE user_id = p_user_id FOR SHARE;
    IF NOT FOUND OR v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Account % is missing or inactive', p_user_id USING ERRCODE = '23514';
    END IF;
END $$;

CREATE FUNCTION chat.lock_user_pair(p_first bigint, p_second bigint) RETURNS void
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    -- A collision only serializes unrelated pairs; it cannot corrupt data.
    PERFORM pg_advisory_xact_lock(hashtextextended(
        'chat.pair:' || LEAST(p_first, p_second) || ':' || GREATEST(p_first, p_second), 0));
END $$;

CREATE FUNCTION chat.pair_is_blocked(p_first bigint, p_second bigint) RETURNS boolean
LANGUAGE sql STABLE SET search_path = chat, pg_temp AS $$
    SELECT EXISTS (SELECT 1 FROM chat.user_blocks
        WHERE (blocker_id = p_first AND blocked_id = p_second)
           OR (blocker_id = p_second AND blocked_id = p_first));
$$;

CREATE FUNCTION chat.guard_user() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Use soft deletion: status=DELETED, deleted_at=clock_timestamp()' USING ERRCODE = '23514';
    END IF;
    IF NEW.user_id <> OLD.user_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Account identity and creation time are immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'DELETED' AND NEW.status <> 'DELETED' THEN
        RAISE EXCEPTION 'A deleted account cannot be reactivated' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER users_guard BEFORE UPDATE OR DELETE ON chat.users
FOR EACH ROW EXECUTE FUNCTION chat.guard_user();

CREATE FUNCTION chat.guard_social() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_first bigint; v_second bigint; v_needs_active boolean := true; v_pending boolean := false;
BEGIN
    IF TG_TABLE_NAME = 'friend_requests' THEN
        v_first := NEW.sender_id; v_second := NEW.receiver_id;
        v_needs_active := NEW.status IN ('PENDING', 'ACCEPTED');
        v_pending := NEW.status = 'PENDING';
        IF TG_OP = 'UPDATE' THEN
            IF (NEW.request_id, NEW.sender_id, NEW.receiver_id, NEW.created_at)
                IS DISTINCT FROM (OLD.request_id, OLD.sender_id, OLD.receiver_id, OLD.created_at)
                OR OLD.status <> 'PENDING' OR NEW.status = 'PENDING' THEN
                RAISE EXCEPTION 'Only a pending request can transition once to a final status' USING ERRCODE = '23514';
            END IF;
            NEW.responded_at := clock_timestamp();
        ELSIF NEW.status <> 'PENDING' THEN
            RAISE EXCEPTION 'New requests must be pending' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'friendships' THEN
        v_first := NEW.user_id_1; v_second := NEW.user_id_2;
    ELSE
        v_first := NEW.blocker_id; v_second := NEW.blocked_id;
    END IF;
    PERFORM chat.lock_user_pair(v_first, v_second);
    IF v_needs_active THEN
        PERFORM chat.require_active_user(LEAST(v_first, v_second));
        PERFORM chat.require_active_user(GREATEST(v_first, v_second));
        IF TG_TABLE_NAME <> 'user_blocks' AND chat.pair_is_blocked(v_first, v_second) THEN
            RAISE EXCEPTION 'This pair is blocked' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF v_pending
        AND EXISTS (SELECT 1 FROM chat.friendships
            WHERE user_id_1 = LEAST(v_first, v_second) AND user_id_2 = GREATEST(v_first, v_second)) THEN
        RAISE EXCEPTION 'These accounts are already friends' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER friend_requests_guard BEFORE INSERT OR UPDATE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();
CREATE TRIGGER friendships_guard BEFORE INSERT ON chat.friendships
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();
CREATE TRIGGER user_blocks_guard BEFORE INSERT ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.guard_social();

CREATE FUNCTION chat.apply_social_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_TABLE_NAME = 'friend_requests' THEN
        IF NEW.status = 'ACCEPTED' THEN
            INSERT INTO chat.friendships(user_id_1, user_id_2)
            VALUES (LEAST(NEW.sender_id, NEW.receiver_id), GREATEST(NEW.sender_id, NEW.receiver_id))
            ON CONFLICT DO NOTHING;
        END IF;
    ELSE
        DELETE FROM chat.friendships
        WHERE user_id_1 = LEAST(NEW.blocker_id, NEW.blocked_id)
            AND user_id_2 = GREATEST(NEW.blocker_id, NEW.blocked_id);
        UPDATE chat.friend_requests SET status = 'CANCELLED'
        WHERE status = 'PENDING' AND LEAST(sender_id, receiver_id) = LEAST(NEW.blocker_id, NEW.blocked_id)
            AND GREATEST(sender_id, receiver_id) = GREATEST(NEW.blocker_id, NEW.blocked_id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER friend_requests_apply AFTER UPDATE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.apply_social_change();
CREATE TRIGGER user_blocks_apply AFTER INSERT ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.apply_social_change();

CREATE FUNCTION chat.guard_conversation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM chat.require_active_user(NEW.created_by);
    ELSIF (NEW.conversation_id, NEW.type, NEW.created_by, NEW.created_at, NEW.encryption_mode)
        IS DISTINCT FROM (OLD.conversation_id, OLD.type, OLD.created_by, OLD.created_at, OLD.encryption_mode) THEN
        RAISE EXCEPTION 'Conversation identity, participants type and encryption mode are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER conversations_guard BEFORE INSERT OR UPDATE ON chat.conversations
FOR EACH ROW EXECUTE FUNCTION chat.guard_conversation();

CREATE FUNCTION chat.guard_member() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_type chat.conversation_type; v_peer bigint; v_max bigint;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Keep memberships for history; use left_at to leave a group' USING ERRCODE = '23514';
    END IF;
    SELECT type INTO v_type FROM chat.conversations WHERE conversation_id = NEW.conversation_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conversation does not exist' USING ERRCODE = '23503';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.conversation_id, NEW.user_id, NEW.joined_at)
            IS DISTINCT FROM (OLD.conversation_id, OLD.user_id, OLD.joined_at) THEN
            RAISE EXCEPTION 'Membership identity and original join time are immutable' USING ERRCODE = '23514';
        END IF;
        IF NEW.history_cleared_through_seq < OLD.history_cleared_through_seq THEN
            RAISE EXCEPTION 'History watermark cannot move backwards' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'INSERT' OR (OLD.left_at IS NOT NULL AND NEW.left_at IS NULL) THEN
        PERFORM chat.require_active_user(NEW.user_id);
    END IF;
    SELECT COALESCE(max(message_seq), 0) INTO v_max FROM chat.messages WHERE conversation_id = NEW.conversation_id;
    IF NEW.history_cleared_through_seq > v_max THEN
        RAISE EXCEPTION 'History watermark exceeds the latest message' USING ERRCODE = '23514';
    END IF;
    IF v_type = 'DIRECT' THEN
        IF NEW.left_at IS NOT NULL OR NEW.group_role <> 'MEMBER' THEN
            RAISE EXCEPTION 'Direct participants cannot leave or hold group roles' USING ERRCODE = '23514';
        END IF;
        IF TG_OP = 'INSERT' THEN
            IF (SELECT count(*) FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id) >= 2 THEN
                RAISE EXCEPTION 'A direct conversation has exactly two participants' USING ERRCODE = '23514';
            END IF;
            SELECT user_id INTO v_peer FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id;
            IF FOUND THEN
                PERFORM chat.lock_user_pair(NEW.user_id, v_peer);
                IF EXISTS (
                    SELECT 1 FROM chat.conversation_members a
                    JOIN chat.conversation_members b USING (conversation_id)
                    JOIN chat.conversations c USING (conversation_id)
                    WHERE c.type = 'DIRECT' AND c.conversation_id <> NEW.conversation_id
                        AND a.user_id = NEW.user_id AND b.user_id = v_peer
                ) THEN
                    RAISE EXCEPTION 'A direct conversation already exists for this pair' USING ERRCODE = '23505';
                END IF;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER members_guard BEFORE INSERT OR UPDATE OR DELETE ON chat.conversation_members
FOR EACH ROW EXECUTE FUNCTION chat.guard_member();

CREATE FUNCTION chat.check_conversation_members() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_id bigint; v_conversation chat.conversations%ROWTYPE; v_total bigint; v_admins bigint;
BEGIN
    v_id := NEW.conversation_id;
    SELECT * INTO v_conversation FROM chat.conversations WHERE conversation_id = v_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*), count(*) FILTER (WHERE group_role = 'ADMIN') INTO v_total, v_admins
    FROM chat.conversation_members WHERE conversation_id = v_id AND left_at IS NULL;
    IF v_conversation.type = 'DIRECT' THEN
        IF v_total <> 2 THEN
            RAISE EXCEPTION 'Direct conversation % needs exactly two participants at commit', v_id USING ERRCODE = '23514';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = v_id
            AND user_id = v_conversation.created_by) THEN
            RAISE EXCEPTION 'Direct creator must be a participant' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF v_total < 1 OR v_admins < 1 THEN
            RAISE EXCEPTION 'Group % must retain a current admin', v_id USING ERRCODE = '23514';
        END IF;
        IF TG_TABLE_NAME = 'conversations' THEN
            IF v_total < 2 OR NOT EXISTS (SELECT 1 FROM chat.conversation_members
                WHERE conversation_id = v_id AND user_id = v_conversation.created_by
                    AND group_role = 'ADMIN' AND left_at IS NULL) THEN
                RAISE EXCEPTION 'New group needs its creator as admin and at least one other member' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END $$;
-- Deferred: a conversation and its initial members must be inserted in ONE transaction.
CREATE CONSTRAINT TRIGGER conversations_members_at_commit AFTER INSERT ON chat.conversations
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION chat.check_conversation_members();
CREATE CONSTRAINT TRIGGER members_at_commit AFTER INSERT OR UPDATE ON chat.conversation_members
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION chat.check_conversation_members();

CREATE FUNCTION chat.guard_message() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_conversation chat.conversations%ROWTYPE; v_peer bigint; v_next bigint;
BEGIN
    SELECT * INTO v_conversation FROM chat.conversations WHERE conversation_id = NEW.conversation_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Conversation does not exist' USING ERRCODE = '23503'; END IF;
    IF v_conversation.type = 'DIRECT' THEN
        SELECT user_id INTO v_peer FROM chat.conversation_members
        WHERE conversation_id = NEW.conversation_id AND user_id <> NEW.sender_id;
        IF v_peer IS NULL THEN RAISE EXCEPTION 'Direct conversation is incomplete' USING ERRCODE = '23514'; END IF;
        PERFORM chat.lock_user_pair(NEW.sender_id, v_peer);
        PERFORM chat.require_active_user(LEAST(NEW.sender_id, v_peer));
        PERFORM chat.require_active_user(GREATEST(NEW.sender_id, v_peer));
        IF chat.pair_is_blocked(NEW.sender_id, v_peer) THEN
            RAISE EXCEPTION 'Direct messages are unavailable for a blocked pair' USING ERRCODE = '23514';
        END IF;
    ELSE
        PERFORM chat.require_active_user(NEW.sender_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members
        WHERE conversation_id = NEW.conversation_id AND user_id = NEW.sender_id AND left_at IS NULL) THEN
        RAISE EXCEPTION 'Sender must be a current member' USING ERRCODE = '23514';
    END IF;
    IF v_conversation.encryption_mode = 'NONE' AND (NEW.content IS NULL OR NEW.key_version IS NOT NULL) THEN
        RAISE EXCEPTION 'This conversation requires a plaintext payload' USING ERRCODE = '23514';
    ELSIF v_conversation.encryption_mode = 'E2EE' THEN
        IF NEW.content IS NOT NULL OR NEW.key_version IS NULL OR NOT EXISTS (
            SELECT 1 FROM chat.group_key_versions WHERE conversation_id = NEW.conversation_id
                AND key_version = NEW.key_version AND retired_at IS NULL) THEN
            RAISE EXCEPTION 'E2EE requires ciphertext and the current group key version' USING ERRCODE = '23514';
        END IF;
    END IF;
    SELECT COALESCE(max(message_seq), 0) + 1 INTO v_next FROM chat.messages WHERE conversation_id = NEW.conversation_id;
    IF NEW.message_seq IS NOT NULL AND NEW.message_seq <> v_next THEN
        RAISE EXCEPTION 'message_seq must be the next sequence (%)', v_next USING ERRCODE = '23514';
    END IF;
    NEW.message_seq := v_next;
    NEW.sent_at := clock_timestamp();
    RETURN NEW;
END $$;
CREATE TRIGGER messages_guard BEFORE INSERT ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.guard_message();

CREATE FUNCTION chat.log_message_sent() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    INSERT INTO chat.activity_logs(user_id, event_type, conversation_id, occurred_at)
    VALUES (NEW.sender_id, 'MESSAGE_SENT', NEW.conversation_id, NEW.sent_at);
    RETURN NEW;
END $$;
CREATE TRIGGER messages_log AFTER INSERT ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.log_message_sent();

CREATE FUNCTION chat.deny_history_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; mutation % is unavailable', TG_TABLE_NAME, TG_OP USING ERRCODE = '23514';
END $$;
CREATE TRIGGER messages_immutable BEFORE UPDATE OR DELETE ON chat.messages
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER activity_immutable BEFORE UPDATE OR DELETE ON chat.activity_logs
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER login_immutable BEFORE UPDATE OR DELETE ON chat.login_history
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER friendships_no_update BEFORE UPDATE ON chat.friendships
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER blocks_no_update BEFORE UPDATE ON chat.user_blocks
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER friend_requests_no_delete BEFORE DELETE ON chat.friend_requests
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_activity() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    IF TG_TABLE_NAME = 'activity_logs' THEN
        IF NEW.event_type = 'MESSAGE_SENT' THEN
            IF pg_trigger_depth() < 2 THEN
                RAISE EXCEPTION 'MESSAGE_SENT is written automatically by the messages trigger' USING ERRCODE = '23514';
            END IF;
        ELSE
            PERFORM chat.require_active_user(NEW.user_id);
        END IF;
    ELSE
        PERFORM chat.require_active_user(NEW.user_id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER activity_guard BEFORE INSERT ON chat.activity_logs
FOR EACH ROW EXECUTE FUNCTION chat.guard_activity();
CREATE TRIGGER login_guard BEFORE INSERT ON chat.login_history
FOR EACH ROW EXECUTE FUNCTION chat.guard_activity();

CREATE FUNCTION chat.guard_visibility_record() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_message chat.messages%ROWTYPE; v_actor bigint;
BEGIN
    IF TG_TABLE_NAME = 'spam_reports' AND TG_OP = 'UPDATE' THEN
        IF (NEW.report_id, NEW.reporter_id, NEW.reported_user_id, NEW.message_id, NEW.reason, NEW.created_at)
            IS DISTINCT FROM (OLD.report_id, OLD.reporter_id, OLD.reported_user_id, OLD.message_id, OLD.reason, OLD.created_at)
            OR OLD.status <> 'PENDING' OR NEW.status = 'PENDING' THEN
            RAISE EXCEPTION 'Only pending report status can change; original evidence is immutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO v_message FROM chat.messages WHERE message_id = NEW.message_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Message does not exist' USING ERRCODE = '23503'; END IF;
    PERFORM 1 FROM chat.conversations WHERE conversation_id = v_message.conversation_id FOR UPDATE;
    IF TG_TABLE_NAME = 'spam_reports' THEN
        v_actor := NEW.reporter_id;
        IF NEW.status <> 'PENDING' OR NEW.reported_user_id <> v_message.sender_id THEN
            RAISE EXCEPTION 'Report must be pending and target the actual sender' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM chat.message_hidden_for_users WHERE user_id = v_actor AND message_id = NEW.message_id) THEN
            RAISE EXCEPTION 'Hidden messages cannot be reported' USING ERRCODE = '23514';
        END IF;
    ELSE
        v_actor := NEW.user_id;
    END IF;
    PERFORM chat.require_active_user(v_actor);
    IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = v_message.conversation_id
        AND user_id = v_actor AND left_at IS NULL AND history_cleared_through_seq < v_message.message_seq) THEN
        RAISE EXCEPTION 'Message is not visible to this account' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER hidden_guard BEFORE INSERT ON chat.message_hidden_for_users
FOR EACH ROW EXECUTE FUNCTION chat.guard_visibility_record();
CREATE TRIGGER reports_guard BEFORE INSERT OR UPDATE ON chat.spam_reports
FOR EACH ROW EXECUTE FUNCTION chat.guard_visibility_record();
CREATE TRIGGER hidden_immutable BEFORE UPDATE OR DELETE ON chat.message_hidden_for_users
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER reports_no_delete BEFORE DELETE ON chat.spam_reports
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_crypto_record() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
DECLARE v_mode chat.encryption_mode; v_owner bigint; v_revoked timestamptz;
BEGIN
    IF TG_TABLE_NAME = 'user_devices' THEN
        IF TG_OP = 'INSERT' THEN
            PERFORM chat.require_active_user(NEW.user_id);
        ELSIF (NEW.device_id, NEW.user_id, NEW.public_key, NEW.created_at)
            IS DISTINCT FROM (OLD.device_id, OLD.user_id, OLD.public_key, OLD.created_at)
            OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
            RAISE EXCEPTION 'Device identity/key are immutable and revocation is final' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT encryption_mode INTO v_mode FROM chat.conversations
    WHERE conversation_id = NEW.conversation_id AND type = 'GROUP' FOR UPDATE;
    IF NOT FOUND OR v_mode <> 'E2EE' THEN
        RAISE EXCEPTION 'Group keys require an E2EE group conversation' USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'group_key_versions' THEN
        IF TG_OP = 'UPDATE' THEN
            IF (NEW.conversation_id, NEW.key_version, NEW.created_by, NEW.created_at)
                IS DISTINCT FROM (OLD.conversation_id, OLD.key_version, OLD.created_by, OLD.created_at)
                OR (OLD.retired_at IS NOT NULL AND NEW.retired_at IS DISTINCT FROM OLD.retired_at) THEN
                RAISE EXCEPTION 'Key identity is immutable and retirement is final' USING ERRCODE = '23514';
            END IF;
        ELSE
            PERFORM chat.require_active_user(NEW.created_by);
            IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id
                AND user_id = NEW.created_by AND left_at IS NULL) THEN
                RAISE EXCEPTION 'Key creator must be a current group member' USING ERRCODE = '23514';
            END IF;
        END IF;
    ELSE
        SELECT user_id, revoked_at INTO v_owner, v_revoked FROM chat.user_devices WHERE device_id = NEW.device_id FOR SHARE;
        IF NOT FOUND OR v_revoked IS NOT NULL THEN
            RAISE EXCEPTION 'Envelope device is missing or revoked' USING ERRCODE = '23514';
        END IF;
        PERFORM chat.require_active_user(v_owner);
        IF NOT EXISTS (SELECT 1 FROM chat.conversation_members WHERE conversation_id = NEW.conversation_id
            AND user_id = v_owner AND left_at IS NULL) OR NOT EXISTS (
            SELECT 1 FROM chat.group_key_versions WHERE conversation_id = NEW.conversation_id
                AND key_version = NEW.key_version AND retired_at IS NULL) THEN
            RAISE EXCEPTION 'Device owner needs current membership and an active key version' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER devices_guard BEFORE INSERT OR UPDATE ON chat.user_devices
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER versions_guard BEFORE INSERT OR UPDATE ON chat.group_key_versions
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER envelopes_guard BEFORE INSERT ON chat.group_key_envelopes
FOR EACH ROW EXECUTE FUNCTION chat.guard_crypto_record();
CREATE TRIGGER devices_no_delete BEFORE DELETE ON chat.user_devices
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER versions_no_delete BEFORE DELETE ON chat.group_key_versions
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();
CREATE TRIGGER envelopes_immutable BEFORE UPDATE OR DELETE ON chat.group_key_envelopes
FOR EACH ROW EXECUTE FUNCTION chat.deny_history_mutation();

CREATE FUNCTION chat.guard_write_isolation() RETURNS trigger
LANGUAGE plpgsql SET search_path = chat, pg_temp AS $$
BEGIN
    -- The advisory-lock + fresh-query checks intentionally use READ COMMITTED.
    -- A repeatable snapshot could miss a pair committed while waiting for a lock.
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Chat writes require READ COMMITTED isolation (PostgreSQL default)'
            USING ERRCODE = '25000';
    END IF;
    RETURN NULL;
END $$;
DO $$
DECLARE v_table text;
BEGIN
    FOREACH v_table IN ARRAY ARRAY['users','friend_requests','friendships','user_blocks',
        'conversations','conversation_members','user_devices','group_key_versions',
        'group_key_envelopes','messages','message_hidden_for_users','login_history',
        'activity_logs','spam_reports'] LOOP
        EXECUTE format('CREATE TRIGGER write_isolation BEFORE INSERT OR UPDATE OR DELETE ON chat.%I '
            'FOR EACH STATEMENT EXECUTE FUNCTION chat.guard_write_isolation()',v_table);
    END LOOP;
END $$;

-- No PUBLIC execution grants. Grant only to a trusted server-side role later.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA chat FROM PUBLIC;
