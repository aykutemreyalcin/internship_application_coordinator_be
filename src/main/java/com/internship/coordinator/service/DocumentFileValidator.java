package com.internship.coordinator.service;

import com.internship.coordinator.config.StorageProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentFileValidator {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Set<String> ALLOWED_PDF_CONTENT_TYPES =
            Set.of(MediaType.APPLICATION_PDF_VALUE, "application/x-pdf");

    private final long maxFileSizeBytes;

    public DocumentFileValidator(StorageProperties storageProperties) {
        this.maxFileSizeBytes = storageProperties.maxFileSizeBytes();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Document file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException("File exceeds maximum allowed size");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (".pdf".equals(extension)) {
            validatePdf(file);
            return;
        }
        if (".docx".equals(extension)) {
            validateDocx(file);
            return;
        }
        throw new InvalidFileException("Only PDF and Word (.docx) files are allowed");
    }

    public void validateBytes(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new InvalidFileException("Document file is required");
        }
        if (content.length > maxFileSizeBytes) {
            throw new InvalidFileException("File exceeds maximum allowed size");
        }

        String extension = extensionOf(fileName);
        if (".pdf".equals(extension)) {
            validatePdfHeader(content);
            return;
        }
        if (".docx".equals(extension)) {
            validateDocxBytes(content);
            return;
        }
        throw new InvalidFileException("Only PDF and Word (.docx) files are allowed");
    }

    public String resolveContentType(String fileName) {
        return ".docx".equals(extensionOf(fileName)) ? DOCX_CONTENT_TYPE : MediaType.APPLICATION_PDF_VALUE;
    }

    public String resolveStorageExtension(String fileName) {
        return extensionOf(fileName);
    }

    public String sanitizeFileName(String originalFilename) {
        String fileName = Path.of(originalFilename == null ? "" : originalFilename).getFileName().toString().trim();
        if (fileName.isBlank()) {
            return "document.pdf";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void validatePdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null
                || ALLOWED_PDF_CONTENT_TYPES.stream().noneMatch(allowed -> allowed.equalsIgnoreCase(contentType))) {
            throw new InvalidFileException("Only PDF and Word (.docx) files are allowed");
        }
        if (!".pdf".equals(extensionOf(file.getOriginalFilename()))) {
            throw new InvalidFileException("Only PDF and Word (.docx) files are allowed");
        }
        try {
            validatePdfHeader(file.getInputStream().readNBytes(5));
        } catch (IOException exception) {
            throw new InvalidFileException("Unable to read uploaded file");
        }
    }

    private void validateDocx(MultipartFile file) {
        if (!".docx".equals(extensionOf(file.getOriginalFilename()))) {
            throw new InvalidFileException("Only PDF and Word (.docx) files are allowed");
        }
        try {
            validateDocxBytes(file.getBytes());
        } catch (IOException exception) {
            throw new InvalidFileException("Unable to read uploaded file");
        }
    }

    private void validatePdfHeader(byte[] header) {
        if (header.length < 4 || !new String(header, 0, Math.min(header.length, 4), StandardCharsets.US_ASCII)
                .startsWith("%PDF")) {
            throw new InvalidFileException("File is not a valid PDF");
        }
    }

    private void validateDocxBytes(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw new InvalidFileException("File is not a valid Word (.docx) document");
        }
        boolean hasContentTypes = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                    break;
                }
            }
        } catch (IOException exception) {
            throw new InvalidFileException("File is not a valid Word (.docx) document");
        }
        if (!hasContentTypes) {
            throw new InvalidFileException("File is not a valid Word (.docx) document");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
