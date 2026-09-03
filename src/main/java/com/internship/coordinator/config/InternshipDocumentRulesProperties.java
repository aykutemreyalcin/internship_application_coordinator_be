package com.internship.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internship-document-rules")
public record InternshipDocumentRulesProperties(
        String configPath, LearningOutcomesReportRules learningOutcomesReport, InternshipJournalRules internshipJournal) {

    public record LearningOutcomesReportRules(
            int minDurationDays, int minWaysOfAchievingLength, boolean requireSupervisorSignature) {}

    public record InternshipJournalRules(
            int minWeeklyHours, int maxDailyHours, int minTotalHours, int minActivitiesLength) {}
}
