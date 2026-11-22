CREATE TABLE incident_events (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    event_type VARCHAR(32) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_event_stream_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);

INSERT INTO incident_event_stream_lock (id) VALUES (1);
