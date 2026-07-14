package com.internship.coordinator.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class SampleApplicationPdf {

    private SampleApplicationPdf() {
    }

    public static byte[] create() {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Internship Application Form");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Student Name: Jan Kowalski");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Student ID: 123456");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Field of Study: Computer Engineering");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Company: Astana Kebab Sp. z o.o.");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Supervisor: Anna Nowak");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Supervisor Email: supervisor@example.com");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("Start Date: 2026-06-01");
                contentStream.newLineAtOffset(0, -24);
                contentStream.showText("End Date: 2026-11-30");
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create sample application PDF", exception);
        }
    }
}
