DROP INDEX IF EXISTS idx_audit_logs_resource_history;

DELETE FROM flyway_schema_history
WHERE version = '5'
  AND success = TRUE;
