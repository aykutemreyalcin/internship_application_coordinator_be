package com.internship.coordinator.repository;

import com.internship.coordinator.model.ValidationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByApplicationCaseCaseId(UUID caseId);
}
