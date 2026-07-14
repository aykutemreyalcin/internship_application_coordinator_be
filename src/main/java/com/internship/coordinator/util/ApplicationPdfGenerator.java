package com.internship.coordinator.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class ApplicationPdfGenerator {

    private ApplicationPdfGenerator() {}

    public static byte[] generate(
            String studentName,
            String studentId,
            String fieldOfStudy,
            String companyName,
            String supervisorName,
            String supervisorEmail,
            LocalDate internshipStartDate,
            LocalDate internshipEndDate) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 700);
                writeLine(contentStream, "Internship Application Form");
                writeLine(contentStream, "Student Name: " + valueOrPlaceholder(studentName));
                writeLine(contentStream, "Student ID: " + valueOrPlaceholder(studentId));
                writeLine(contentStream, "Field of Study: " + valueOrPlaceholder(fieldOfStudy));
                writeLine(contentStream, "Company: " + valueOrPlaceholder(companyName));
                writeLine(contentStream, "Supervisor: " + valueOrPlaceholder(supervisorName));
                writeLine(contentStream, "Supervisor Email: " + valueOrPlaceholder(supervisorEmail));
                writeLine(contentStream, "Start Date: " + formatDate(internshipStartDate));
                writeLine(contentStream, "End Date: " + formatDate(internshipEndDate));
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate application PDF", exception);
        }
    }

    private static void writeLine(PDPageContentStream contentStream, String text) throws IOException {
        contentStream.showText(text);
        contentStream.newLineAtOffset(0, -24);
    }

    private static String valueOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "(missing)" : value;
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "(missing)" : date.toString();
    }
}
