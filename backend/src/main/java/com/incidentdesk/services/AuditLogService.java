package com.incidentdesk.services;

import com.incidentdesk.entities.AuditLog;
import com.incidentdesk.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logAction(String actorEmail, String action, String resourceType, Long resourceId, String beforeJson, String afterJson) {
        AuditLog log = new AuditLog();
        log.setActorEmail(actorEmail);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setBeforeStateJson(beforeJson);
        log.setAfterStateJson(afterJson);
        auditLogRepository.save(log);
    }
}