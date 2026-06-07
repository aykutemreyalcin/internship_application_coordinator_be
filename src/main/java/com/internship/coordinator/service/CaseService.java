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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseService {

    private final ApplicationCaseRepository applicationCaseRepository;

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
