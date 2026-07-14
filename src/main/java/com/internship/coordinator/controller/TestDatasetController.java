package com.internship.coordinator.controller;

import com.internship.coordinator.dto.TestDatasetSeedResponse;
import com.internship.coordinator.testdataset.TestDatasetSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/test-dataset")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-dataset", name = "enabled", havingValue = "true")
public class TestDatasetController {

    private final TestDatasetSeeder testDatasetSeeder;

    @PostMapping("/seed")
    public TestDatasetSeedResponse seed() {
        return testDatasetSeeder.seed();
    }

    @GetMapping("/summary")
    public TestDatasetSeedResponse summary() {
        var counts = testDatasetSeeder.summarizeLoadedDataset();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new TestDatasetSeedResponse(total, counts);
    }
}
