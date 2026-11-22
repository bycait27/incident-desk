package com.incidentdesk.services;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(Long incidentId) {
        super("Incident not found with id: " + incidentId);
    }
}
