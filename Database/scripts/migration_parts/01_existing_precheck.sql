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
