package com.internship.coordinator.service;

import org.springframework.core.io.Resource;

public record StoredDocument(String fileName, Resource resource) {
}
