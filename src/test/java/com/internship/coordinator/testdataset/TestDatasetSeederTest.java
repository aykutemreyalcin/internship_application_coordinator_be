package com.internship.coordinator.testdataset;

import com.internship.coordinator.agent.CompletenessValidationAgent;
import com.internship.coordinator.agent.UniversityRulesAgent;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.model.ValidationResult;
import com.internship.coordinator.model.ValidationType;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TestDatasetSeederTest {

    @Autowired
    private TestDatasetSeeder testDatasetSeeder;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Autowired
    private CompletenessValidationAgent completenessValidationAgent;

    @Autowired
    private UniversityRulesAgent universityRulesAgent;

    @Test
    void seedCreatesFortyCasesThatBehavePerCategory() {
        var response = testDatasetSeeder.seed();

        assertEquals(40, response.total());
        assertEquals(10, response.countsByCategory().get(TestDatasetCategory.VALID.name()));
        assertEquals(10, response.countsByCategory().get(TestDatasetCategory.INCOMPLETE.name()));
        assertEquals(10, response.countsByCategory().get(TestDatasetCategory.RULE_VIOLATION.name()));
        assertEquals(10, response.countsByCategory().get(TestDatasetCategory.AMBIGUOUS.name()));
        assertEquals(40, applicationCaseRepository.countByDatasetKeyIsNotNull());

        for (ApplicationCase applicationCase : applicationCaseRepository.findAll()) {
            if (applicationCase.getDatasetKey() == null) {
                continue;
            }
            assertCategoryBehavior(applicationCase);
            assertEquals(1, applicationCase.getDocuments().size());
            assertTrue(applicationCase.getDocuments().getFirst().getPageCount() >= 1);
        }
    }

    @Test
    void reseedReplacesExistingDatasetCases() {
        testDatasetSeeder.seed();
        testDatasetSeeder.seed();
        assertEquals(40, applicationCaseRepository.countByDatasetKeyIsNotNull());
    }

    private void assertCategoryBehavior(ApplicationCase applicationCase) {
        ValidationResult completeness = completenessValidationAgent.validate(applicationCase);
        ValidationResult rules = universityRulesAgent.validate(applicationCase);
        TestDatasetCategory category = resolveCategory(applicationCase.getDatasetKey());

        switch (category) {
            case VALID -> {
                assertTrue(completeness.isPassed(), applicationCase.getDatasetKey());
                assertTrue(rules.isPassed(), applicationCase.getDatasetKey());
                assertEquals(Recommendation.APPROVE, applicationCase.getRecommendation());
                assertEquals(CaseStatus.READY_FOR_REVIEW, applicationCase.getStatus());
            }
            case INCOMPLETE -> {
                assertFalse(completeness.isPassed(), applicationCase.getDatasetKey());
                assertEquals(Recommendation.REJECT, applicationCase.getRecommendation());
                assertEquals(CaseStatus.NEW, applicationCase.getStatus());
            }
            case RULE_VIOLATION -> {
                assertTrue(completeness.isPassed(), applicationCase.getDatasetKey());
                assertFalse(rules.isPassed(), applicationCase.getDatasetKey());
                assertEquals(Recommendation.REJECT, applicationCase.getRecommendation());
                assertEquals(CaseStatus.NEW, applicationCase.getStatus());
            }
            case AMBIGUOUS -> {
                assertTrue(completeness.isPassed(), applicationCase.getDatasetKey());
                assertTrue(rules.isPassed(), applicationCase.getDatasetKey());
                assertEquals(Recommendation.CLARIFY, applicationCase.getRecommendation());
                assertEquals(CaseStatus.READY_FOR_REVIEW, applicationCase.getStatus());
            }
        }

        assertStoredValidationMatches(applicationCase, ValidationType.COMPLETENESS, completeness.isPassed());
        assertStoredValidationMatches(applicationCase, ValidationType.RULES, rules.isPassed());
    }

    private void assertStoredValidationMatches(
            ApplicationCase applicationCase, ValidationType type, boolean expectedPassed) {
        ValidationResult stored = applicationCase.getValidationResults().stream()
                .filter(result -> result.getType() == type)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedPassed, stored.isPassed(), applicationCase.getDatasetKey() + " " + type);
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
