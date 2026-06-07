package com.internship.coordinator.repository;

import com.internship.coordinator.model.ApplicationCase;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationCaseRepository
        extends JpaRepository<ApplicationCase, UUID>, JpaSpecificationExecutor<ApplicationCase> {

    long countByDatasetKeyIsNotNull();

    List<ApplicationCase> findByDatasetKeyIsNotNull();

    List<ApplicationCase> findByDatasetKeyStartingWith(String prefix);
}
