package main.java.com.incidentdesk.repositories;

import main.java.com.incidentdesk.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Append-only logs typically only require save and read operations
}
