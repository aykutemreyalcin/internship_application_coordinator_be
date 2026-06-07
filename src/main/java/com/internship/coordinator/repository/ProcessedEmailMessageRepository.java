package com.internship.coordinator.repository;

import com.internship.coordinator.model.ProcessedEmailMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEmailMessageRepository extends JpaRepository<ProcessedEmailMessage, UUID> {

    boolean existsByMessageId(String messageId);
}
