-- OPTIONAL: development only; run separately after 00_install.sql.
-- Not idempotent. Refuses to overwrite existing demo accounts.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
    a bigint; b bigint; c bigint; d bigint; admin_id bigint;
    direct_id bigint; group_id bigint; request_id bigint; message_id bigint;
    -- Real bcrypt hash (cost 12) for DemoPass123!, verified during packaging.
    demo_hash text := '$2b$12$B1zVABcLojU.3zd7RlG4gen6H2ey2AXe/jlhb864mMNFOO4HIh19y';
BEGIN
    IF EXISTS (SELECT 1 FROM chat.users WHERE lower(email) IN
        ('alex.morgan@example.com','sophia.chen@example.com','marcus.johnson@example.com',
         'sarah.connor@example.com','admin@relay.example')) THEN
        RAISE EXCEPTION 'Demo email already exists; seed aborted without changing existing data';
    END IF;
    INSERT INTO chat.users(username,email,password_hash,full_name)
    VALUES('alex.morgan','alex.morgan@example.com',demo_hash,'Alex Morgan') RETURNING user_id INTO a;
    INSERT INTO chat.users(username,email,password_hash,full_name)
    VALUES('sophia.chen','sophia.chen@example.com',demo_hash,'Sophia Chen') RETURNING user_id INTO b;
    INSERT INTO chat.users(username,email,password_hash,full_name)
    VALUES('marcus.johnson','marcus.johnson@example.com',demo_hash,'Marcus Johnson') RETURNING user_id INTO c;
    INSERT INTO chat.users(username,email,password_hash,full_name)
    VALUES('sarah.connor','sarah.connor@example.com',demo_hash,'Sarah Connor') RETURNING user_id INTO d;
    INSERT INTO chat.users(username,email,password_hash,full_name,system_role)
    VALUES('relay.admin','admin@relay.example',demo_hash,'RELAY Administrator','ADMIN') RETURNING user_id INTO admin_id;

    request_id := chat.send_friend_request(a,b);
    PERFORM chat.respond_friend_request(b,request_id,'ACCEPTED');
    PERFORM chat.send_friend_request(c,a);
    PERFORM chat.send_friend_request(d,b);
    direct_id := chat.create_direct_conversation(a,b);
    PERFORM chat.send_text_message(a,direct_id,'Chào Sophia, mình đang hoàn thiện đồ án chat Java.');
    message_id := chat.send_text_message(b,direct_id,'Mình sẽ kiểm tra phần PostgreSQL và báo lại nhé.');
    PERFORM chat.submit_spam_report(a,message_id,'Báo cáo minh họa để kiểm tra màn hình quản trị.');
    group_id := chat.create_group(a,'Nhóm đồ án Java',ARRAY[b,c]);
    PERFORM chat.send_text_message(a,group_id,'Nhóm trao đổi về JavaFX, TCP Socket và PostgreSQL.');
    PERFORM chat.send_text_message(c,group_id,'Database đã có cấu trúc bảng và ràng buộc.');
    INSERT INTO chat.login_history(user_id) VALUES(a),(b),(c),(admin_id);
    INSERT INTO chat.activity_logs(user_id,event_type) VALUES(a,'APP_OPEN'),(b,'APP_OPEN'),(c,'APP_OPEN');
    RAISE NOTICE 'Demo created: 5 accounts, 1 direct conversation, 1 group, 4 messages, 1 pending spam report.';
END $$;
COMMIT;
