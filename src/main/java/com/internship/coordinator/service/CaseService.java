package com.internship.coordinator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.internship.coordinator.agent.ClarificationRequestAgent;
import com.internship.coordinator.agent.CompletenessValidationAgent;
import com.internship.coordinator.agent.DecisionRecommendationAgent;
import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.agent.InternshipJournalExtractionAgent;
import com.internship.coordinator.agent.LearningOutcomesReportExtractionAgent;
import com.internship.coordinator.agent.SupervisorVerificationAgent;
import com.internship.coordinator.agent.UniversityRulesAgent;
import com.internship.coordinator.dto.AuditLogEntryDto;
import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.ClarificationDraftResponse;
import com.internship.coordinator.dto.CoordinatorDecisionRequest;
import com.internship.coordinator.dto.DocumentSummaryDto;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.dto.SupervisorVerificationDraftResponse;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.CaseType;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.ApplicationDocumentRepository;
import com.internship.coordinator.repository.AuditLogEntryRepository;
import com.internship.coordinator.util.CaseStateMachine;
import com.internship.coordinator.util.DocxTextExtractor;
import com.internship.coordinator.util.PdfPageCounter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
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
    private final DocumentFileValidator documentFileValidator;
    private final PdfFileValidator pdfFileValidator;
    private final ExtractedPayloadService extractedPayloadService;
    private final DocxTextExtractor docxTextExtractor;
    private final ObjectProvider<DocumentExtractionAgent> documentExtractionAgentProvider;
    private final ObjectProvider<LearningOutcomesReportExtractionAgent> learningOutcomesReportExtractionAgentProvider;
    private final ObjectProvider<InternshipJournalExtractionAgent> internshipJournalExtractionAgentProvider;
    private final ObjectProvider<DecisionRecommendationAgent> decisionRecommendationAgentProvider;
    private final ObjectProvider<ClarificationRequestAgent> clarificationRequestAgentProvider;
    private final ObjectProvider<SupervisorVerificationAgent> supervisorVerificationAgentProvider;
    private final CompletenessValidationAgent completenessValidationAgent;
    private final UniversityRulesAgent universityRulesAgent;
    private final CaseStateMachine caseStateMachine;
    private final PdfPageCounter pdfPageCounter;

    public PageResponse<CaseSummaryResponse> listCases(
            CaseStatus status, CaseType caseType, String search, Pageable pageable) {
        Specification<ApplicationCase> specification = CaseSpecifications.withFilters(status, caseType, search);
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
        return createCaseWithDocument(file, CaseType.APPLICATION);
    }

    @Transactional
    public CaseDetailResponse createCaseWithDocument(MultipartFile file, CaseType caseType) {
        CaseType resolvedCaseType = caseType == null ? CaseType.APPLICATION : caseType;
        documentFileValidator.validate(file);
        String fileName = documentFileValidator.sanitizeFileName(file.getOriginalFilename());
        String contentType = documentFileValidator.resolveContentType(fileName);
        String extension = documentFileValidator.resolveStorageExtension(fileName);
        return createCaseFromStoredDocument(
                fileName,
                contentType,
                extension,
                file,
                resolvedCaseType,
                "SYSTEM",
                "CASE_CREATED",
                "Uploaded " + fileName + " (" + resolvedCaseType + ")");
    }

    @Transactional
    public CaseDetailResponse createCaseFromEmail(
            String fileName,
            byte[] pdfBytes,
            String sender,
            String subject,
            String messageId) {
        pdfFileValidator.validateBytes(pdfBytes, fileName);
        String sanitizedFileName = pdfFileValidator.sanitizeFileName(fileName);
        String auditDetail = "messageId="
                + messageId
                + ", from="
                + sender
                + ", subject="
                + (subject == null ? "(no subject)" : subject)
                + ", file="
                + sanitizedFileName;
        return createCaseFromStoredDocumentBytes(
                sanitizedFileName,
                MediaType.APPLICATION_PDF_VALUE,
                ".pdf",
                pdfBytes,
                CaseType.APPLICATION,
                "Email Intake Agent",
                "EMAIL_INTAKE",
                auditDetail);
    }

    @Transactional
    public void tryGenerateRecommendation(UUID caseId) {
        if (decisionRecommendationAgentProvider.getIfAvailable() == null) {
            return;
        }
        try {
            generateRecommendation(caseId);
        } catch (CaseRecommendationException exception) {
            // Pipeline continues even if recommendation cannot be generated yet.
        }
    }

    private CaseDetailResponse createCaseFromStoredDocument(
            String fileName,
            String contentType,
            String extension,
            MultipartFile file,
            CaseType caseType,
            String auditActor,
            String auditAction,
            String auditDetail) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.NEW)
                .caseType(caseType)
                .build();
        ApplicationDocument document = ApplicationDocument.builder()
                .fileName(fileName)
                .storagePath("pending")
                .contentType(contentType)
                .build();
        applicationCase.addDocument(document);
        applicationCaseRepository.save(applicationCase);

        String storagePath = applicationCase.getCaseId() + "/" + document.getId() + extension;
        try {
            documentStorageService.store(storagePath, file);
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to store uploaded document", exception);
        }

        document.setStoragePath(storagePath);
        auditLogService.record(applicationCase, auditActor, auditAction, auditDetail);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    private CaseDetailResponse createCaseFromStoredDocumentBytes(
            String fileName,
            String contentType,
            String extension,
            byte[] bytes,
            CaseType caseType,
            String auditActor,
            String auditAction,
            String auditDetail) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.NEW)
                .caseType(caseType)
                .build();
        ApplicationDocument document = ApplicationDocument.builder()
                .fileName(fileName)
                .storagePath("pending")
                .contentType(contentType)
                .build();
        applicationCase.addDocument(document);
        applicationCaseRepository.save(applicationCase);

        String storagePath = applicationCase.getCaseId() + "/" + document.getId() + extension;
        try {
            documentStorageService.storeBytes(storagePath, bytes);
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to store email PDF attachment", exception);
        }

        document.setStoragePath(storagePath);
        auditLogService.record(applicationCase, auditActor, auditAction, auditDetail);
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

        return new StoredDocument(
                document.getFileName(),
                document.getContentType() != null ? document.getContentType() : MediaType.APPLICATION_PDF_VALUE,
                documentStorageService.loadAsResource(document.getStoragePath()));
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

        long startedNanos = System.nanoTime();
        log.info("agent.step.start caseId={} step=recommendation", caseId);
        GeminiCallContext.clear();
        try {
            runValidations(applicationCase);
            ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
            var generatedRecommendation = decisionRecommendationAgent.recommend(applicationCase, validation);
            GeminiCallMetrics geminiMetrics = GeminiCallContext.consume();

            CaseStatus previousStatus = applicationCase.getStatus();
            applicationCase.setRecommendation(generatedRecommendation.recommendation());
            applicationCase.setRecommendationReason(generatedRecommendation.reason());
            auditLogService.record(
                    applicationCase,
                    "Decision Recommendation Agent",
                    "RECOMMENDATION",
                    GeminiCallMetrics.appendToDetail(
                            generatedRecommendation.recommendation().name()
                                    + ": "
                                    + generatedRecommendation.reason(),
                            geminiMetrics));
            auditLogService.recordStatusChange(
                    applicationCase, "Decision Recommendation Agent", previousStatus, CaseStatus.READY_FOR_REVIEW);
            applicationCaseRepository.save(applicationCase);

            log.info(
                    "agent.step.success caseId={} step=recommendation durationMs={} recommendation={}",
                    caseId,
                    elapsedMs(startedNanos),
                    generatedRecommendation.recommendation());
            return toDetail(applicationCase);
        } catch (RuntimeException exception) {
            log.warn(
                    "agent.step.failed caseId={} step=recommendation durationMs={} error={}",
                    caseId,
                    elapsedMs(startedNanos),
                    exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public ClarificationDraftResponse generateClarification(UUID caseId) {
        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        ensureApplicationOnlyCase(applicationCase, "Clarification emails are only available for application cases");

        ClarificationRequestAgent clarificationRequestAgent = clarificationRequestAgentProvider.getIfAvailable();
        if (clarificationRequestAgent == null) {
            throw new CaseClarificationException(
                    "Clarification generation is disabled. Enable Vertex AI to generate clarification drafts.");
        }

        if (!CLARIFICATION_STATUSES.contains(applicationCase.getStatus())) {
            throw new CaseClarificationException(
                    "Case status does not allow clarification: " + applicationCase.getStatus());
        }

        long startedNanos = System.nanoTime();
        log.info("agent.step.start caseId={} step=clarification", caseId);
        GeminiCallContext.clear();
        try {
            runValidations(applicationCase);
            ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
            List<String> topics = clarificationRequestAgent.collectTopics(applicationCase, validation);
            if (topics.isEmpty()) {
                throw new CaseClarificationException("No missing or ambiguous fields require clarification");
            }

            var draft = clarificationRequestAgent.generateDraft(applicationCase, validation, topics);
            GeminiCallMetrics geminiMetrics = GeminiCallContext.consume();
            CaseStatus previousStatus = applicationCase.getStatus();
            auditLogService.record(
                    applicationCase,
                    "Clarification Request Agent",
                    "CLARIFICATION_DRAFT",
                    GeminiCallMetrics.appendToDetail(
                            "Generated draft for " + topics.size() + " topic(s): " + String.join("; ", topics),
                            geminiMetrics));
            auditLogService.recordStatusChange(
                    applicationCase,
                    "Clarification Request Agent",
                    previousStatus,
                    CaseStatus.CLARIFICATION_REQUESTED);
            applicationCaseRepository.save(applicationCase);

            log.info(
                    "agent.step.success caseId={} step=clarification durationMs={} topics={}",
                    caseId,
                    elapsedMs(startedNanos),
                    topics.size());
            return new ClarificationDraftResponse(
                    applicationCase.getCaseId(),
                    applicationCase.getStatus(),
                    applicationCase.getStudentName(),
                    draft.subject(),
                    draft.body());
        } catch (RuntimeException exception) {
            log.warn(
                    "agent.step.failed caseId={} step=clarification durationMs={} error={}",
                    caseId,
                    elapsedMs(startedNanos),
                    exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public SupervisorVerificationDraftResponse generateSupervisorVerification(UUID caseId) {
        ApplicationCase applicationCase = applicationCaseRepository
                .findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        ensureApplicationOnlyCase(applicationCase, "Supervisor verification is only available for application cases");

        SupervisorVerificationAgent supervisorVerificationAgent =
                supervisorVerificationAgentProvider.getIfAvailable();
        if (supervisorVerificationAgent == null) {
            throw new CaseSupervisorVerificationException(
                    "Supervisor verification is disabled. Enable Vertex AI to generate verification drafts.");
        }

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

        long startedNanos = System.nanoTime();
        log.info("agent.step.start caseId={} step=supervisor_verification", caseId);
        GeminiCallContext.clear();
        try {
            runValidations(applicationCase);
            ValidationSummaryDto validation = toValidationSummary(applicationCase.getValidationResults());
            var draft = supervisorVerificationAgent.generateDraft(applicationCase, validation);
            GeminiCallMetrics geminiMetrics = GeminiCallContext.consume();
            CaseStatus previousStatus = applicationCase.getStatus();
            auditLogService.record(
                    applicationCase,
                    "Supervisor Verification Agent",
                    "SUPERVISOR_VERIFICATION_DRAFT",
                    GeminiCallMetrics.appendToDetail(
                            "Generated draft for " + applicationCase.getSupervisorEmail(), geminiMetrics));
            auditLogService.recordStatusChange(
                    applicationCase,
                    "Supervisor Verification Agent",
                    previousStatus,
                    CaseStatus.PENDING_SUPERVISOR);
            applicationCaseRepository.save(applicationCase);

            log.info(
                    "agent.step.success caseId={} step=supervisor_verification durationMs={}",
                    caseId,
                    elapsedMs(startedNanos));
            return new SupervisorVerificationDraftResponse(
                    applicationCase.getCaseId(),
                    applicationCase.getStatus(),
                    applicationCase.getSupervisorName(),
                    applicationCase.getSupervisorEmail(),
                    draft.subject(),
                    draft.body());
        } catch (RuntimeException exception) {
            log.warn(
                    "agent.step.failed caseId={} step=supervisor_verification durationMs={} error={}",
                    caseId,
                    elapsedMs(startedNanos),
                    exception.getMessage());
            throw exception;
        }
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

        CaseStatus targetStatus = resolveDecisionTarget(applicationCase, request);

        auditLogService.record(
                applicationCase,
                "COORDINATOR",
                "DECISION_" + request.decision().name(),
                buildDecisionDetail(request));
        auditLogService.recordStatusChange(applicationCase, "COORDINATOR", currentStatus, targetStatus);
        applicationCaseRepository.save(applicationCase);

        return toDetail(applicationCase);
    }

    private CaseStatus resolveDecisionTarget(ApplicationCase applicationCase, CoordinatorDecisionRequest request) {
        CaseStatus currentStatus = applicationCase.getStatus();
        if (request.decision() == Recommendation.CLARIFY
                && extractedPayloadService.isDocumentCase(applicationCase)) {
            if (currentStatus == CaseStatus.APPROVED || currentStatus == CaseStatus.REJECTED) {
                throw new CaseDecisionException("Case is already in a terminal status: " + currentStatus);
            }
            if (!caseStateMachine.allowsCoordinatorDecision(currentStatus)) {
                throw new CaseDecisionException("Coordinator decision is not allowed from status: " + currentStatus);
            }
            return CaseStatus.CLARIFICATION_REQUESTED;
        }

        try {
            return caseStateMachine.resolveCoordinatorDecision(currentStatus, request.decision());
        } catch (IllegalStateException exception) {
            throw new CaseDecisionException(exception.getMessage());
        }
    }

    private void ensureApplicationOnlyCase(ApplicationCase applicationCase, String message) {
        CaseType caseType = applicationCase.getCaseType() == null ? CaseType.APPLICATION : applicationCase.getCaseType();
        if (caseType != CaseType.APPLICATION) {
            throw new InvalidCaseTypeException(message);
        }
    }

    private String buildDecisionDetail(CoordinatorDecisionRequest request) {
        if (request.note() == null || request.note().isBlank()) {
            return request.decision().name();
        }
        return request.decision().name() + ": " + request.note().trim();
    }

    @Transactional
    public CaseDetailResponse extractCase(UUID caseId) {
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
                applicationCase, resolveExtractionActor(applicationCase), previousStatus, CaseStatus.EXTRACTING);
        applicationCaseRepository.save(applicationCase);

        long startedNanos = System.nanoTime();
        log.info("agent.step.start caseId={} step=extraction caseType={}", caseId, applicationCase.getCaseType());
        GeminiCallContext.clear();
        try {
            byte[] documentBytes = documentStorageService.readBytes(document.getStoragePath());
            ExtractionSummary extractionSummary = extractDocumentData(applicationCase, document, documentBytes);
            if (extractionSummary.applicationData() != null) {
                applyExtractedData(applicationCase, extractionSummary.applicationData());
            }
            GeminiCallMetrics geminiMetrics = GeminiCallContext.consume();

            if (isPdfDocument(document)) {
                document.setPageCount(pdfPageCounter.countPages(documentBytes));
            }

            auditLogService.record(
                    applicationCase,
                    resolveExtractionActor(applicationCase),
                    "EXTRACTION_COMPLETED",
                    GeminiCallMetrics.appendToDetail(extractionSummary.detail(), geminiMetrics));
            runValidations(applicationCase);
            auditLogService.recordStatusChange(
                    applicationCase, resolveExtractionActor(applicationCase), CaseStatus.EXTRACTING, CaseStatus.NEW);
            applicationCaseRepository.save(applicationCase);

            log.info(
                    "agent.step.success caseId={} step=extraction durationMs={}",
                    caseId,
                    elapsedMs(startedNanos));
            return toDetail(applicationCase);
        } catch (RuntimeException exception) {
            GeminiCallContext.consume();
            log.warn(
                    "agent.step.failed caseId={} step=extraction durationMs={} error={}",
                    caseId,
                    elapsedMs(startedNanos),
                    exception.getMessage());
            throw exception;
        }
    }

    private ExtractionSummary extractDocumentData(
            ApplicationCase applicationCase, ApplicationDocument document, byte[] documentBytes) {
        CaseType caseType = applicationCase.getCaseType() == null ? CaseType.APPLICATION : applicationCase.getCaseType();
        return switch (caseType) {
            case APPLICATION -> extractApplication(documentBytes);
            case LEARNING_OUTCOMES_REPORT -> extractLearningOutcomesReport(applicationCase, document, documentBytes);
            case INTERNSHIP_JOURNAL -> extractInternshipJournal(applicationCase, document, documentBytes);
        };
    }

    private ExtractionSummary extractApplication(byte[] pdfBytes) {
        DocumentExtractionAgent documentExtractionAgent = documentExtractionAgentProvider.getIfAvailable();
        if (documentExtractionAgent == null) {
            throw new CaseExtractionException("Document extraction is disabled. Enable Vertex AI to extract documents.");
        }
        ExtractedApplicationData extractedData = documentExtractionAgent.extract(pdfBytes);
        return new ExtractionSummary(extractedData, null, summarizeApplicationExtraction(extractedData));
    }

    private ExtractionSummary extractLearningOutcomesReport(
            ApplicationCase applicationCase, ApplicationDocument document, byte[] documentBytes) {
        LearningOutcomesReportExtractionAgent agent = learningOutcomesReportExtractionAgentProvider.getIfAvailable();
        if (agent == null) {
            throw new CaseExtractionException("Document extraction is disabled. Enable Vertex AI to extract documents.");
        }
        ExtractedLearningOutcomesReportData extractedData = isPdfDocument(document)
                ? agent.extractFromPdf(documentBytes)
                : extractReportFromDocx(agent, documentBytes);
        applyReportExtractedData(applicationCase, extractedData);
        return new ExtractionSummary(null, extractedData, summarizeReportExtraction(extractedData));
    }

    private ExtractionSummary extractInternshipJournal(
            ApplicationCase applicationCase, ApplicationDocument document, byte[] documentBytes) {
        InternshipJournalExtractionAgent agent = internshipJournalExtractionAgentProvider.getIfAvailable();
        if (agent == null) {
            throw new CaseExtractionException("Document extraction is disabled. Enable Vertex AI to extract documents.");
        }
        ExtractedInternshipJournalData extractedData = isPdfDocument(document)
                ? agent.extractFromPdf(documentBytes)
                : extractJournalFromDocx(agent, documentBytes);
        applyJournalExtractedData(applicationCase, extractedData);
        return new ExtractionSummary(null, extractedData, summarizeJournalExtraction(extractedData));
    }

    private ExtractedLearningOutcomesReportData extractReportFromDocx(
            LearningOutcomesReportExtractionAgent agent, byte[] documentBytes) {
        try {
            return agent.extractFromDocx(documentBytes);
        } catch (RuntimeException multimodalFailure) {
            log.warn(
                    "DOCX multimodal report extraction failed, falling back to text: {}",
                    multimodalFailure.getMessage());
            return agent.extractFromText(docxTextExtractor.extractText(documentBytes));
        }
    }

    private ExtractedInternshipJournalData extractJournalFromDocx(
            InternshipJournalExtractionAgent agent, byte[] documentBytes) {
        try {
            return agent.extractFromDocx(documentBytes);
        } catch (RuntimeException multimodalFailure) {
            log.warn(
                    "DOCX multimodal journal extraction failed, falling back to text: {}",
                    multimodalFailure.getMessage());
            return agent.extractFromText(docxTextExtractor.extractText(documentBytes));
        }
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

    private void applyReportExtractedData(
            ApplicationCase applicationCase, ExtractedLearningOutcomesReportData extractedData) {
        applicationCase.setStudentName(extractedData.studentName());
        applicationCase.setStudentId(extractedData.studentId());
        applicationCase.setCompanyName(extractedData.hostCompanyOrEmployer());
        applicationCase.setSupervisorName(extractedData.supervisorName());
        applicationCase.setInternshipStartDate(parseDate(extractedData.internshipStartDate()));
        applicationCase.setInternshipEndDate(parseDate(extractedData.internshipEndDate()));
        extractedPayloadService.storeReportPayload(applicationCase, extractedData);
    }

    private void applyJournalExtractedData(
            ApplicationCase applicationCase, ExtractedInternshipJournalData extractedData) {
        applicationCase.setStudentName(extractedData.studentName());
        applicationCase.setStudentId(extractedData.studentId());
        applicationCase.setFieldOfStudy(extractedData.fieldOfStudy());
        applicationCase.setCompanyName(extractedData.companyName());
        applicationCase.setSupervisorName(extractedData.companySupervisorName());
        applicationCase.setInternshipStartDate(parseDate(extractedData.internshipStartDate()));
        applicationCase.setInternshipEndDate(parseDate(extractedData.internshipEndDate()));
        extractedPayloadService.storeJournalPayload(applicationCase, extractedData);
    }

    private boolean isPdfDocument(ApplicationDocument document) {
        if (document.getContentType() != null
                && document.getContentType().equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) {
            return true;
        }
        return document.getFileName() != null
                && document.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String resolveExtractionActor(ApplicationCase applicationCase) {
        CaseType caseType = applicationCase.getCaseType() == null ? CaseType.APPLICATION : applicationCase.getCaseType();
        return switch (caseType) {
            case LEARNING_OUTCOMES_REPORT -> "Learning Outcomes Report Extraction Agent";
            case INTERNSHIP_JOURNAL -> "Internship Journal Extraction Agent";
            case APPLICATION -> "Document Extraction Agent";
        };
    }

    private String summarizeApplicationExtraction(ExtractedApplicationData extractedData) {
        return "studentName="
                + valueOrMissing(extractedData.studentName())
                + ", studentId="
                + valueOrMissing(extractedData.studentId())
                + ", companyName="
                + valueOrMissing(extractedData.companyName());
    }

    private String summarizeReportExtraction(ExtractedLearningOutcomesReportData extractedData) {
        int outcomes = extractedData.learningOutcomes() == null ? 0 : extractedData.learningOutcomes().size();
        return "studentName="
                + valueOrMissing(extractedData.studentName())
                + ", studentId="
                + valueOrMissing(extractedData.studentId())
                + ", hostCompany="
                + valueOrMissing(extractedData.hostCompanyOrEmployer())
                + ", outcomes="
                + outcomes;
    }

    private String summarizeJournalExtraction(ExtractedInternshipJournalData extractedData) {
        int weeks = extractedData.weeklyEntries() == null ? 0 : extractedData.weeklyEntries().size();
        return "studentName="
                + valueOrMissing(extractedData.studentName())
                + ", studentId="
                + valueOrMissing(extractedData.studentId())
                + ", companyName="
                + valueOrMissing(extractedData.companyName())
                + ", weeks="
                + weeks;
    }

    private String valueOrMissing(String value) {
        return value == null || value.isBlank() ? "(missing)" : value;
    }

    private void runValidations(ApplicationCase applicationCase) {
        long startedNanos = System.nanoTime();
        UUID caseId = applicationCase.getCaseId();
        log.info("agent.step.start caseId={} step=validation", caseId);
        ValidationResult completeness = completenessValidationAgent.validate(applicationCase);
        ValidationResult rules = universityRulesAgent.validate(applicationCase);
        upsertValidationResult(applicationCase, completeness);
        upsertValidationResult(applicationCase, rules);
        auditLogService.recordValidationResults(applicationCase, completeness, rules);
        log.info(
                "agent.step.success caseId={} step=validation durationMs={} completenessPassed={} rulesPassed={}",
                caseId,
                elapsedMs(startedNanos),
                completeness.isPassed(),
                rules.isPassed());
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private void upsertValidationResult(ApplicationCase applicationCase, ValidationResult validationResult) {
        applicationCase.getValidationResults().removeIf(result -> result.getType() == validationResult.getType());
        applicationCase.addValidationResult(validationResult);
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
                applicationCase.getCaseType() == null ? CaseType.APPLICATION : applicationCase.getCaseType(),
                applicationCase.getStatus(),
                applicationCase.getStudentName(),
                applicationCase.getStudentId(),
                applicationCase.getCompanyName(),
                applicationCase.getRecommendation(),
                applicationCase.getCreatedAt(),
                applicationCase.getUpdatedAt());
    }

    private CaseDetailResponse toDetail(ApplicationCase applicationCase) {
        JsonNode extractedPayload = readExtractedPayloadNode(applicationCase);
        return new CaseDetailResponse(
                applicationCase.getCaseId(),
                applicationCase.getCaseType() == null ? CaseType.APPLICATION : applicationCase.getCaseType(),
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
                extractedPayload,
                toValidationSummary(applicationCase.getValidationResults()),
                applicationCase.getDocuments().stream().map(this::toDocumentSummary).toList(),
                applicationCase.getCreatedAt(),
                applicationCase.getUpdatedAt());
    }

    private JsonNode readExtractedPayloadNode(ApplicationCase applicationCase) {
        JsonNode node = extractedPayloadService.readPayloadNode(applicationCase);
        return node.isEmpty() ? null : node;
    }

    private DocumentSummaryDto toDocumentSummary(ApplicationDocument document) {
        return new DocumentSummaryDto(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getPageCount());
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

    private record ExtractionSummary(
            ExtractedApplicationData applicationData,
            Object documentPayload,
            String detail) {}
}
