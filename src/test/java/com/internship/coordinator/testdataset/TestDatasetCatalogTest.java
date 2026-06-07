package com.internship.coordinator.testdataset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatasetCatalogTest {

    @Test
    void catalogContainsFortyCasesAcrossFourCategories() {
        var cases = TestDatasetCatalog.all();

        assertEquals(40, cases.size());
        assertEquals(10, count(TestDatasetCategory.VALID));
        assertEquals(10, count(TestDatasetCategory.INCOMPLETE));
        assertEquals(10, count(TestDatasetCategory.RULE_VIOLATION));
        assertEquals(10, count(TestDatasetCategory.AMBIGUOUS));
        assertEquals(40, cases.stream().map(TestDatasetCaseSpec::key).distinct().count());
    }

    @Test
    void ambiguousCasesIncludeAmbiguityNotes() {
        TestDatasetCatalog.all().stream()
                .filter(spec -> spec.category() == TestDatasetCategory.AMBIGUOUS)
                .forEach(spec -> assertTrue(
                        spec.ambiguityNote() != null && !spec.ambiguityNote().isBlank(),
                        spec.key() + " should include ambiguity note"));
    }

    private long count(TestDatasetCategory category) {
        return TestDatasetCatalog.all().stream()
                .filter(spec -> spec.category() == category)
                .count();
    }
}
