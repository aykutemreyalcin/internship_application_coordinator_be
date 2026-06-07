package com.internship.coordinator.controller;

import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.dto.CaseSummaryResponse;
import com.internship.coordinator.dto.PageResponse;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.service.CaseNotFoundException;
import com.internship.coordinator.service.CaseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{id}")
    public CaseDetailResponse getCase(@PathVariable UUID id) {
        return caseService.getCase(id);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Void> handleCaseNotFound(CaseNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }
}
