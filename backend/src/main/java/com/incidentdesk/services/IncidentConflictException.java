package com.incidentdesk.services;

public class IncidentConflictException extends RuntimeException {

    public IncidentConflictException(Long id) {
        super("Incident " + id + " cannot be changed in its current state.");
    }
}
