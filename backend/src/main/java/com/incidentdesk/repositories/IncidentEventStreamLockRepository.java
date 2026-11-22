package com.incidentdesk.repositories;

import java.util.Optional;

import com.incidentdesk.entities.IncidentEventStreamLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface IncidentEventStreamLockRepository extends JpaRepository<IncidentEventStreamLock, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT streamLock FROM IncidentEventStreamLock streamLock WHERE streamLock.id = 1")
    Optional<IncidentEventStreamLock> lockForEventWrite();
}
