package com.internship.coordinator.service;

import com.internship.coordinator.dto.AuditLogEntryDto;
import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.ClarificationDraftResponse;
import com.internship.coordinator.dto.CoordinatorDecisionRequest;
import com.internship.coordinator.dto.DocumentSummaryDto;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.dto.SupervisorVerificationDraftResponse;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.agent.ClarificationRequestAgent;
import com.internship.coordinator.agent.CompletenessValidationAgent;
import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.agent.SupervisorVerificationAgent;
import com.internship.coordinator.agent.UniversityRulesAgent;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.ApplicationDocumentRepository;
import com.internship.coordinator.repository.AuditLogEntryRepository;
import com.internship.coordinator.util.CaseStateMachine;
import com.internship.coordinator.util.PdfPageCounter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseService {

    private static final Set<CaseStatus> EXTRACTABLE_STATUSES = EnumSet.of(
            CaseStatus.NEW, CaseStatus.NEEDS_CLARIFICATION, CaseStatus.CLARIFICATION_REQUESTED);

    private static final Set<CaseStatus> RECOMMENDABLE_STATUSES = EnumSet.of(
            CaseStatus.NEW, CaseStatus.NEEDS_CLARIFICATION, CaseStatus.CLARIFICATION_REQUESTED);

    private static final Set<CaseStatus> CLARIFICATION_STATUSES = EnumSet.of(
            CaseStatus.NEW,
            CaseStatus.NEEDS_CLARIFICATION,
            CaseStatus.READY_FOR_REVIEW,
            CaseStatus.CLARIFICATION_REQUESTED);

    private static final Set<CaseStatus> SUPERVISOR_VERIFICATION_STATUSES = EnumSet.of(
            CaseStatus.READY_FOR_REVIEW,
            CaseStatus.PENDING_SUPERVISOR,
            CaseStatus.CLARIFICATION_REQUESTED);

    private final ApplicationCaseRepository applicationCaseRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final AuditLogService auditLogService;
    private final DocumentStorageService documentStorageService;
    private final PdfFileValidator pdfFileValidator;
    private final ObjectProvider<DocumentExtractionAgent> documentExtractionAgentProvider;
    private final ObjectProvider<DecisionRecommendationAgent> decisionRecommendationAgentProvider;
    private final ObjectProvider<ClarificationRequestAgent> clarificationRequestAgentProvider;
    private final ObjectProvider<SupervisorVerificationAgent> supervisorVerificationAgentProvider;
    private final CompletenessValidationAgent completenessValidationAgent;
    private final UniversityRulesAgent universityRulesAgent;
    private final CaseStateMachine caseStateMachine;
    private final PdfPageCounter pdfPageCounter;

    public PageResponse<CaseSummaryResponse> listCases(CaseStatus status, String search, Pageable pageable) {
        Specification<ApplicationCase> specification = CaseSpecifications.withFilters(status, search);
        Page<CaseSummaryResponse> page =
                applicationCaseRepository.findAll(specification, pageable).map(this::toSummary);
        return PageResponse.from(page);
    }

    public CaseDetailResponse getCase(UUID caseId) {
        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        applicationCase.getDocuments().size();
        applicationCase.getValidationResults().forEach(result -> result.getIssues().size());
        return toDetail(applicationCase);
    }

    public List<AuditLogEntryDto> getAuditLog(UUID caseId) {
        if (!applicationCaseRepository.existsById(caseId)) {
            throw new CaseNotFoundException(caseId);
        }
        return auditLogEntryRepository.findByApplicationCaseCaseIdOrderByTimestampAsc(caseId).stream()
                .map(entry -> new AuditLogEntryDto(
                        entry.getId(), entry.getActor(), entry.getAction(), entry.getDetail(), entry.getTimestamp()))
                .toList();
    }

    @Transactional
    public CaseDetailResponse createCaseWithPdf(MultipartFile file) {
        pdfFileValidator.validate(file);

        String fileName = pdfFileValidator.sanitizeFileName(file.getOriginalFilename());

        ApplicationCase applicationCase =
                ApplicationCase.builder().status(CaseStatus.NEW).build();
        ApplicationDocument document = ApplicationDocument.builder()
                .fileName(fileName)
                .storagePath("pending")
                .build();
        applicationCase.addDocument(document);
        applicationCaseRepository.save(applicationCase);

        String storagePath = applicationCase.getCaseId() + "/" + document.getId() + ".pdf";
        try {
            documentStorageService.store(storagePath, file);
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to store uploaded PDF", exception);
        }

        document.setStoragePath(storagePath);
        auditLogService.record(applicationCase, "SYSTEM", "CASE_CREATED", "Uploaded " + fileName);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    public StoredDocument getDocument(UUID caseId, UUID documentId) {
        if (!applicationCaseRepository.existsById(caseId)) {
            throw new CaseNotFoundException(caseId);
        }

        ApplicationDocument document = applicationDocumentRepository
                .findByIdAndApplicationCaseCaseId(documentId, caseId)
                .orElseThrow(() -> new DocumentNotFoundException(caseId, documentId));

        return new StoredDocument(document.getFileName(), documentStorageService.loadAsResource(document.getStoragePath()));
    }

    public ValidationSummaryDto getValidation(UUID caseId) {
        return validateAndPersist(caseId);
    }

    @Transactional
    public ValidationSummaryDto validateAndPersist(UUID caseId) {
        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        runValidations(applicationCase);
        applicationCaseRepository.save(applicationCase);

        return toValidationSummary(applicationCase.getValidationResults());
    }

    @Transactional
    public CaseDetailResponse generateRecommendation(UUID caseId) {
        DecisionRecommendationAgent decisionRecommendationAgent =
                decisionRecommendationAgentProvider.getIfAvailable();
        if (decisionRecommendationAgent == null) {
            throw new CaseRecommendationException(
                    "Recommendation generation is disabled. Enable Vertex AI to generate recommendations.");
        }

        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        if (!RECOMMENDABLE_STATUSES.contains(applicationCase.getStatus())) {
            throw new CaseRecommendationException(
                    "Case status does not allow recommendation: " + applicationCase.getStatus());
        }

        runValidations(applicationCase);
        ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
        var generatedRecommendation = decisionRecommendationAgent.recommend(applicationCase, validation);

        CaseStatus previousStatus = applicationCase.getStatus();
        applicationCase.setRecommendation(generatedRecommendation.recommendation());
        applicationCase.setRecommendationReason(generatedRecommendation.reason());
        auditLogService.record(
                applicationCase,
                "Decision Recommendation Agent",
                "RECOMMENDATION",
                generatedRecommendation.recommendation().name() + ": " + generatedRecommendation.reason());
        auditLogService.recordStatusChange(
                applicationCase, "Decision Recommendation Agent", previousStatus, CaseStatus.READY_FOR_REVIEW);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    @Transactional
    public ClarificationDraftResponse generateClarification(UUID caseId) {
        ClarificationRequestAgent clarificationRequestAgent = clarificationRequestAgentProvider.getIfAvailable();
        if (clarificationRequestAgent == null) {
            throw new CaseClarificationException(
                    "Clarification generation is disabled. Enable Vertex AI to generate clarification drafts.");
        }

        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        if (!CLARIFICATION_STATUSES.contains(applicationCase.getStatus())) {
            throw new CaseClarificationException(
                    "Case status does not allow clarification: " + applicationCase.getStatus());
        }

        runValidations(applicationCase);
        ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
        List<String> topics = clarificationRequestAgent.collectTopics(applicationCase, validation);
        if (topics.isEmpty()) {
            throw new CaseClarificationException("No missing or ambiguous fields require clarification");
        }

        var draft = clarificationRequestAgent.generateDraft(applicationCase, validation, topics);
        CaseStatus previousStatus = applicationCase.getStatus();
        auditLogService.record(
                applicationCase,
                "Clarification Request Agent",
                "CLARIFICATION_DRAFT",
                "Generated draft for " + topics.size() + " topic(s): " + String.join("; ", topics));
        auditLogService.recordStatusChange(
                applicationCase, "Clarification Request Agent", previousStatus, CaseStatus.CLARIFICATION_REQUESTED);
        applicationCaseRepository.save(applicationCase);

        return new ClarificationDraftResponse(
                applicationCase.getCaseId(),
                applicationCase.getStatus(),
                applicationCase.getStudentName(),
                draft.subject(),
                draft.body());
    }

    @Transactional
    public SupervisorVerificationDraftResponse generateSupervisorVerification(UUID caseId) {
        SupervisorVerificationAgent supervisorVerificationAgent =
                supervisorVerificationAgentProvider.getIfAvailable();
        if (supervisorVerificationAgent == null) {
            throw new CaseSupervisorVerificationException(
                    "Supervisor verification is disabled. Enable Vertex AI to generate verification drafts.");
        }

        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        if (!SUPERVISOR_VERIFICATION_STATUSES.contains(applicationCase.getStatus())) {
            throw new CaseSupervisorVerificationException(
                    "Case status does not allow supervisor verification: " + applicationCase.getStatus());
        }
        if (!StringUtils.hasText(applicationCase.getSupervisorEmail())) {
            throw new CaseSupervisorVerificationException("Supervisor email is required for verification");
        }
        if (!StringUtils.hasText(applicationCase.getSupervisorName())) {
            throw new CaseSupervisorVerificationException("Supervisor name is required for verification");
        }

        runValidations(applicationCase);
        ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
        var draft = supervisorVerificationAgent.generateDraft(applicationCase, validation);
        CaseStatus previousStatus = applicationCase.getStatus();
        auditLogService.record(
                applicationCase,
                "Supervisor Verification Agent",
                "SUPERVISOR_VERIFICATION_DRAFT",
                "Generated draft for " + applicationCase.getSupervisorEmail());
        auditLogService.recordStatusChange(
                applicationCase, "Supervisor Verification Agent", previousStatus, CaseStatus.PENDING_SUPERVISOR);
        applicationCaseRepository.save(applicationCase);

        return new SupervisorVerificationDraftResponse(
                applicationCase.getCaseId(),
                applicationCase.getStatus(),
                applicationCase.getSupervisorName(),
                applicationCase.getSupervisorEmail(),
                draft.subject(),
                draft.body());
    }

    @Transactional
    public CaseDetailResponse applyCoordinatorDecision(UUID caseId, CoordinatorDecisionRequest request) {
        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        CaseStatus currentStatus = applicationCase.getStatus();
        if (!caseStateMachine.allowsCoordinatorDecision(currentStatus)) {
            throw new CaseDecisionException("Coordinator decision is not allowed from status: " + currentStatus);
        }

        CaseStatus targetStatus;
        try {
            targetStatus = caseStateMachine.resolveCoordinatorDecision(currentStatus, request.decision());
        } catch (IllegalStateException exception) {
            throw new CaseDecisionException(exception.getMessage());
        }

        auditLogService.record(
                applicationCase,
                "COORDINATOR",
                "DECISION_" + request.decision().name(),
                buildDecisionDetail(request));
        auditLogService.recordStatusChange(applicationCase, "COORDINATOR", currentStatus, targetStatus);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    private String buildDecisionDetail(CoordinatorDecisionRequest request) {
        if (request.note() == null || request.note().isBlank()) {
            return request.decision().name();
        }
        return request.decision().name() + ": " + request.note().trim();
    }

    private String summarizeExtraction(ExtractedApplicationData extractedData) {
        return "studentName="
                + valueOrMissing(extractedData.studentName())
                + ", studentId="
                + valueOrMissing(extractedData.studentId())
                + ", companyName="
                + valueOrMissing(extractedData.companyName());
    }

    private String valueOrMissing(String value) {
        return value == null || value.isBlank() ? "(missing)" : value;
    }

    @Transactional
    public CaseDetailResponse extractCase(UUID caseId) {
        DocumentExtractionAgent documentExtractionAgent = documentExtractionAgentProvider.getIfAvailable();
        if (documentExtractionAgent == null) {
            throw new CaseExtractionException("Document extraction is disabled. Enable Vertex AI to extract documents.");
        }

        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        if (applicationCase.getStatus() == CaseStatus.EXTRACTING) {
            throw new CaseExtractionException("Extraction is already in progress for this case");
        }
        if (!EXTRACTABLE_STATUSES.contains(applicationCase.getStatus())) {
            throw new CaseExtractionException("Case status does not allow extraction: " + applicationCase.getStatus());
        }

        ApplicationDocument document = applicationCase.getDocuments().stream()
                .findFirst()
                .orElseThrow(() -> new CaseExtractionException("Case has no uploaded document to extract"));

        CaseStatus previousStatus = applicationCase.getStatus();
        auditLogService.recordStatusChange(
                applicationCase, "Document Extraction Agent", previousStatus, CaseStatus.EXTRACTING);
        applicationCaseRepository.save(applicationCase);

        byte[] pdfBytes = documentStorageService.readBytes(document.getStoragePath());
        ExtractedApplicationData extractedData = documentExtractionAgent.extract(pdfBytes);

        applyExtractedData(applicationCase, extractedData);
        document.setPageCount(pdfPageCounter.countPages(pdfBytes));
        auditLogService.record(
                applicationCase,
                "Document Extraction Agent",
                "EXTRACTION_COMPLETED",
                summarizeExtraction(extractedData));
        runValidations(applicationCase);
        auditLogService.recordStatusChange(
                applicationCase, "Document Extraction Agent", CaseStatus.EXTRACTING, CaseStatus.NEW);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    private void runValidations(ApplicationCase applicationCase) {
        ValidationResult completeness = completenessValidationAgent.validate(applicationCase);
        ValidationResult rules = universityRulesAgent.validate(applicationCase);
        upsertValidationResult(applicationCase, completeness);
        upsertValidationResult(applicationCase, rules);
        auditLogService.recordValidationResults(applicationCase, completeness, rules);
    }

    private void upsertValidationResult(ApplicationCase applicationCase, ValidationResult validationResult) {
        applicationCase.getValidationResults().removeIf(result -> result.getType() == validationResult.getType());
        applicationCase.addValidationResult(validationResult);
    }

    private void applyExtractedData(ApplicationCase applicationCase, ExtractedApplicationData extractedData) {
        applicationCase.setStudentName(extractedData.studentName());
        applicationCase.setStudentId(extractedData.studentId());
        applicationCase.setFieldOfStudy(extractedData.fieldOfStudy());
        applicationCase.setCompanyName(extractedData.companyName());
        applicationCase.setSupervisorName(extractedData.supervisorName());
        applicationCase.setSupervisorEmail(extractedData.supervisorEmail());
        applicationCase.setInternshipStartDate(parseDate(extractedData.internshipStartDate()));
        applicationCase.setInternshipEndDate(parseDate(extractedData.internshipEndDate()));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ExtractionParseException("Invalid date in extraction response: " + value, exception);
        }
    }

    private CaseSummaryResponse toSummary(ApplicationCase applicationCase) {
        return new CaseSummaryResponse(
                applicationCase.getCaseId(),
                applicationCase.getStatus(),
                applicationCase.getStudentName(),
                applicationCase.getStudentId(),
                applicationCase.getCompanyName(),
                applicationCase.getRecommendation(),
                applicationCase.getCreatedAt(),
                applicationCase.getUpdatedAt());
    }

    private CaseDetailResponse toDetail(ApplicationCase applicationCase) {
        return new CaseDetailResponse(
                applicationCase.getCaseId(),
                applicationCase.getStatus(),
                applicationCase.getStudentName(),
                applicationCase.getStudentId(),
                applicationCase.getCompanyName(),
                applicationCase.getSupervisorName(),
                applicationCase.getSupervisorEmail(),
                applicationCase.getFieldOfStudy(),
                applicationCase.getInternshipStartDate(),
                applicationCase.getInternshipEndDate(),
                applicationCase.getRecommendation(),
                applicationCase.getRecommendationReason(),
                toValidationSummary(applicationCase.getValidationResults()),
                applicationCase.getDocuments().stream().map(this::toDocumentSummary).toList(),
                applicationCase.getCreatedAt(),
                applicationCase.getUpdatedAt());
    }

    private DocumentSummaryDto toDocumentSummary(ApplicationDocument document) {
        return new DocumentSummaryDto(document.getId(), document.getFileName(), document.getPageCount());
    }

    private ValidationSummaryDto toValidationSummary(List<ValidationResult> validationResults) {
        return new ValidationSummaryDto(
                toValidationGroup(validationResults, ValidationType.COMPLETENESS),
                toValidationGroup(validationResults, ValidationType.RULES));
    }

    private ValidationGroupDto toValidationGroup(List<ValidationResult> validationResults, ValidationType type) {
        return validationResults.stream()
                .filter(result -> result.getType() == type)
                .findFirst()
                .map(result -> new ValidationGroupDto(result.isPassed(), mapIssues(result.getIssues())))
                .orElse(new ValidationGroupDto(true, List.of()));
    }

    private List<ValidationIssueDto> mapIssues(List<ValidationIssue> issues) {
        return issues.stream()
                .map(issue -> new ValidationIssueDto(issue.getField(), issue.getMessage(), issue.getSeverity()))
                .toList();
    }
}
