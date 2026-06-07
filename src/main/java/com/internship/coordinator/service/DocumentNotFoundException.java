package com.internship.coordinator.service;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID caseId, UUID documentId) {
        super("Document not found: caseId=" + caseId + ", documentId=" + documentId);
    }
}
