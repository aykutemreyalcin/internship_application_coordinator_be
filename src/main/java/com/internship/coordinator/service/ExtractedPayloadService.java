package com.internship.coordinator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ExtractedInternshipJournalData;
import com.internship.coordinator.dto.ExtractedLearningOutcomesReportData;
import com.internship.coordinator.model.ApplicationCase;
import com.internship.coordinator.model.CaseType;
import org.springframework.stereotype.Component;

@Component
public class ExtractedPayloadService {

    private final ObjectMapper objectMapper;

    public ExtractedPayloadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void storeReportPayload(ApplicationCase applicationCase, ExtractedLearningOutcomesReportData data) {
        applicationCase.setExtractedPayload(writeJson(data));
    }

    public void storeJournalPayload(ApplicationCase applicationCase, ExtractedInternshipJournalData data) {
        applicationCase.setExtractedPayload(writeJson(data));
    }

    public ExtractedLearningOutcomesReportData readReportPayload(ApplicationCase applicationCase) {
        return readPayload(applicationCase, ExtractedLearningOutcomesReportData.class);
    }

    public ExtractedInternshipJournalData readJournalPayload(ApplicationCase applicationCase) {
        return readPayload(applicationCase, ExtractedInternshipJournalData.class);
    }

    public JsonNode readPayloadNode(ApplicationCase applicationCase) {
        if (applicationCase.getExtractedPayload() == null || applicationCase.getExtractedPayload().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(applicationCase.getExtractedPayload());
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse extracted payload", exception);
        }
    }

    private <T> T readPayload(ApplicationCase applicationCase, Class<T> type) {
        if (applicationCase.getExtractedPayload() == null || applicationCase.getExtractedPayload().isBlank()) {
            return emptyPayload(type);
        }
        try {
            return objectMapper.readValue(applicationCase.getExtractedPayload(), type);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to parse extracted payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T emptyPayload(Class<T> type) {
        if (type == ExtractedLearningOutcomesReportData.class) {
            return (T) new ExtractedLearningOutcomesReportData(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        if (type == ExtractedInternshipJournalData.class) {
            return (T) new ExtractedInternshipJournalData(
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }
        throw new IllegalArgumentException("Unsupported payload type: " + type.getName());
    }

    public boolean isDocumentCase(ApplicationCase applicationCase) {
        CaseType caseType = applicationCase.getCaseType();
        return caseType == CaseType.LEARNING_OUTCOMES_REPORT || caseType == CaseType.INTERNSHIP_JOURNAL;
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new ExtractionParseException("Failed to serialize extracted payload", exception);
        }
    }
}
