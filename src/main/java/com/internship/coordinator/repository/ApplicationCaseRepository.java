package com.internship.coordinator.repository;

import com.internship.coordinator.model.ApplicationCase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationCaseRepository extends JpaRepository<ApplicationCase, UUID> {
}
