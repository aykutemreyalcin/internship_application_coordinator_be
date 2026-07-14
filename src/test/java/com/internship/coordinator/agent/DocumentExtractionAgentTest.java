package com.internship.coordinator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionAgentTest {

    @Mock
    private GeminiClient geminiClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DocumentExtractionAgent documentExtractionAgent;

    @Test
    void extractParsesStructuredJsonFromGemini() {
        when(geminiClient.generateFromPdf(any(), eq(DocumentExtractionAgent.EXTRACTION_PROMPT)))
                .thenReturn(
                        """
                        {
                          "studentName": "Jan Kowalski",
                          "studentId": "123456",
                          "fieldOfStudy": "Computer Engineering",
                          "companyName": "Astana Kebab Sp. z o.o.",
                          "supervisorName": "Anna Nowak",
                          "supervisorEmail": "supervisor@example.com",
                          "internshipStartDate": "2026-06-01",
                          "internshipEndDate": "2026-11-30"
                        }
                        """);

        ExtractedApplicationData extracted = documentExtractionAgent.extract(new byte[] {1, 2, 3});

        assertEquals("Jan Kowalski", extracted.studentName());
        assertEquals("123456", extracted.studentId());
        assertEquals("Computer Engineering", extracted.fieldOfStudy());
        assertEquals("Astana Kebab Sp. z o.o.", extracted.companyName());
        assertEquals("Anna Nowak", extracted.supervisorName());
        assertEquals("supervisor@example.com", extracted.supervisorEmail());
        assertEquals("2026-06-01", extracted.internshipStartDate());
        assertEquals("2026-11-30", extracted.internshipEndDate());
        verify(geminiClient).generateFromPdf(any(), eq(DocumentExtractionAgent.EXTRACTION_PROMPT));
    }

    @Test
    void extractParsesJsonWrappedInCodeFence() {
        when(geminiClient.generateFromPdf(any(), eq(DocumentExtractionAgent.EXTRACTION_PROMPT)))
                .thenReturn(
                        """
                        ```json
                        {
                          "studentName": "Jan Kowalski",
                          "studentId": "123456",
                          "fieldOfStudy": "Computer Engineering",
                          "companyName": "Example GmbH",
                          "supervisorName": "Anna Nowak",
                          "supervisorEmail": "supervisor@example.com",
                          "internshipStartDate": "2026-06-01",
                          "internshipEndDate": "2026-10-28"
                        }
                        ```
                        """);

        ExtractedApplicationData extracted = documentExtractionAgent.extract(new byte[] {1, 2, 3});

        assertEquals("Jan Kowalski", extracted.studentName());
        assertEquals("123456", extracted.studentId());
    }
}
