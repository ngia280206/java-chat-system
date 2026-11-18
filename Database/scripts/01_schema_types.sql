-- PostgreSQL 16+. Initial migration: intentionally fails if chat already exists.
DO $$
BEGIN
    IF current_setting('server_version_num')::integer < 160000 THEN
        RAISE EXCEPTION 'PostgreSQL 16 or newer is required';
    END IF;
END $$;

CREATE SCHEMA chat;
COMMENT ON SCHEMA chat IS 'RELAY chat system; initial PostgreSQL implementation of the 14-table ERD.';
REVOKE ALL ON SCHEMA chat FROM PUBLIC;
ALTER DEFAULT PRIVILEGES
REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TYPE chat.system_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE chat.user_status AS ENUM ('ACTIVE', 'LOCKED', 'DELETED');
CREATE TYPE chat.friend_request_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED');
CREATE TYPE chat.conversation_type AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE chat.encryption_mode AS ENUM ('NONE', 'E2EE');
CREATE TYPE chat.group_role AS ENUM ('ADMIN', 'MEMBER');
CREATE TYPE chat.activity_event_type AS ENUM ('APP_OPEN', 'MESSAGE_SENT');
CREATE TYPE chat.report_status AS ENUM ('PENDING', 'REVIEWED', 'DISMISSED');
