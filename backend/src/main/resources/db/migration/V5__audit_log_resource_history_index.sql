CREATE INDEX idx_audit_logs_resource_history
    ON audit_logs (resource_type, resource_id, timestamp DESC);
