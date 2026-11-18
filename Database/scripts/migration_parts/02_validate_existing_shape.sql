-- IF NOT EXISTS khong tu kiem tra cau truc; doan nay kiem tra ro rang truoc khi tao ham.
DO $$
DECLARE e record; v_type oid; v_notnull boolean; v_pk text[];
BEGIN
    FOR e IN SELECT * FROM (VALUES
        ('group_invitations','invitation_id','bigint',true),
        ('group_invitations','conversation_id','bigint',true),
        ('group_invitations','inviter_id','bigint',true),
        ('group_invitations','invitee_id','bigint',true),
        ('group_invitations','status','chat.friend_request_status',true),
        ('group_invitations','created_at','timestamptz',true),
        ('group_invitations','responded_at','timestamptz',false),
        ('user_settings','user_id','bigint',true),
        ('user_settings','desktop_notifications','boolean',true),
        ('user_settings','sound_enabled','boolean',true),
        ('user_settings','message_previews','boolean',true),
        ('user_settings','updated_at','timestamptz',true),
        ('password_reset_requests','reset_id','bigint',true),
        ('password_reset_requests','user_id','bigint',true),
        ('password_reset_requests','token_hash','bytea',true),
        ('password_reset_requests','created_at','timestamptz',true),
        ('password_reset_requests','expires_at','timestamptz',true),
        ('password_reset_requests','verified_at','timestamptz',false),
        ('password_reset_requests','consumed_at','timestamptz',false),
        ('password_reset_requests','revoked_at','timestamptz',false),
        ('password_reset_requests','failed_attempts','integer',true),
        ('password_reset_requests','attempt_limit','smallint',true),
        ('moderation_actions','action_id','bigint',true),
        ('moderation_actions','report_id','bigint',true),
        ('moderation_actions','admin_id','bigint',true),
        ('moderation_actions','action_type','chat.moderation_action_type',true),
        ('moderation_actions','notes','text',true),
        ('moderation_actions','created_at','timestamptz',true)
    ) AS expected(tbl,col,typ,nn) LOOP
        SELECT atttypid,attnotnull INTO v_type,v_notnull FROM pg_attribute
        WHERE attrelid=to_regclass('chat.'||e.tbl) AND attname=e.col AND attnum>0 AND NOT attisdropped;
        IF NOT FOUND OR v_type IS DISTINCT FROM to_regtype(e.typ)::oid OR v_notnull IS DISTINCT FROM e.nn THEN
            RAISE EXCEPTION 'Cau truc chat.%.% khong dung: can % / NOT NULL=%. Cap nhat da rollback.',e.tbl,e.col,e.typ,e.nn;
        END IF;
    END LOOP;
    FOR e IN SELECT * FROM (VALUES('group_invitations','invitation_id'),('user_settings','user_id'),
        ('password_reset_requests','reset_id'),('moderation_actions','action_id')) AS expected(tbl,col) LOOP
        SELECT array_agg(a.attname::text ORDER BY k.ord) INTO v_pk
        FROM pg_constraint p CROSS JOIN LATERAL unnest(p.conkey) WITH ORDINALITY k(num,ord)
        JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=k.num
        WHERE p.conrelid=to_regclass('chat.'||e.tbl) AND p.contype='p';
        IF v_pk IS DISTINCT FROM ARRAY[e.col]::text[] THEN
            RAISE EXCEPTION 'Khoa chinh chat.% khong dung cau truc du kien.',e.tbl;
        END IF;
    END LOOP;
    FOR e IN SELECT * FROM (VALUES
        ('group_invitations','conversation_id','conversations','conversation_id'),
        ('group_invitations','inviter_id','users','user_id'),('group_invitations','invitee_id','users','user_id'),
        ('user_settings','user_id','users','user_id'),('password_reset_requests','user_id','users','user_id'),
        ('moderation_actions','report_id','spam_reports','report_id'),('moderation_actions','admin_id','users','user_id')
    ) AS expected(tbl,col,parent,parent_col) LOOP
        IF NOT EXISTS(SELECT 1 FROM pg_constraint k
            JOIN pg_attribute a ON a.attrelid=k.conrelid AND k.conkey=ARRAY[a.attnum]::smallint[]
            JOIN pg_attribute b ON b.attrelid=k.confrelid AND k.confkey=ARRAY[b.attnum]::smallint[]
            WHERE k.contype='f' AND k.conrelid=to_regclass('chat.'||e.tbl) AND a.attname=e.col
                AND k.confrelid=to_regclass('chat.'||e.parent) AND b.attname=e.parent_col AND k.convalidated) THEN
            RAISE EXCEPTION 'Thieu FK hop le: chat.%.% -> %.%',e.tbl,e.col,e.parent,e.parent_col;
        END IF;
    END LOOP;
END $$;
