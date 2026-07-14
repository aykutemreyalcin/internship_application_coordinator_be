package com.internship.coordinator.repository;

import com.internship.coordinator.model.ApplicationDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

    List<ApplicationDocument> findByApplicationCaseCaseId(UUID caseId);

    Optional<ApplicationDocument> findByIdAndApplicationCaseCaseId(UUID id, UUID caseId);
}
