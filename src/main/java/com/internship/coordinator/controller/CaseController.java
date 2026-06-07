package com.internship.coordinator.controller;

import com.internship.coordinator.dto.AuditLogEntryDto;
import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.ClarificationDraftResponse;
import com.internship.coordinator.dto.CoordinatorDecisionRequest;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.dto.SupervisorVerificationDraftResponse;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.service.CaseService;
import com.internship.coordinator.service.StoredDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Validated
public class CaseController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final CaseService caseService;

    @GetMapping
    public PageResponse<CaseSummaryResponse> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return caseService.listCases(
                status,
                search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CaseDetailResponse> createCase(@RequestPart("file") MultipartFile file) {
        CaseDetailResponse response = caseService.createCaseWithPdf(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public CaseDetailResponse getCase(@PathVariable UUID id) {
        return caseService.getCase(id);
    }

    @PostMapping("/{id}/extract")
    public CaseDetailResponse extractCase(@PathVariable UUID id) {
        return caseService.extractCase(id);
    }

    @GetMapping("/{id}/validation")
    public ValidationSummaryDto getValidation(@PathVariable UUID id) {
        return caseService.getValidation(id);
    }

    @GetMapping("/{id}/audit")
    public List<AuditLogEntryDto> getAuditLog(@PathVariable UUID id) {
        return caseService.getAuditLog(id);
    }

    @PostMapping("/{id}/recommendation")
    public CaseDetailResponse generateRecommendation(@PathVariable UUID id) {
        return caseService.generateRecommendation(id);
    }

    @PostMapping("/{id}/clarification")
    public ClarificationDraftResponse generateClarification(@PathVariable UUID id) {
        return caseService.generateClarification(id);
    }

    @PostMapping("/{id}/supervisor-verification")
    public SupervisorVerificationDraftResponse generateSupervisorVerification(@PathVariable UUID id) {
        return caseService.generateSupervisorVerification(id);
    }

    @PostMapping("/{id}/decision")
    public CaseDetailResponse applyCoordinatorDecision(
            @PathVariable UUID id, @Valid @RequestBody CoordinatorDecisionRequest request) {
        return caseService.applyCoordinatorDecision(id, request);
    }

    @GetMapping("/{id}/documents/{docId}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id, @PathVariable UUID docId) {
        StoredDocument storedDocument = caseService.getDocument(id, docId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + storedDocument.fileName() + "\"")
                .body(storedDocument.resource());
    }
}
