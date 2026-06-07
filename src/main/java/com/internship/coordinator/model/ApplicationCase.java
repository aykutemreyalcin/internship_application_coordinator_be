package com.internship.coordinator.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

@Entity
@Table(name = "application_cases")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CaseStatus status = CaseStatus.NEW;

    private String studentName;

    private String studentId;

    private String companyName;

    private String supervisorName;

    private String supervisorEmail;

    private LocalDate internshipStartDate;

    private LocalDate internshipEndDate;

    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    private Recommendation recommendation;

    @Column(columnDefinition = "TEXT")
    private String recommendationReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "applicationCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "applicationCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ValidationResult> validationResults = new ArrayList<>();

    @OneToMany(mappedBy = "applicationCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AuditLogEntry> auditLogEntries = new ArrayList<>();

    public void addDocument(ApplicationDocument document) {
        documents.add(document);
        document.setApplicationCase(this);
    }

    public void addValidationResult(ValidationResult validationResult) {
        validationResults.add(validationResult);
        validationResult.setApplicationCase(this);
    }

    public void addAuditLogEntry(AuditLogEntry auditLogEntry) {
        auditLogEntries.add(auditLogEntry);
        auditLogEntry.setApplicationCase(this);
    }
}
