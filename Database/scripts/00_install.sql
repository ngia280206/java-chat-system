-- Cai MOI vao database TRONG. Database dang co du lieu dung UPDATE_EXISTING_DATABASE.sql.
\set ON_ERROR_STOP on
BEGIN;
\ir 01_schema_types.sql
\ir 02_accounts_social.sql
\ir 03_conversations_keys.sql
\ir 04_messages_activity_reports.sql
\ir 05_group_invitations.sql
\ir 06_user_settings.sql
\ir 07_password_reset_requests.sql
\ir 08_moderation_actions.sql
\ir 09_indexes.sql
\ir 10_integrity_triggers.sql
\ir 11_service_functions.sql
\ir 12_sync_triggers.sql
\ir 13_sync_functions.sql
\ir 14_views.sql
\ir 15_sync_data_permissions.sql
COMMIT;
