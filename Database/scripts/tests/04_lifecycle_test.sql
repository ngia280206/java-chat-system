BEGIN;
CREATE TEMP TABLE relay_lifecycle_checks(label text);
CREATE FUNCTION pg_temp.lifecycle_ok(p_condition boolean,p_label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 IF p_condition IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %',p_label; END IF;
 INSERT INTO relay_lifecycle_checks VALUES(p_label);
END $$;
DO $$
DECLARE a bigint;b bigint;c bigint;d bigint;g bigint;i bigint;r bigint;f bigint;
 h text:='$2b$12$B1zVABcLojU.3zd7RlG4gen6H2ey2AXe/jlhb864mMNFOO4HIh19y';
 p text:='lifecycle_'||txid_current()||'_'; token bytea:=decode(repeat('ab',32),'hex');
BEGIN
 INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(p||'a',p||'a@example.com',h,'A') RETURNING user_id INTO a;
 INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(p||'b',p||'b@example.com',h,'B') RETURNING user_id INTO b;
 INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(p||'c',p||'c@example.com',h,'C') RETURNING user_id INTO c;
 INSERT INTO chat.users(username,email,password_hash,full_name) VALUES(p||'d',p||'d@example.com',h,'D') RETURNING user_id INTO d;
 g:=chat.create_group(a,'Lifecycle test',ARRAY[b]);
 SET CONSTRAINTS ALL IMMEDIATE; SET CONSTRAINTS ALL DEFERRED;
 i:=chat.create_group_invitation(a,g,c);
 PERFORM chat.add_group_member(a,g,c);
 PERFORM pg_temp.lifecycle_ok((SELECT status='CANCELLED' FROM chat.group_invitations WHERE invitation_id=i),'Manual member addition cancels obsolete invitation');
 i:=chat.create_group_invitation(a,g,d);
 r:=chat.create_password_reset(d,token);
 f:=chat.send_friend_request(a,d);
 UPDATE chat.users SET status='DELETED',deleted_at=clock_timestamp() WHERE user_id=d;
 PERFORM pg_temp.lifecycle_ok((SELECT status='CANCELLED' FROM chat.group_invitations WHERE invitation_id=i),'Deleting account cancels group invitations');
 PERFORM pg_temp.lifecycle_ok((SELECT status='CANCELLED' FROM chat.friend_requests WHERE request_id=f),'Deleting account cancels friend requests');
 PERFORM pg_temp.lifecycle_ok((SELECT revoked_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=r),'Deleting account revokes reset');
 PERFORM pg_temp.lifecycle_ok(EXISTS(SELECT 1 FROM chat.users WHERE user_id=d AND status='DELETED'),'Soft deletion retains account and foreign keys');
 r:=chat.create_password_reset(c,token);
 UPDATE chat.users SET status='LOCKED' WHERE user_id=c;
 PERFORM pg_temp.lifecycle_ok((SELECT revoked_at IS NOT NULL FROM chat.password_reset_requests WHERE reset_id=r),'Locking account revokes reset');
 PERFORM chat.change_group_role(a,g,b,'ADMIN');
 UPDATE chat.users SET status='ACTIVE' WHERE user_id=c;
 PERFORM chat.remove_group_member(a,g,c);
 i:=chat.create_group_invitation(b,g,c);
 PERFORM chat.change_group_role(a,g,b,'MEMBER');
 PERFORM pg_temp.lifecycle_ok((SELECT status='CANCELLED' FROM chat.group_invitations WHERE invitation_id=i),'Demoted admin has pending invitations cancelled');
 SET CONSTRAINTS ALL IMMEDIATE;
 RAISE NOTICE 'PASS: % lifecycle checks; test rows will be rolled back.',(SELECT count(*) FROM relay_lifecycle_checks);
END $$;
ROLLBACK;
