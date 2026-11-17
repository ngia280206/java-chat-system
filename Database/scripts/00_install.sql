-- Run with psql, not as a single query in pgAdmin. See README.md.
\set ON_ERROR_STOP on
BEGIN;
\ir 01_schema_types.sql
\ir 02_accounts_social.sql
\ir 03_conversations_keys.sql
\ir 04_messages_activity_reports.sql
\ir 05_indexes.sql
\ir 06_integrity_triggers.sql
\ir 07_service_functions.sql
\ir 08_views.sql
COMMIT;
\echo 'RELAY: schema chat installed successfully. Demo data is optional.'
