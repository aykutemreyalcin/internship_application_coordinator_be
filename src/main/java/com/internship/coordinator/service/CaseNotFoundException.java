package com.internship.coordinator.service;

import java.util.UUID;

public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(UUID caseId) {
        super("Case not found: " + caseId);
    }
}
