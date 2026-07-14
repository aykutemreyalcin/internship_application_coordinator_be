package com.internship.coordinator.testdataset;

import com.internship.coordinator.agent.CompletenessValidationAgent;
import com.internship.coordinator.agent.UniversityRulesAgent;
import com.internship.coordinator.dto.TestDatasetSeedResponse;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.service.AuditLogService;
import com.internship.coordinator.service.DocumentStorageService;
import com.internship.coordinator.util.ApplicationPdfGenerator;
import com.internship.coordinator.util.PdfPageCounter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestDatasetSeeder {

    private final ApplicationCaseRepository applicationCaseRepository;
    private final DocumentStorageService documentStorageService;
    private final CompletenessValidationAgent completenessValidationAgent;
    private final UniversityRulesAgent universityRulesAgent;
    private final AuditLogService auditLogService;
    private final PdfPageCounter pdfPageCounter;

    @Transactional
    public TestDatasetSeedResponse seed() {
        applicationCaseRepository.deleteAll(applicationCaseRepository.findByDatasetKeyIsNotNull());
        applicationCaseRepository.flush();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TestDatasetCategory category : TestDatasetCategory.values()) {
            counts.put(category.name(), 0);
        }

        for (TestDatasetCaseSpec spec : TestDatasetCatalog.all()) {
            seedCase(spec);
            counts.merge(spec.category().name(), 1, Integer::sum);
        }

        return new TestDatasetSeedResponse(TestDatasetCatalog.all().size(), counts);
    }

    private void seedCase(TestDatasetCaseSpec spec) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(CaseStatus.NEW)
                .datasetKey(spec.key())
                .studentName(spec.studentName())
                .studentId(spec.studentId())
                .fieldOfStudy(spec.fieldOfStudy())
                .companyName(spec.companyName())
                .supervisorName(spec.supervisorName())
                .supervisorEmail(spec.supervisorEmail())
                .internshipStartDate(spec.internshipStartDate())
                .internshipEndDate(spec.internshipEndDate())
                .build();

        ApplicationDocument document = ApplicationDocument.builder()
                .fileName(spec.fileName())
                .storagePath("pending")
                .build();
        applicationCase.addDocument(document);
        applicationCaseRepository.save(applicationCase);

        byte[] pdfBytes = ApplicationPdfGenerator.generate(
                spec.studentName(),
                spec.studentId(),
                spec.fieldOfStudy(),
                spec.companyName(),
                spec.supervisorName(),
                spec.supervisorEmail(),
                spec.internshipStartDate(),
                spec.internshipEndDate());

        String storagePath = applicationCase.getCaseId() + "/" + document.getId() + ".pdf";
        try {
            documentStorageService.storeBytes(storagePath, pdfBytes);
        } catch (IOException exception) {
            throw new TestDatasetSeedException("Failed to store sample PDF for " + spec.key(), exception);
        }

        document.setStoragePath(storagePath);
        document.setPageCount(pdfPageCounter.countPages(pdfBytes));

        ValidationResult completeness = completenessValidationAgent.validate(applicationCase);
        ValidationResult rules = universityRulesAgent.validate(applicationCase);
        applicationCase.addValidationResult(completeness);
        applicationCase.addValidationResult(rules);
        auditLogService.recordValidationResults(applicationCase, completeness, rules);

        applyCategoryOutcome(applicationCase, spec, completeness, rules);
        auditLogService.record(
                applicationCase,
                "Test Dataset Seeder",
                "DATASET_SEEDED",
                spec.category().name() + ": " + spec.key());
        applicationCaseRepository.save(applicationCase);
    }

    private void applyCategoryOutcome(
            ApplicationCase applicationCase,
            TestDatasetCaseSpec spec,
            ValidationResult completeness,
            ValidationResult rules) {
        switch (spec.category()) {
            case VALID -> {
                applicationCase.setRecommendation(Recommendation.APPROVE);
                applicationCase.setRecommendationReason("All required fields are present; duration complies with rules.");
                applicationCase.setStatus(CaseStatus.READY_FOR_REVIEW);
            }
            case INCOMPLETE -> {
                applicationCase.setRecommendation(Recommendation.REJECT);
                applicationCase.setRecommendationReason(buildCompletenessFailureReason(completeness));
                applicationCase.setStatus(CaseStatus.NEW);
            }
            case RULE_VIOLATION -> {
                applicationCase.setRecommendation(Recommendation.REJECT);
                applicationCase.setRecommendationReason(buildRulesFailureReason(rules));
                applicationCase.setStatus(CaseStatus.NEW);
            }
            case AMBIGUOUS -> {
                applicationCase.setRecommendation(Recommendation.CLARIFY);
                applicationCase.setRecommendationReason(spec.ambiguityNote());
                applicationCase.setStatus(CaseStatus.READY_FOR_REVIEW);
            }
        }
    }

    private String buildCompletenessFailureReason(ValidationResult completeness) {
        if (completeness.isPassed()) {
            return "Completeness validation failed";
        }
        return "Completeness validation failed: "
                + completeness.getIssues().stream()
                        .map(issue -> issue.getField() + ": " + issue.getMessage())
                        .reduce((left, right) -> left + "; " + right)
                        .orElse("unknown issue");
    }

    private String buildRulesFailureReason(ValidationResult rules) {
        if (rules.isPassed()) {
            return "University rules validation failed";
        }
        return "University rules validation failed: "
                + rules.getIssues().stream()
                        .map(issue -> issue.getField() + ": " + issue.getMessage())
                        .reduce((left, right) -> left + "; " + right)
                        .orElse("unknown issue");
    }

    public Map<String, Integer> summarizeLoadedDataset() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TestDatasetCategory category : TestDatasetCategory.values()) {
            counts.put(category.name(), 0);
        }

        List<ApplicationCase> cases = applicationCaseRepository.findAll().stream()
                .filter(applicationCase -> applicationCase.getDatasetKey() != null)
                .toList();

        for (ApplicationCase applicationCase : cases) {
            TestDatasetCategory category = resolveCategory(applicationCase.getDatasetKey());
            counts.merge(category.name(), 1, Integer::sum);
        }

        return counts;
    }

    private TestDatasetCategory resolveCategory(String datasetKey) {
        if (datasetKey.startsWith("valid-")) {
            return TestDatasetCategory.VALID;
        }
        if (datasetKey.startsWith("incomplete-")) {
            return TestDatasetCategory.INCOMPLETE;
        }
        if (datasetKey.startsWith("rules-")) {
            return TestDatasetCategory.RULE_VIOLATION;
        }
        return TestDatasetCategory.AMBIGUOUS;
    }
}
