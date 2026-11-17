-- Run only on a development/test database after installation.
-- All rows are rolled back. Identity sequences can still advance (normal PostgreSQL behavior).
BEGIN;
CREATE TEMP TABLE relay_test_results (label text NOT NULL);

CREATE FUNCTION pg_temp.assert_true(p_condition boolean, p_label text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF p_condition IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %', p_label; END IF;
    INSERT INTO relay_test_results VALUES (p_label);
END $$;

CREATE FUNCTION pg_temp.expect_error(p_sql text, p_states text[], p_label text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE v_state text;
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
        IF v_state = ANY(p_states) THEN
            INSERT INTO relay_test_results VALUES (p_label);
            RETURN;
        END IF;
        RAISE EXCEPTION 'FAIL: %, expected SQLSTATE %, got %: %', p_label, p_states, v_state, SQLERRM;
    END;
    RAISE EXCEPTION 'FAIL: %, expected error but operation succeeded', p_label;
END $$;

DO $$
DECLARE
    a bigint; b bigint; c bigint; d bigint; admin_id bigint;
    direct_id bigint; group_id bigint; enc_id bigint; req_id bigint;
    m1 bigint; m2 bigint; m3 bigint; v_report_id bigint;
    dev_a bigint; dev_c bigint; dev_d bigint; boundary bigint;
    v_count bigint; v_hash text := '$TEST_ONLY_HASH_NOT_A_VALID_LOGIN_HASH$';
    prefix text := 'test_' || txid_current() || '_';
BEGIN
    PERFORM pg_temp.assert_true((SELECT count(*) = 18 FROM information_schema.tables
        WHERE table_schema = 'chat' AND table_type = 'BASE TABLE'), 'ERD has exactly 18 tables');
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(prefix||'a',prefix||'a@example.com',v_hash,'An') RETURNING user_id INTO a;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(prefix||'b',prefix||'b@example.com',v_hash,'Binh') RETURNING user_id INTO b;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(prefix||'c',prefix||'c@example.com',v_hash,'Chi') RETURNING user_id INTO c;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(prefix||'d',prefix||'d@example.com',v_hash,'Dung') RETURNING user_id INTO d;
    INSERT INTO chat.users(username,email,password_hash,full_name,system_role)
    VALUES(prefix||'admin',prefix||'admin@example.com',v_hash,'Admin','ADMIN') RETURNING user_id INTO admin_id;

    PERFORM pg_temp.expect_error(format('INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(%L,%L,%L,%L)',
        prefix||'other',upper(prefix||'a@example.com'),v_hash,'Other'), ARRAY['23505'], 'Email uniqueness ignores case');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(%L,%L,%L,%L)',
        upper(prefix||'a'),prefix||'other@example.com',v_hash,'Other'), ARRAY['23505'], 'Username uniqueness ignores case');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_friend_request(%s,%s)',a,a),ARRAY['23514'],'No self friend request');
    req_id := chat.send_friend_request(a,b);
    PERFORM pg_temp.expect_error(format('SELECT chat.send_friend_request(%s,%s)',b,a),ARRAY['23505'],'One pending pair in either direction');
    PERFORM pg_temp.expect_error(format('SELECT chat.respond_friend_request(%s,%s,''ACCEPTED'')',c,req_id),ARRAY['42501'],'Only receiver accepts');
    PERFORM chat.respond_friend_request(b,req_id,'ACCEPTED');
    PERFORM pg_temp.assert_true(EXISTS(SELECT 1 FROM chat.friendships WHERE user_id_1=a AND user_id_2=b),'Acceptance creates canonical friendship');
    PERFORM pg_temp.expect_error(format('SELECT chat.respond_friend_request(%s,%s,''REJECTED'')',b,req_id),ARRAY['42501'],'Final request cannot transition again');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_friend_request(%s,%s)',a,b),ARRAY['23514'],'No request for existing friends');

    direct_id := chat.create_direct_conversation(a,b);
    PERFORM pg_temp.assert_true(direct_id=chat.create_direct_conversation(b,a),'Direct conversation creation is idempotent');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.conversation_members(conversation_id,user_id) VALUES(%s,%s)',direct_id,c),ARRAY['23514'],'Direct conversation cannot have third member');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.conversations(type,created_by) VALUES(''DIRECT'',%s); SET CONSTRAINTS ALL IMMEDIATE',a),ARRAY['23514'],'Deferred check rejects incomplete direct conversation');
    PERFORM pg_temp.expect_error(format($q$
        DO $inner$ DECLARE x bigint; BEGIN
        INSERT INTO chat.conversations(type,created_by) VALUES('DIRECT',%s) RETURNING conversation_id INTO x;
        INSERT INTO chat.conversation_members(conversation_id,user_id) VALUES(x,%s),(x,%s);
        END $inner$ $q$,a,a,b),ARRAY['23505'],'Raw duplicate direct pair is rejected');
    group_id := chat.create_group(a,'Test group',ARRAY[b,c]);
    PERFORM pg_temp.assert_true((SELECT count(*)=3 FROM chat.conversation_members WHERE conversation_id=group_id),'Group includes creator and unique supplied members');
    PERFORM pg_temp.expect_error(format('SELECT chat.create_group(%s,''Solo'',ARRAY[]::bigint[])',a),ARRAY['23514'],'New group cannot be solo');
    -- Validate initial membership now, then test later lifecycle changes.
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;

    m1 := chat.send_text_message(a,direct_id,'Hello PostgreSQL');
    m2 := chat.send_text_message(b,direct_id,'Hello An');
    PERFORM pg_temp.assert_true((SELECT message_seq=1 FROM chat.messages WHERE message_id=m1),'First per-conversation sequence is one');
    PERFORM pg_temp.assert_true((SELECT message_seq=2 FROM chat.messages WHERE message_id=m2),'Second per-conversation sequence is two');
    PERFORM pg_temp.assert_true((SELECT count(*)=2 FROM chat.activity_logs WHERE conversation_id=direct_id AND event_type='MESSAGE_SENT'),'Each message writes exactly one activity event');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''outside'')',d,group_id),ARRAY['23514'],'Outsider cannot send');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''   '')',a,direct_id),ARRAY['23514'],'Whitespace-only message rejected');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.messages(conversation_id,sender_id,message_seq,content) VALUES(%s,%s,99,''bad sequence'')',direct_id,a),ARRAY['23514'],'Client cannot skip message sequence');
    PERFORM pg_temp.expect_error(format('UPDATE chat.messages SET content=''changed'' WHERE message_id=%s',m1),ARRAY['23514'],'Messages are immutable');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.activity_logs(user_id,event_type,conversation_id) VALUES(%s,''MESSAGE_SENT'',%s)',a,direct_id),ARRAY['23514'],'Caller cannot manually double-count MESSAGE_SENT');
    PERFORM chat.hide_message(b,m1);
    PERFORM pg_temp.assert_true((SELECT count(*)=0 FROM chat.search_visible_messages(b,direct_id,'PostgreSQL')),'Search excludes individually hidden messages');
    PERFORM pg_temp.assert_true((SELECT count(*)=1 FROM chat.search_visible_messages(a,direct_id,'PostgreSQL')),'Hiding does not affect other participants');
    PERFORM pg_temp.expect_error(format('SELECT chat.submit_spam_report(%s,%s,''hidden'')',b,m1),ARRAY['23514'],'Hidden message cannot be reported');
    PERFORM pg_temp.expect_error(format('SELECT chat.submit_spam_report(%s,%s,''self'')',a,m1),ARRAY['23514'],'No self report');
    PERFORM pg_temp.expect_error(format('SELECT chat.submit_spam_report(%s,%s,''outsider'')',d,m1),ARRAY['23514'],'Outsider cannot report');
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.spam_reports(reporter_id,reported_user_id,message_id,reason) VALUES(%s,%s,%s,''mismatch'')',a,c,m2),ARRAY['23514'],'Report target must match actual sender');
    boundary := chat.clear_history(a,direct_id);
    PERFORM pg_temp.assert_true(boundary=2,'Clear-history watermark equals latest sequence');
    PERFORM pg_temp.assert_true((SELECT count(*)=0 FROM chat.get_visible_messages(a,direct_id)),'History hidden only through watermark');
    PERFORM pg_temp.assert_true((SELECT count(*)=1 FROM chat.get_visible_messages(b,direct_id)),'Other participant keeps own visibility');
    m3 := chat.send_text_message(b,direct_id,'New message after clearing 100%');
    PERFORM pg_temp.assert_true((SELECT count(*)=1 FROM chat.get_visible_messages(a,direct_id)),'Future messages survive clear-history');
    PERFORM pg_temp.assert_true((SELECT count(*)=1 FROM chat.search_visible_messages(a,direct_id,'100%')),'Search treats percent literally');
    v_report_id := chat.submit_spam_report(a,m3,'Test report');
    PERFORM pg_temp.expect_error(format('SELECT chat.review_spam_report(%s,%s,''REVIEWED'')',b,v_report_id),ARRAY['42501'],'Only system admin reviews reports');
    PERFORM chat.review_spam_report(admin_id,v_report_id,'REVIEWED');
    PERFORM pg_temp.assert_true((SELECT status='REVIEWED' FROM chat.spam_reports WHERE spam_reports.report_id=v_report_id),'Admin can review pending report');
    PERFORM pg_temp.expect_error(format('SELECT chat.review_spam_report(%s,%s,''DISMISSED'')',admin_id,v_report_id),ARRAY['23514'],'Resolved report cannot be processed twice');

    PERFORM chat.block_user(a,b);
    PERFORM pg_temp.assert_true(NOT EXISTS(SELECT 1 FROM chat.friendships WHERE user_id_1=a AND user_id_2=b),'Block removes friendship');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''blocked'')',b,direct_id),ARRAY['23514'],'Block prevents direct sends in both directions');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_friend_request(%s,%s)',b,a),ARRAY['23514'],'Block prevents friend requests');
    PERFORM chat.unblock_user(a,b);
    req_id := chat.send_friend_request(a,d);
    PERFORM chat.block_user(d,a);
    PERFORM pg_temp.assert_true((SELECT status='CANCELLED' FROM chat.friend_requests WHERE request_id=req_id),'Block cancels pending request');
    PERFORM chat.unblock_user(d,a);
    PERFORM chat.set_account_locked(admin_id,b,true);
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''locked'')',b,direct_id),ARRAY['23514'],'Locked sender cannot send');
    PERFORM chat.set_account_locked(admin_id,b,false);

    PERFORM pg_temp.expect_error(format('SELECT chat.remove_group_member(%s,%s,%s)',b,group_id,c),ARRAY['42501'],'Member cannot remove another member');
    PERFORM pg_temp.expect_error(format('SELECT chat.change_group_role(%s,%s,%s,''MEMBER''); SET CONSTRAINTS ALL IMMEDIATE',a,group_id,a),ARRAY['23514'],'Cannot demote last group admin');
    PERFORM chat.change_group_role(a,group_id,b,'ADMIN');
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM chat.leave_group(a,group_id);
    PERFORM pg_temp.assert_true(EXISTS(SELECT 1 FROM chat.conversation_members WHERE conversation_id=group_id AND user_id=a AND left_at IS NOT NULL),'Leaving retains historical membership');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''left'')',a,group_id),ARRAY['23514'],'Former member cannot send');
    PERFORM chat.remove_group_member(b,group_id,c);
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM pg_temp.assert_true((SELECT member_count=1 AND admin_count=1 FROM chat.v_group_summary WHERE conversation_id=group_id),'Existing group may shrink to one admin');

    enc_id := chat.create_group(a,'Encrypted test',ARRAY[b],'E2EE');
    INSERT INTO chat.user_devices(user_id,public_key) VALUES(a,decode('010203','hex')) RETURNING device_id INTO dev_a;
    INSERT INTO chat.user_devices(user_id,public_key) VALUES(c,decode('040506','hex')) RETURNING device_id INTO dev_c;
    INSERT INTO chat.user_devices(user_id,public_key) VALUES(d,decode('070809','hex')) RETURNING device_id INTO dev_d;
    INSERT INTO chat.group_key_versions(conversation_id,key_version,created_by) VALUES(enc_id,1,a);
    INSERT INTO chat.group_key_envelopes(conversation_id,key_version,device_id,wrapped_key) VALUES(enc_id,1,dev_a,decode('112233','hex'));
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.group_key_envelopes(conversation_id,key_version,device_id,wrapped_key) VALUES(%s,1,%s,decode(''11'',''hex''))',enc_id,dev_c),ARRAY['23514'],'Outsider device cannot receive group key');
    PERFORM chat.add_group_member(a,enc_id,d);
    UPDATE chat.user_devices SET revoked_at=clock_timestamp() WHERE device_id=dev_d;
    PERFORM pg_temp.expect_error(format('INSERT INTO chat.group_key_envelopes(conversation_id,key_version,device_id,wrapped_key) VALUES(%s,1,%s,decode(''11'',''hex''))',enc_id,dev_d),ARRAY['23514'],'Revoked device cannot receive group key');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_text_message(%s,%s,''plaintext'')',a,enc_id),ARRAY['23514'],'E2EE group rejects plaintext');
    PERFORM chat.send_encrypted_message(a,enc_id,decode('aabbcc','hex'),decode('0001','hex'),1);
    PERFORM pg_temp.assert_true((SELECT count(*)=0 FROM chat.search_visible_messages(a,enc_id,'aabb')),'Encrypted payload is not server text-searchable');
    PERFORM pg_temp.expect_error(format('SELECT chat.send_encrypted_message(%s,%s,decode(''aabb'',''hex''),decode(''0001'',''hex''),1)',a,enc_id),ARRAY['23505'],'Nonce cannot repeat within a key version');
    UPDATE chat.group_key_versions SET retired_at=clock_timestamp() WHERE conversation_id=enc_id AND key_version=1;
    INSERT INTO chat.group_key_versions(conversation_id,key_version,created_by) VALUES(enc_id,2,a);
    PERFORM pg_temp.expect_error(format('SELECT chat.send_encrypted_message(%s,%s,decode(''aabb'',''hex''),decode(''0002'',''hex''),1)',a,enc_id),ARRAY['23514'],'Retired key cannot encrypt new messages');
    PERFORM chat.send_encrypted_message(a,enc_id,decode('ddeeff','hex'),decode('0001','hex'),2);
    PERFORM pg_temp.assert_true((SELECT count(*)=2 FROM chat.messages WHERE conversation_id=enc_id),'New key version may reuse nonce value');

    INSERT INTO chat.login_history(user_id) VALUES(a);
    INSERT INTO chat.activity_logs(user_id,event_type) VALUES(a,'APP_OPEN');
    PERFORM pg_temp.assert_true(EXISTS(SELECT 1 FROM chat.v_user_last_login WHERE user_id=a),'Successful login stored separately');
    PERFORM pg_temp.expect_error(format('DELETE FROM chat.users WHERE user_id=%s',d),ARRAY['23514'],'Physical account deletion forbidden');
    UPDATE chat.users SET status='DELETED',deleted_at=clock_timestamp() WHERE user_id=d;
    PERFORM pg_temp.expect_error(format('UPDATE chat.users SET status=''ACTIVE'',deleted_at=NULL WHERE user_id=%s',d),ARRAY['23514'],'Deleted account cannot be reactivated');
    PERFORM pg_temp.expect_error(format('DELETE FROM chat.conversation_members WHERE conversation_id=%s AND user_id=%s',group_id,a),ARRAY['23514'],'Historical membership cannot be deleted');
    SET CONSTRAINTS ALL IMMEDIATE;
    SELECT count(*) INTO v_count FROM relay_test_results;
    RAISE NOTICE 'PASS: % SQL integrity/behavior checks; test rows will be rolled back.',v_count;
END $$;
SELECT count(*) AS passed_checks FROM relay_test_results;
ROLLBACK;
