package com.internship.coordinator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

@Entity
@Table(name = "processed_email_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    private String messageId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(length = 512)
    private String sender;

    @Column(length = 1024)
    private String subject;

    @CreatedDate
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
