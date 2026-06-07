package com.internship.coordinator.service;

import com.internship.coordinator.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentStorageService {

    private final Path uploadRoot;

    public DocumentStorageService(StorageProperties storageProperties) throws IOException {
        this.uploadRoot = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
    }

    public String store(String relativePath, MultipartFile file) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return relativePath;
    }

    public void storeBytes(String relativePath, byte[] content) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    public Resource loadAsResource(String relativePath) {
        Path filePath = resolve(relativePath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new DocumentStorageException("Stored file not found: " + relativePath);
        }
        return new FileSystemResource(filePath);
    }

    public byte[] readBytes(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to read stored file: " + relativePath, exception);
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = uploadRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new DocumentStorageException("Invalid storage path: " + relativePath);
        }
        return resolved;
    }
}
