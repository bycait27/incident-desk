package com.incidentdesk.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_event_stream_lock")
public class IncidentEventStreamLock {

    @Id
    private Integer id;

    protected IncidentEventStreamLock() {
    }

    public Integer getId() {
        return id;
    }
}
