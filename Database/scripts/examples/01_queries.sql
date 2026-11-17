-- Read-only examples. Some use the optional demo accounts/group.
-- Never return password_hash to a desktop client.

-- 1. User management list.
SELECT user_id,username,email,full_name,system_role,status,created_at
FROM chat.users WHERE status <> 'DELETED' ORDER BY created_at DESC,user_id DESC LIMIT 50;

-- 2. Friends of Alex (both orientations of the canonical pair).
WITH actor AS (SELECT user_id FROM chat.users WHERE email='alex.morgan@example.com')
SELECT u.user_id,u.full_name,u.status
FROM chat.friendships f CROSS JOIN actor a
JOIN chat.users u ON u.user_id=CASE WHEN f.user_id_1=a.user_id THEN f.user_id_2 ELSE f.user_id_1 END
WHERE a.user_id IN (f.user_id_1,f.user_id_2) ORDER BY u.full_name;

-- 3. Friends-of-friends: no self, existing friends, or blocked pairs.
WITH actor AS (SELECT user_id FROM chat.users WHERE email='alex.morgan@example.com'),
edges AS (
    SELECT user_id_1 AS source,user_id_2 AS target FROM chat.friendships
    UNION ALL SELECT user_id_2,user_id_1 FROM chat.friendships
), direct_friends AS (SELECT target FROM edges,actor WHERE source=actor.user_id)
SELECT DISTINCT u.user_id,u.full_name FROM edges e
JOIN direct_friends d ON e.source=d.target JOIN chat.users u ON u.user_id=e.target CROSS JOIN actor a
WHERE u.user_id<>a.user_id AND u.status='ACTIVE'
    AND NOT EXISTS(SELECT 1 FROM direct_friends WHERE target=u.user_id)
    AND NOT chat.pair_is_blocked(a.user_id,u.user_id)
ORDER BY u.full_name;

-- 4. Visible history: newest first; pass oldest returned message_seq as the next before_seq.
SELECT m.* FROM chat.get_visible_messages(
    (SELECT user_id FROM chat.users WHERE email='alex.morgan@example.com'),
    (SELECT conversation_id FROM chat.conversations WHERE name='Nhóm đồ án Java' ORDER BY conversation_id LIMIT 1),50,NULL
) m;

-- 5. Search only messages that Alex can currently see.
SELECT m.* FROM chat.search_visible_messages(
    (SELECT user_id FROM chat.users WHERE email='alex.morgan@example.com'),
    (SELECT conversation_id FROM chat.conversations WHERE name='Nhóm đồ án Java' ORDER BY conversation_id LIMIT 1),'PostgreSQL',50
) m;

-- 6. Group overview.
SELECT * FROM chat.v_group_summary ORDER BY created_at DESC;

-- 7. DISTINCT counts for the WHOLE period, not a sum of daily DISTINCT counts.
SELECT count(*) FILTER(WHERE a.event_type='APP_OPEN') AS app_opens,
    count(*) FILTER(WHERE a.event_type='MESSAGE_SENT') AS sent_messages,
    count(DISTINCT a.user_id) FILTER(WHERE a.event_type='MESSAGE_SENT') AS chatting_users,
    count(DISTINCT a.conversation_id) FILTER(WHERE a.event_type='MESSAGE_SENT' AND c.type='GROUP') AS chatting_groups,
    count(DISTINCT a.conversation_id) FILTER(WHERE a.event_type='MESSAGE_SENT' AND c.type='DIRECT') AS chatting_direct_conversations
FROM chat.activity_logs a LEFT JOIN chat.conversations c USING(conversation_id)
WHERE a.occurred_at>=CURRENT_TIMESTAMP-INTERVAL '30 days' AND a.occurred_at<CURRENT_TIMESTAMP;

-- 8. Successful logins: count events separately from opening the application.
SELECT u.user_id,u.full_name,l.last_login_at,l.successful_login_count
FROM chat.users u LEFT JOIN chat.v_user_last_login l USING(user_id) ORDER BY l.last_login_at DESC NULLS LAST;

-- 9. Moderation evidence. Ciphertext needs a separate client-side evidence protocol.
SELECT r.report_id,r.status,r.reason,r.created_at,reporter.full_name AS reporter,
    target.full_name AS reported_user,m.conversation_id,m.content,m.ciphertext
FROM chat.spam_reports r JOIN chat.users reporter ON reporter.user_id=r.reporter_id
JOIN chat.users target ON target.user_id=r.reported_user_id JOIN chat.messages m USING(message_id)
ORDER BY r.created_at DESC,r.report_id DESC;

-- 10. Daily UTC chart.
SELECT * FROM chat.v_daily_activity ORDER BY activity_date_utc DESC LIMIT 30;
