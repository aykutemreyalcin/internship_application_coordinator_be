package com.internship.coordinator.service;

import com.internship.coordinator.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PdfFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(MediaType.APPLICATION_PDF_VALUE, "application/x-pdf");

    private final long maxFileSizeBytes;

    public PdfFileValidator(StorageProperties storageProperties) {
        this.maxFileSizeBytes = storageProperties.maxFileSizeBytes();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("PDF file is required");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException("PDF file exceeds maximum allowed size");
        }

        String contentType = file.getContentType();
        if (contentType == null
                || ALLOWED_CONTENT_TYPES.stream().noneMatch(allowed -> allowed.equalsIgnoreCase(contentType))) {
            throw new InvalidFileException("Only PDF files are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidFileException("Only PDF files are allowed");
        }

        try {
            byte[] header = file.getInputStream().readNBytes(5);
            if (header.length < 4 || !new String(header, 0, 4).startsWith("%PDF")) {
                throw new InvalidFileException("File is not a valid PDF");
            }
        } catch (IOException exception) {
            throw new InvalidFileException("Unable to read uploaded PDF");
        }
    }

    public String sanitizeFileName(String originalFilename) {
        String fileName = Path.of(originalFilename).getFileName().toString().trim();
        if (fileName.isBlank()) {
            return "application.pdf";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
