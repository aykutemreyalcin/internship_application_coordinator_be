package com.internship.coordinator.controller;

import com.internship.coordinator.dto.TestDatasetSeedResponse;
import com.internship.coordinator.testdataset.InternshipDocumentsSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/internship-documents")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-dataset", name = "enabled", havingValue = "true")
public class InternshipDocumentsSeedController {

    private final InternshipDocumentsSeeder internshipDocumentsSeeder;

    @PostMapping("/seed")
    public TestDatasetSeedResponse seed() {
        return internshipDocumentsSeeder.seed();
    }
}
