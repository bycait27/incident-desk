package com.incidentdesk.repositories;

import com.incidentdesk.entities.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatus(String status);
    List<Incident> findBySeverity(String severity);
    List<Incident> findByPublishedTrueAndArchivedFalseOrderByCreatedAtDesc();
    List<Incident> findByArchivedFalseOrderByCreatedAtDesc();
    java.util.Optional<Incident> findByIdAndPublishedTrueAndArchivedFalse(Long id);
    java.util.Optional<Incident> findByIdAndArchivedFalse(Long id);
}
