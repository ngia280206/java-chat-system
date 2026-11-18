-- Chay tren database phat trien sau khi cai bo sung. Du lieu test duoc ROLLBACK.
-- Identity sequence van co the tang sau rollback, day la hanh vi binh thuong.
BEGIN;
CREATE TEMP TABLE supplement_test_results(label text NOT NULL);
CREATE FUNCTION pg_temp.ok(p_condition boolean,p_label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_condition IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %',p_label; END IF;
    INSERT INTO supplement_test_results VALUES(p_label);
END $$;
CREATE FUNCTION pg_temp.fails(p_sql text,p_state text,p_label text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_state text;
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state=RETURNED_SQLSTATE;
        IF v_state=p_state THEN INSERT INTO supplement_test_results VALUES(p_label); RETURN; END IF;
        RAISE EXCEPTION 'FAIL: %, expected %, got %: %',p_label,p_state,v_state,SQLERRM;
    END;
    RAISE EXCEPTION 'FAIL: %, expected an error',p_label;
END $$;

DO $$
DECLARE
    a bigint; b bigint; c bigint; d bigint; v_admin bigint; v_group bigint; v_direct bigint;
    v_inv bigint; v_next bigint; v_reset bigint; v_old bigint; v_msg bigint;
    r1 bigint; r2 bigint; r3 bigint; r4 bigint; r5 bigint; r6 bigint; v_action bigint;
    v_joined timestamptz; v_updated timestamptz;
    v_hash text:='$2b$12$B1zVABcLojU.3zd7RlG4gen6H2ey2AXe/jlhb864mMNFOO4HIh19y';
    v_token bytea:=decode(repeat('ab',32),'hex'); v_wrong bytea:=decode(repeat('cd',32),'hex');
    pfx text:='supplement_'||txid_current()||'_';
BEGIN
    PERFORM pg_temp.ok((SELECT count(*)=18 FROM information_schema.tables WHERE table_schema='chat' AND table_type='BASE TABLE'),'18 tables after migration');
    PERFORM pg_temp.ok(NOT EXISTS(SELECT 1 FROM chat.users u LEFT JOIN chat.user_settings s USING(user_id) WHERE s.user_id IS NULL),'Settings backfilled for existing users');
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(pfx||'a',pfx||'a@example.com',v_hash,'An') RETURNING user_id INTO a;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(pfx||'b',pfx||'b@example.com',v_hash,'Binh') RETURNING user_id INTO b;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(pfx||'c',pfx||'c@example.com',v_hash,'Chi') RETURNING user_id INTO c;
    INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(pfx||'d',pfx||'d@example.com',v_hash,'Dung') RETURNING user_id INTO d;
    INSERT INTO chat.users(username,email,password_hash,full_name,system_role) VALUES(pfx||'admin',pfx||'admin@example.com',v_hash,'Admin','ADMIN') RETURNING user_id INTO v_admin;

    PERFORM pg_temp.ok((SELECT desktop_notifications AND NOT sound_enabled AND message_previews FROM chat.user_settings WHERE user_id=a),'New account automatically receives default settings');
    SELECT updated_at INTO v_updated FROM chat.user_settings WHERE user_id=a;
    PERFORM chat.save_user_settings(a,false,true,false);
    PERFORM pg_temp.ok((SELECT NOT desktop_notifications AND sound_enabled AND NOT message_previews FROM chat.user_settings WHERE user_id=a),'Settings save persists all flags');
    PERFORM pg_temp.ok((SELECT updated_at>=v_updated FROM chat.user_settings WHERE user_id=a),'Saving updates settings timestamp');
    PERFORM pg_temp.fails(format('INSERT INTO chat.user_settings(user_id) VALUES(%s)',a),'23505','One settings row per user');
    PERFORM pg_temp.fails(format('UPDATE chat.user_settings SET user_id=%s WHERE user_id=%s',b,a),'23514','Cannot reassign settings owner');
    PERFORM pg_temp.fails(format('SELECT chat.save_user_settings(%s,NULL,true,true)',a),'23502','Settings flags cannot be NULL');

    v_group:=chat.create_group(a,'Supplement test group',ARRAY[b]);
    v_direct:=chat.create_direct_conversation(a,b);
    SET CONSTRAINTS ALL IMMEDIATE; SET CONSTRAINTS ALL DEFERRED;
    PERFORM pg_temp.fails(format('SELECT chat.create_group_invitation(%s,%s,%s)',b,v_group,c),'42501','Ordinary member cannot invite');
    PERFORM pg_temp.fails(format('SELECT chat.create_group_invitation(%s,%s,%s)',a,v_direct,c),'23514','Cannot invite into DIRECT conversation');
    PERFORM pg_temp.fails(format('SELECT chat.create_group_invitation(%s,%s,%s)',a,v_group,b),'23514','Cannot invite existing member');
    v_inv:=chat.create_group_invitation(a,v_group,c);
    PERFORM pg_temp.ok((SELECT status='PENDING' AND responded_at IS NULL FROM chat.group_invitations WHERE invitation_id=v_inv),'New invitation is pending');
    PERFORM pg_temp.ok(NOT EXISTS(SELECT 1 FROM chat.conversation_members WHERE conversation_id=v_group AND user_id=c),'Pending invitation does not add membership');
    PERFORM pg_temp.fails(format('SELECT chat.create_group_invitation(%s,%s,%s)',a,v_group,c),'23505','No duplicate pending invitation');
    PERFORM pg_temp.fails(format('SELECT chat.respond_group_invitation(%s,%s,''ACCEPTED'')',d,v_inv),'42501','Another user cannot accept invitation');
    PERFORM chat.respond_group_invitation(c,v_inv,'ACCEPTED');
    PERFORM pg_temp.ok((SELECT status='ACCEPTED' AND responded_at IS NOT NULL FROM chat.group_invitations WHERE invitation_id=v_inv),'Accept resolves invitation');
    PERFORM pg_temp.ok(EXISTS(SELECT 1 FROM chat.conversation_members WHERE conversation_id=v_group AND user_id=c AND group_role='MEMBER' AND left_at IS NULL),'Accept adds current membership');
    PERFORM pg_temp.fails(format('SELECT chat.respond_group_invitation(%s,%s,''REJECTED'')',c,v_inv),'23514','Invitation cannot be processed twice');
    v_inv:=chat.create_group_invitation(a,v_group,d);
    PERFORM chat.respond_group_invitation(d,v_inv,'REJECTED');
    PERFORM pg_temp.ok(NOT EXISTS(SELECT 1 FROM chat.conversation_members WHERE conversation_id=v_group AND user_id=d),'Reject does not create membership');
    v_next:=chat.create_group_invitation(a,v_group,d);
    PERFORM pg_temp.ok(v_next<>v_inv,'Can invite again after rejection');
    PERFORM chat.respond_group_invitation(a,v_next,'CANCELLED');
    PERFORM pg_temp.ok((SELECT status='CANCELLED' FROM chat.group_invitations WHERE invitation_id=v_next),'Inviter can cancel');
    SELECT joined_at INTO v_joined FROM chat.conversation_members WHERE conversation_id=v_group AND user_id=c;
    PERFORM chat.leave_group(c,v_group);
    v_inv:=chat.create_group_invitation(a,v_group,c);
    PERFORM chat.respond_group_invitation(c,v_inv,'ACCEPTED');
    PERFORM pg_temp.ok((SELECT left_at IS NULL AND joined_at=v_joined FROM chat.conversation_members WHERE conversation_id=v_group AND user_id=c),'Rejoining retains historical membership row');
    PERFORM pg_temp.fails(format('DELETE FROM chat.group_invitations WHERE invitation_id=%s',v_inv),'23514','Invitation history cannot be deleted');

    v_reset:=chat.create_password_reset(a,v_token);
    PERFORM pg_temp.ok((SELECT expires_at>created_at AND failed_attempts=0 AND attempt_limit=5 FROM chat.password_reset_requests WHERE reset_id=v_reset),'Reset has expiry and attempt defaults');
    PERFORM pg_temp.ok(NOT chat.complete_password_reset(v_reset,v_token,v_hash),'Cannot complete unverified reset');
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(v_reset,v_wrong),'Wrong reset code returns false');
    PERFORM pg_temp.ok((SELECT failed_attempts=1 FROM chat.password_reset_requests WHERE reset_id=v_reset),'Wrong attempt is counted');
    PERFORM pg_temp.ok(chat.verify_password_reset(v_reset,v_token),'Correct reset code verifies');
    PERFORM pg_temp.ok((SELECT verified_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=v_reset),'Verification time is saved');
    PERFORM pg_temp.fails(format('SELECT chat.complete_password_reset(%s,decode(%L,''hex''),''plaintext_password_not_a_hash'')',v_reset,encode(v_token,'hex')),'23514','Plaintext cannot be used as password hash');
    PERFORM pg_temp.ok(NOT chat.complete_password_reset(v_reset,v_wrong,v_hash),'Verified reset still requires correct token proof');
    PERFORM pg_temp.ok((SELECT failed_attempts=2 FROM chat.password_reset_requests WHERE reset_id=v_reset),'Wrong completion proof increments attempts');
    PERFORM pg_temp.ok(chat.complete_password_reset(v_reset,v_token,v_hash),'Verified reset can be consumed');
    PERFORM pg_temp.ok((SELECT consumed_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=v_reset),'Consumption timestamp saved');
    PERFORM pg_temp.ok((SELECT password_hash=v_hash FROM chat.users WHERE user_id=a),'Password hash saved to account');
    PERFORM pg_temp.ok(NOT chat.complete_password_reset(v_reset,v_token,v_hash),'Consumed reset cannot be reused');
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(v_reset,v_token),'Consumed reset cannot be reverified');
    v_old:=chat.create_password_reset(a,v_token);
    v_reset:=chat.create_password_reset(a,v_wrong);
    PERFORM pg_temp.ok((SELECT revoked_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=v_old),'New reset revokes prior open reset');
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(v_old,v_token),'Revoked reset is rejected');
    v_reset:=chat.create_password_reset(b,v_token,interval '15 minutes',2);
    PERFORM chat.verify_password_reset(v_reset,v_wrong);
    PERFORM chat.verify_password_reset(v_reset,v_wrong);
    PERFORM pg_temp.ok((SELECT failed_attempts=2 AND revoked_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=v_reset),'Attempt limit revokes reset');
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(v_reset,v_token),'Correct code rejected after attempt limit');
    PERFORM pg_temp.fails(format('UPDATE chat.password_reset_requests SET failed_attempts=0 WHERE reset_id=%s',v_reset),'23514','Attempt counter cannot be reset');
    INSERT INTO chat.password_reset_requests(user_id,token_hash,created_at,expires_at)
    VALUES(d,v_token,clock_timestamp()-interval '2 hours',clock_timestamp()-interval '1 hour') RETURNING reset_id INTO v_reset;
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(v_reset,v_token),'Expired reset is rejected');
    PERFORM pg_temp.ok(NOT chat.verify_password_reset(-1,v_token),'Unknown reset returns false');
    PERFORM pg_temp.fails(format('SELECT chat.create_password_reset(%s,decode(''123456'',''hex''))',a),'23514','Too-short token hash rejected');
    PERFORM pg_temp.ok((SELECT count(*)=1 FROM chat.password_reset_requests WHERE user_id=a AND consumed_at IS NULL AND revoked_at IS NULL),'Invalid new reset does not revoke prior request');
    PERFORM pg_temp.fails(format('SELECT chat.create_password_reset(%s,decode(%L,''hex''),interval ''-1 minute'')',c,encode(v_token,'hex')),'23514','Negative reset TTL rejected');

    v_msg:=chat.send_text_message(b,v_group,'Evidence message');
    r1:=chat.submit_spam_report(a,v_msg,'Report 1'); r2:=chat.submit_spam_report(a,v_msg,'Report 2');
    r3:=chat.submit_spam_report(a,v_msg,'Report 3'); r4:=chat.submit_spam_report(a,v_msg,'Report 4');
    r5:=chat.submit_spam_report(a,v_msg,'Report 5');
    PERFORM pg_temp.fails(format('SELECT chat.moderate_report(%s,%s,''WARN'',''note'')',a,r1),'42501','Ordinary user cannot moderate');
    PERFORM pg_temp.fails(format('SELECT chat.moderate_report(%s,%s,''WARN'',''   '')',v_admin,r1),'23514','Moderation requires nonempty notes');
    PERFORM pg_temp.fails(format('UPDATE chat.spam_reports SET status=''REVIEWED'' WHERE report_id=%s',r1),'23514','Raw report resolution cannot bypass decision log');
    v_action:=chat.moderate_report(v_admin,r1,'WARN','Warning note');
    PERFORM pg_temp.ok((SELECT admin_id=v_admin AND action_type='WARN' AND notes='Warning note' FROM chat.moderation_actions WHERE action_id=v_action),'Decision stores actual admin, type and notes');
    PERFORM pg_temp.ok((SELECT status='REVIEWED' FROM chat.spam_reports WHERE report_id=r1),'WARN marks report reviewed');
    PERFORM pg_temp.ok((SELECT status='ACTIVE' FROM chat.users WHERE user_id=b),'WARN does not lock account');
    PERFORM pg_temp.fails(format('SELECT chat.moderate_report(%s,%s,''DISMISS'',''again'')',v_admin,r1),'23514','Cannot moderate same report twice');
    PERFORM pg_temp.fails(format('UPDATE chat.moderation_actions SET notes=''changed'' WHERE action_id=%s',v_action),'23514','Decision cannot be edited');
    PERFORM pg_temp.fails(format('DELETE FROM chat.moderation_actions WHERE action_id=%s',v_action),'23514','Decision cannot be deleted');
    PERFORM chat.moderate_report(v_admin,r2,'DISMISS','Dismiss note');
    PERFORM pg_temp.ok((SELECT status='DISMISSED' FROM chat.spam_reports WHERE report_id=r2),'DISMISS updates report status');
    PERFORM chat.moderate_report(v_admin,r3,'REVIEW','Review note');
    PERFORM pg_temp.ok((SELECT status='REVIEWED' FROM chat.spam_reports WHERE report_id=r3),'REVIEW updates report status');
    PERFORM chat.moderate_report(v_admin,r4,'SUSPEND','Suspend note');
    PERFORM pg_temp.ok((SELECT status='LOCKED' FROM chat.users WHERE user_id=b),'SUSPEND locks reported account');
    PERFORM pg_temp.ok((SELECT status='REVIEWED' FROM chat.spam_reports WHERE report_id=r4),'SUSPEND also marks report reviewed');
    PERFORM chat.review_spam_report(v_admin,r5,'REVIEWED');
    PERFORM pg_temp.ok(EXISTS(SELECT 1 FROM chat.moderation_actions WHERE report_id=r5 AND admin_id=v_admin AND action_type='REVIEW'),'Legacy review API also logs decision');
    PERFORM pg_temp.ok((SELECT count(*)=5 FROM chat.moderation_actions WHERE report_id IN(r1,r2,r3,r4,r5)),'One stored decision for each resolved test report');
    r6:=chat.submit_spam_report(a,v_msg,'Report after account deletion');
    UPDATE chat.users SET status='DELETED',deleted_at=clock_timestamp() WHERE user_id=b;
    PERFORM pg_temp.fails(format('SELECT chat.moderate_report(%s,%s,''SUSPEND'',''cannot suspend deleted target'')',v_admin,r6),'23514','Cannot suspend deleted account');
    PERFORM pg_temp.ok((SELECT status='PENDING' FROM chat.spam_reports WHERE report_id=r6),'Failed suspension leaves report pending');
    PERFORM pg_temp.ok(NOT EXISTS(SELECT 1 FROM chat.moderation_actions WHERE report_id=r6),'Failed suspension does not leave partial decision');
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE NOTICE 'PASS: % supplement checks; rolling back test rows.',(SELECT count(*) FROM supplement_test_results);
END $$;
SELECT count(*) AS passed_checks FROM supplement_test_results;
ROLLBACK;
