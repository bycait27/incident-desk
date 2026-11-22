ALTER TABLE incidents
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE incident_events
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_incidents_visibility_created_at
    ON incidents (published, archived, created_at DESC);
