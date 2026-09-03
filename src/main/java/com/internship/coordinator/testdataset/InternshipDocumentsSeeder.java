package com.internship.coordinator.testdataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.JournalDayEntry;
import com.internship.coordinator.dto.JournalWeekEntry;
import com.internship.coordinator.dto.LearningOutcomeEntry;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.ApplicationDocument;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.CaseType;
import com.internship.coordinator.model.Recommendation;
import com.internship.coordinator.service.DocumentFileValidator;
import com.internship.coordinator.service.DocumentStorageService;
import com.internship.coordinator.service.ExtractedPayloadService;
import com.internship.coordinator.agent.CompletenessValidationAgent;
import com.internship.coordinator.agent.UniversityRulesAgent;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.dto.TestDatasetSeedResponse;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InternshipDocumentsSeeder {

    private final ApplicationCaseRepository applicationCaseRepository;
    private final DocumentStorageService documentStorageService;
    private final ExtractedPayloadService extractedPayloadService;
    private final CompletenessValidationAgent completenessValidationAgent;
    private final UniversityRulesAgent universityRulesAgent;

    @Transactional
    public TestDatasetSeedResponse seed() {
        applicationCaseRepository
                .findAll()
                .stream()
                .filter(applicationCase -> applicationCase.getDatasetKey() != null
                        && applicationCase.getDatasetKey().startsWith("doc-"))
                .forEach(applicationCase -> applicationCaseRepository.delete(applicationCase));

        Map<String, Integer> counts = new LinkedHashMap<>();
        seedCompleteReport();
        counts.merge("report-complete", 1, Integer::sum);
        seedIncompleteReport();
        counts.merge("report-incomplete", 1, Integer::sum);
        seedCompleteJournal();
        counts.merge("journal-complete", 1, Integer::sum);
        seedUnderLoggedJournal();
        counts.merge("journal-under-logged", 1, Integer::sum);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new TestDatasetSeedResponse(total, counts);
    }

    private void seedCompleteReport() {
        ExtractedLearningOutcomesReportData payload = new ExtractedLearningOutcomesReportData(
                "Jan Kowalski",
                "123456",
                "2026-11-30",
                "Astana Kebab Sp. z o.o.",
                "2026-06-01",
                "2026-11-30",
                completeOutcomes(),
                true,
                "Anna Nowak",
                "Good performance",
                true,
                "2026-11-29",
                6,
                6,
                "YES",
                "All outcomes achieved");
        createDocumentCase(
                "doc-report-complete",
                CaseType.LEARNING_OUTCOMES_REPORT,
                "report-complete.docx",
                payload,
                Recommendation.APPROVE,
                CaseStatus.READY_FOR_REVIEW);
    }

    private void seedIncompleteReport() {
        ExtractedLearningOutcomesReportData payload = new ExtractedLearningOutcomesReportData(
                "Ewa Nowak",
                "654321",
                "2026-11-30",
                "Example Corp",
                "2026-06-01",
                "2026-11-30",
                List.of(new LearningOutcomeEntry(
                        "W1",
                        "Familiar with company activities",
                        "Shadowed the engineering team for two weeks")),
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        createDocumentCase(
                "doc-report-incomplete",
                CaseType.LEARNING_OUTCOMES_REPORT,
                "report-incomplete.docx",
                payload,
                Recommendation.REJECT,
                CaseStatus.NEW);
    }

    private void seedCompleteJournal() {
        ExtractedInternshipJournalData payload = completeJournalPayload();
        createDocumentCase(
                "doc-journal-complete",
                CaseType.INTERNSHIP_JOURNAL,
                "journal-complete.docx",
                payload,
                Recommendation.APPROVE,
                CaseStatus.READY_FOR_REVIEW);
    }

    private void seedUnderLoggedJournal() {
        ExtractedInternshipJournalData payload = new ExtractedInternshipJournalData(
                "Piotr Wiśniewski",
                "789012",
                "Faculty of Applied Sciences",
                "Computer Engineering",
                "FULL_TIME",
                "2025/2026",
                "Example Corp",
                "Warsaw, Poland",
                "2026-06-01",
                "2026-11-30",
                "Anna Nowak",
                List.of(new JournalWeekEntry(
                        "2026-06-01",
                        "2026-06-07",
                        List.of(
                                new JournalDayEntry("2026-06-01", "10:00", "16:00", 6, "Backend development"),
                                new JournalDayEntry("2026-06-02", "10:00", "16:00", 6, "API integration")),
                        false)));
        createDocumentCase(
                "doc-journal-under-logged",
                CaseType.INTERNSHIP_JOURNAL,
                "journal-under-logged.docx",
                payload,
                Recommendation.REJECT,
                CaseStatus.NEW);
    }

    private void createDocumentCase(
            String datasetKey,
            CaseType caseType,
            String fileName,
            Object payload,
            Recommendation recommendation,
            CaseStatus status) {
        ApplicationCase applicationCase = ApplicationCase.builder()
                .status(status)
                .caseType(caseType)
                .datasetKey(datasetKey)
                .recommendation(recommendation)
                .build();

        if (payload instanceof ExtractedLearningOutcomesReportData reportPayload) {
            applicationCase.setStudentName(reportPayload.studentName());
            applicationCase.setStudentId(reportPayload.studentId());
            applicationCase.setCompanyName(reportPayload.hostCompanyOrEmployer());
            applicationCase.setSupervisorName(reportPayload.supervisorName());
            applicationCase.setInternshipStartDate(parseDate(reportPayload.internshipStartDate()));
            applicationCase.setInternshipEndDate(parseDate(reportPayload.internshipEndDate()));
            extractedPayloadService.storeReportPayload(applicationCase, reportPayload);
        } else if (payload instanceof ExtractedInternshipJournalData journalPayload) {
            applicationCase.setStudentName(journalPayload.studentName());
            applicationCase.setStudentId(journalPayload.studentId());
            applicationCase.setFieldOfStudy(journalPayload.fieldOfStudy());
            applicationCase.setCompanyName(journalPayload.companyName());
            applicationCase.setSupervisorName(journalPayload.companySupervisorName());
            applicationCase.setInternshipStartDate(parseDate(journalPayload.internshipStartDate()));
            applicationCase.setInternshipEndDate(parseDate(journalPayload.internshipEndDate()));
            extractedPayloadService.storeJournalPayload(applicationCase, journalPayload);
        }

        ApplicationDocument document = ApplicationDocument.builder()
                .fileName(fileName)
                .storagePath("pending")
                .contentType(DocumentFileValidator.DOCX_CONTENT_TYPE)
                .build();
        applicationCase.addDocument(document);
        applicationCaseRepository.save(applicationCase);

        String storagePath = applicationCase.getCaseId() + "/" + document.getId() + ".docx";
        try {
            documentStorageService.storeBytes(storagePath, placeholderDocxBytes(fileName));
        } catch (IOException exception) {
            throw new TestDatasetSeedException("Failed to store sample document", exception);
        }
        document.setStoragePath(storagePath);

        var completeness = completenessValidationAgent.validate(applicationCase);
        var rules = universityRulesAgent.validate(applicationCase);
        applicationCase.addValidationResult(completeness);
        applicationCase.addValidationResult(rules);
        applicationCaseRepository.save(applicationCase);
    }

    private List<LearningOutcomeEntry> completeOutcomes() {
        return List.of(
                new LearningOutcomeEntry(
                        "W1",
                        "The student became familiar with the activities carried out by the host company",
                        "Participated in daily stand-ups and observed sprint planning sessions throughout June"),
                new LearningOutcomeEntry(
                        "U1",
                        "The student is able to navigate the organizational system of a company",
                        "Mapped team structure and documented reporting lines during the first internship week"));
    }

    private ExtractedInternshipJournalData completeJournalPayload() {
        return new ExtractedInternshipJournalData(
                "Jan Kowalski",
                "123456",
                "Faculty of Applied Sciences",
                "Computer Engineering",
                "FULL_TIME",
                "2025/2026",
                "Astana Kebab Sp. z o.o.",
                "Warsaw, Poland",
                "2026-06-01",
                "2026-11-30",
                "Anna Nowak",
                List.of(
                        fullWeek("2026-06-01", "2026-06-07"),
                        fullWeek("2026-06-08", "2026-06-14"),
                        fullWeek("2026-06-15", "2026-06-21"),
                        fullWeek("2026-06-22", "2026-06-28"),
                        fullWeek("2026-06-29", "2026-07-05"),
                        fullWeek("2026-07-06", "2026-07-12")));
    }

    private JournalWeekEntry fullWeek(String weekStart, String weekEnd) {
        return new JournalWeekEntry(
                weekStart,
                weekEnd,
                List.of(
                        workingDay(weekStart, "Backend API development and code review"),
                        workingDay(offsetDay(weekStart, 1), "Database migration and integration tests"),
                        workingDay(offsetDay(weekStart, 2), "Frontend coordination and bug fixing"),
                        workingDay(offsetDay(weekStart, 3), "Documentation and deployment support"),
                        workingDay(offsetDay(weekStart, 4), "Sprint retrospective and planning")),
                true);
    }

    private JournalDayEntry workingDay(String date, String activities) {
        return new JournalDayEntry(date, "10:00", "16:00", 6, activities);
    }

    private String offsetDay(String isoDate, int days) {
        return LocalDate.parse(isoDate).plusDays(days).toString();
    }

    private LocalDate parseDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private byte[] placeholderDocxBytes(String fileName) {
        return ("Placeholder DOCX content for " + fileName).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
