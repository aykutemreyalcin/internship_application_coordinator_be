package com.internship.coordinator.service;

import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.DocumentSummaryDto;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.dto.ValidationGroupDto;
import com.internship.coordinator.dto.ValidationIssueDto;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.ValidationIssue;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.ApplicationDocumentRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseService {

    private final ApplicationCaseRepository applicationCaseRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final DocumentStorageService documentStorageService;
    private final PdfFileValidator pdfFileValidator;

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
