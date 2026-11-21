package main.java.com.incidentdesk.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime timestamp;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private Long resourceId;

    @Column(name = "before_state_json", columnDefinition = "TEXT", updatable = false)
    private String beforeStateJson;

    @Column(name = "after_state_json", columnDefinition = "TEXT", updatable = false)
    private String afterStateJson;

    @PrePersist
    protected void onCreate() {
        timestamp = OffsetDateTime.now();
    }

    // Getters only (Append-only audit trail - requirement F6)
    public Long getId() { return id; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getBeforeStateJson() { return beforeStateJson; }
    public void setBeforeStateJson(String beforeStateJson) { this.beforeStateJson = beforeStateJson; }
    public String getAfterStateJson() { return afterStateJson; }
    public void setAfterStateJson(String afterStateJson) { this.afterStateJson = afterStateJson; }
}
