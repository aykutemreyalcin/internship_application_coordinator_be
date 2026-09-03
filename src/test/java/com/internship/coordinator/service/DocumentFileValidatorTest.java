package com.internship.coordinator.service;

import com.internship.coordinator.config.StorageProperties;
import com.internship.coordinator.support.SampleDocx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentFileValidatorTest {

    private DocumentFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DocumentFileValidator(new StorageProperties("uploads", 1024 * 1024));
    }

    @Test
    void validateAcceptsValidPdfMultipartFile() {
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        validator.validate(file);
    }

    @Test
    void validateAcceptsValidDocxMultipartFile() {
        byte[] docxBytes = SampleDocx.create("Student Internship Journal", "Week 1 activities");
        MockMultipartFile file = new MockMultipartFile(
                "file", "journal.docx", DocumentFileValidator.DOCX_CONTENT_TYPE, docxBytes);

        validator.validate(file);
    }

    @Test
    void validateRejectsDocExtension() {
        byte[] docxBytes = SampleDocx.create("test");
        MockMultipartFile file = new MockMultipartFile(
                "file", "legacy.doc", DocumentFileValidator.DOCX_CONTENT_TYPE, docxBytes);

        InvalidFileException exception = assertThrows(InvalidFileException.class, () -> validator.validate(file));
        assertEquals("Only PDF and Word (.docx) files are allowed", exception.getMessage());
    }

    @Test
    void validateRejectsImageFile() {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

        InvalidFileException exception = assertThrows(InvalidFileException.class, () -> validator.validate(file));
        assertEquals("Only PDF and Word (.docx) files are allowed", exception.getMessage());
    }

    @Test
    void sanitizeFileNameReplacesUnsafeCharacters() {
        assertEquals("my_document.docx", validator.sanitizeFileName("my document.docx"));
    }
}
