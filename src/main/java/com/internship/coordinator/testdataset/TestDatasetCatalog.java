package com.internship.coordinator.testdataset;

import java.time.LocalDate;
import java.util.List;

public final class TestDatasetCatalog {

    private TestDatasetCatalog() {}

    public static List<TestDatasetCaseSpec> all() {
        return List.of(
                valid("valid-01", "Jan Kowalski", "100001", "Computer Engineering", "Astana Kebab Sp. z o.o.", "Anna Nowak", "anna.nowak@astanakebab.pl", "2026-06-01", "2026-10-28"),
                valid("valid-02", "Maria Wisniewska", "100002", "Business Administration", "Green Logistics SA", "Piotr Zielinski", "piotr.z@greenlogistics.pl", "2026-03-15", "2026-08-15"),
                valid("valid-03", "Tomasz Lewandowski", "100003", "Mechanical Engineering", "MetalWorks GmbH", "Eva Schmidt", "eva.schmidt@metalworks.de", "2026-05-01", "2026-09-30"),
                valid("valid-04", "Katarzyna Dabrowska", "100004", "Psychology", "City Hospital", "Dr. Jan Malinowski", "j.malinowski@cityhospital.pl", "2026-07-01", "2026-11-15"),
                valid("valid-05", "Piotr Zajac", "100005", "Electrical Engineering", "PowerGrid Sp. z o.o.", "Marta Kaminska", "marta.k@powergrid.pl", "2026-04-01", "2026-08-30"),
                valid("valid-06", "Agnieszka Wojcik", "100006", "Biotechnology", "BioLab Research", "Prof. Helena Rut", "h.rut@biolab.pl", "2026-06-15", "2026-11-01"),
                valid("valid-07", "Marcin Kaczmarek", "100007", "Architecture", "Studio Architektura", "Lukasz Borkowski", "l.borkowski@studioarch.pl", "2026-02-01", "2026-07-01"),
                valid("valid-08", "Ewa Jankowska", "100008", "Law", "Legal Partners LLP", "Adv. Tomasz Rybak", "t.rybak@legalpartners.pl", "2026-08-01", "2026-12-15"),
                valid("valid-09", "Jakub Mazur", "100009", "Data Science", "Analytics Hub", "Nina Olszewska", "nina.o@analyticshub.io", "2026-05-15", "2026-10-01"),
                valid("valid-10", "Zofia Krupa", "100010", "Industrial Design", "Design Factory", "Karol Sienkiewicz", "karol.s@designfactory.pl", "2026-09-01", "2027-01-15"),

                incomplete("incomplete-01", null, "200001", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-02", "Jan Kowalski", null, "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-03", "Jan Kowalski", "200003", null, "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-04", "Jan Kowalski", "200004", "Computer Engineering", null, "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-05", "Jan Kowalski", "200005", "Computer Engineering", "Example GmbH", null, "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-06", "Jan Kowalski", "200006", "Computer Engineering", "Example GmbH", "Anna Nowak", null, "2026-06-01", "2026-10-28"),
                incomplete("incomplete-07", "Jan Kowalski", "200007", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", null, "2026-10-28"),
                incomplete("incomplete-08", "Jan Kowalski", "200008", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", null),
                incomplete("incomplete-09", "Jan Kowalski", null, null, "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                incomplete("incomplete-10", "Jan Kowalski", "200010", "Computer Engineering", "Example GmbH", null, null, "2026-06-01", "2026-10-28"),

                ruleViolation("rules-01", "Jan Kowalski", "300001", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-06-30"),
                ruleViolation("rules-02", "Jan Kowalski", "300002", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-01-01", "2026-12-31"),
                ruleViolation("rules-03", "Jan Kowalski", "300003", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-10-01", "2026-06-01"),
                ruleViolation("rules-04", "Jan Kowalski", "300004", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2025-06-01", "2025-10-01"),
                ruleViolation("rules-05", "Jan Kowalski", "300005", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2027-06-01", "2028-01-01"),
                ruleViolation("rules-06", "Jan Kowalski", "ABC123", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                ruleViolation("rules-07", "Jan Kowalski", "1234", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-10-28"),
                ruleViolation("rules-08", "Jan Kowalski", "300008", "Computer Engineering", "Example GmbH", "Anna Nowak", "not-an-email", "2026-06-01", "2026-10-28"),
                ruleViolation("rules-09", "Jan Kowalski", "BAD", "Computer Engineering", "Example GmbH", "Anna Nowak", "supervisor@example.com", "2026-06-01", "2026-07-15"),
                ruleViolation("rules-10", "Jan Kowalski", "300010", "Computer Engineering", "Example GmbH", "Anna Nowak", "invalid@", "2026-10-01", "2026-06-01"),

                ambiguous("ambiguous-01", "Jan Kowalski", "400001", "Computer Engineering", "Jan Kowalski IT Services", "Anna Nowak", "anna.nowak@jankowalski-it.pl", "2026-06-01", "2026-10-28", "Company name matches student name; verify employer is external."),
                ambiguous("ambiguous-02", "Maria Wisniewska", "400002", "Business Administration", "Wisniewski Trading", "Tomasz Wisniewski", "t.wisniewski@wtrade.pl", "2026-04-01", "2026-09-01", "Supervisor shares surname with student; confirm no family relationship."),
                ambiguous("ambiguous-03", "Piotr Zielinski", "400003", "Fine Arts", "TechCorp Software", "Eva Schmidt", "eva.s@techcorp.io", "2026-05-01", "2026-09-30", "Field of study does not match company industry."),
                ambiguous("ambiguous-04", "Katarzyna Nowak", "400004", "Nursing", "City Hospital", "Dr. A. Nowak", "a.nowak@hospital.pl", "2026-06-01", "2026-08-23", "Minimum duration internship; confirm full-time commitment."),
                ambiguous("ambiguous-05", "Tomasz Lewandowski", "400005", "Marketing", "Remote / TBD", "Contact Person", "info@remote-tbd.com", "2026-07-01", "2026-11-01", "Company location and legal entity are unclear."),
                ambiguous("ambiguous-06", "Agnieszka Wojcik", "400006", "Law", "Legal Partners LLP", "A. W.", "contact@legalpartners.pl", "2026-06-15", "2026-10-15", "Supervisor listed only by initials."),
                ambiguous("ambiguous-07", "Marcin Kaczmarek", "400007", "Economics", "Maria Nowak Consulting", "Maria Nowak", "maria@mnconsulting.pl", "2026-03-01", "2026-07-30", "Sole proprietorship named after individual; verify business registration."),
                ambiguous("ambiguous-08", "Ewa Jankowska", "400008", "Computer Science", "Hospital XYZ", "IT Manager", "it@hospitalxyz.org", "2026-08-01", "2026-12-01", "Tech internship at healthcare org without department details."),
                ambiguous("ambiguous-09", "Jakub Mazur", "400009", "International Relations", "Global Partners Ltd", "Jan Kowalski", "jan.kowalski@gmail.com", "2026-05-01", "2026-09-01", "Supervisor uses personal email domain instead of company domain."),
                ambiguous("ambiguous-10", "Zofia Krupa", "400010", "Industrial Design", "Design Factory Sp. z o.o. / DF", "Karol Sienkiewicz", "karol.s@designfactory.pl", "2026-09-01", "2027-01-10", "Company listed under two names; confirm official employer."));
    }

    private static TestDatasetCaseSpec valid(
            String key,
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            String start,
            String end) {
        return spec(key, TestDatasetCategory.VALID, studentName, studentId, fieldOfStudy, companyName, supervisorName, supervisorEmail, start, end, null);
    }

    private static TestDatasetCaseSpec incomplete(
            String key,
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            String start,
            String end) {
        return spec(key, TestDatasetCategory.INCOMPLETE, studentName, studentId, fieldOfStudy, companyName, supervisorName, supervisorEmail, start, end, null);
    }

    private static TestDatasetCaseSpec ruleViolation(
            String key,
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            String start,
            String end) {
        return spec(key, TestDatasetCategory.RULE_VIOLATION, studentName, studentId, fieldOfStudy, companyName, supervisorName, supervisorEmail, start, end, null);
    }

    private static TestDatasetCaseSpec ambiguous(
            String key,
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            String start,
            String end,
            String note) {
        return spec(key, TestDatasetCategory.AMBIGUOUS, studentName, studentId, fieldOfStudy, companyName, supervisorName, supervisorEmail, start, end, note);
    }

    private static TestDatasetCaseSpec spec(
            String key,
            TestDatasetCategory category,
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            String start,
            String end,
            String note) {
        return new TestDatasetCaseSpec(
                key,
                category,
                studentName,
                studentId,
                fieldOfStudy,
                companyName,
                supervisorName,
                supervisorEmail,
                parseDate(start),
                parseDate(end),
                note);
    }

    private static LocalDate parseDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
