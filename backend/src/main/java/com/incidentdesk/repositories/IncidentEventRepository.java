package com.incidentdesk.repositories;

import java.util.List;
import java.util.Optional;

import com.incidentdesk.entities.IncidentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    List<IncidentEvent> findTop100ByIdGreaterThanOrderByIdAsc(Long id);

    Optional<IncidentEvent> findFirstByOrderByIdDesc();
}
