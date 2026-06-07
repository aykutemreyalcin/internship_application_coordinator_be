package com.internship.coordinator.controller;

import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.service.CaseNotFoundException;
import com.internship.coordinator.service.CaseService;
import com.internship.coordinator.service.CaseExtractionException;
import com.internship.coordinator.service.DocumentNotFoundException;
import com.internship.coordinator.service.ExtractionParseException;
import com.internship.coordinator.service.GeminiException;
import com.internship.coordinator.service.InvalidFileException;
import com.internship.coordinator.service.StoredDocument;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final CaseService caseService;

    @GetMapping
    public PageResponse<CaseSummaryResponse> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Void> handleCaseNotFound(CaseNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Void> handleDocumentNotFound(DocumentNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Void> handleInvalidFile(InvalidFileException exception) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(CaseExtractionException.class)
    public ResponseEntity<Void> handleCaseExtraction(CaseExtractionException exception) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({ExtractionParseException.class, GeminiException.class})
    public ResponseEntity<Void> handleExtractionFailure(RuntimeException exception) {
        return ResponseEntity.internalServerError().build();
    }
}
