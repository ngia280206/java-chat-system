-- Administrative projections. These are NOT row-level access-control policies.
CREATE VIEW chat.v_group_summary AS
SELECT c.conversation_id, c.name, c.created_by, c.created_at, c.encryption_mode,
    count(cm.user_id) FILTER (WHERE cm.left_at IS NULL) AS member_count,
    count(cm.user_id) FILTER (WHERE cm.left_at IS NULL AND cm.group_role = 'ADMIN') AS admin_count
FROM chat.conversations c LEFT JOIN chat.conversation_members cm USING (conversation_id)
WHERE c.type = 'GROUP'
GROUP BY c.conversation_id;

CREATE VIEW chat.v_user_last_login AS
SELECT user_id, max(logged_in_at) AS last_login_at, count(*) AS successful_login_count
FROM chat.login_history GROUP BY user_id;

CREATE VIEW chat.v_daily_activity AS
SELECT (occurred_at AT TIME ZONE 'UTC')::date AS activity_date_utc,
    count(*) FILTER (WHERE event_type = 'APP_OPEN') AS app_open_count,
    count(*) FILTER (WHERE event_type = 'MESSAGE_SENT') AS message_count,
    count(DISTINCT user_id) AS active_user_count,
    count(DISTINCT user_id) FILTER (WHERE event_type = 'MESSAGE_SENT') AS chatting_user_count,
    count(DISTINCT conversation_id) FILTER (WHERE event_type = 'MESSAGE_SENT') AS chatting_conversation_count
FROM chat.activity_logs GROUP BY (occurred_at AT TIME ZONE 'UTC')::date;

COMMENT ON VIEW chat.v_daily_activity IS 'Daily DISTINCT counts cannot be summed to obtain unique users/conversations for a multi-day range.';
