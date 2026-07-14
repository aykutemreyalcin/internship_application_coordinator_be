package com.internship.coordinator.service;

import com.internship.coordinator.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfFileValidatorTest {

    private PdfFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PdfFileValidator(new StorageProperties("uploads", 1024));
    }

    @Test
    void validateAcceptsValidPdfMultipartFile() {
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        validator.validate(file);
    }

    @Test
    void validateRejectsNonPdfExtension() {
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        MockMultipartFile file =
                new MockMultipartFile("file", "application.txt", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        InvalidFileException exception = assertThrows(InvalidFileException.class, () -> validator.validate(file));
        assertEquals("Only PDF files are allowed", exception.getMessage());
    }

    @Test
    void validateRejectsFileExceedingMaxSize() {
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes) {
            @Override
            public long getSize() {
                return 2048;
            }
        };

        InvalidFileException exception = assertThrows(InvalidFileException.class, () -> validator.validate(file));
        assertEquals("PDF file exceeds maximum allowed size", exception.getMessage());
    }

    @Test
    void validateBytesAcceptsValidPdfContent() {
        validator.validateBytes("%PDF-1.4\n".getBytes(), "application.pdf");
    }

    @Test
    void validateBytesRejectsInvalidHeader() {
        InvalidFileException exception = assertThrows(
                InvalidFileException.class, () -> validator.validateBytes("NOTPDF".getBytes(), "application.pdf"));
        assertEquals("File is not a valid PDF", exception.getMessage());
    }

    @Test
    void sanitizeFileNameReplacesUnsafeCharacters() {
        assertEquals("my_application.pdf", validator.sanitizeFileName("my application.pdf"));
        assertEquals("application.pdf", validator.sanitizeFileName(""));
    }
}
