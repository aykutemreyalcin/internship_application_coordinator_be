package com.internship.coordinator.repository;

import com.internship.coordinator.model.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findByApplicationCaseCaseIdOrderByTimestampAsc(UUID caseId);
}
