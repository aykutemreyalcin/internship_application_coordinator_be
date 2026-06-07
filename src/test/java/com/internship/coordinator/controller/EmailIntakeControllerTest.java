package com.internship.coordinator.controller;

import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.agent.EmailIntakeAgent;
import com.internship.coordinator.dto.ExtractedApplicationData;
import com.internship.coordinator.dto.IncomingEmailAttachment;
import com.internship.coordinator.dto.IncomingEmailMessage;
import com.internship.coordinator.repository.ApplicationCaseRepository;
import com.internship.coordinator.repository.ProcessedEmailMessageRepository;
import com.internship.coordinator.service.MailboxClient;
import com.internship.coordinator.support.SampleApplicationPdf;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
        properties = {
            "app.email-intake.enabled=true",
            "app.email-intake.imap.username=test@gmail.com",
            "app.email-intake.imap.password=test-app-password",
            "spring.task.scheduling.enabled=false"
        })
class EmailIntakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationCaseRepository applicationCaseRepository;

    @Autowired
    private ProcessedEmailMessageRepository processedEmailMessageRepository;

    @MockitoBean
    private MailboxClient mailboxClient;

    @MockitoBean
    private DocumentExtractionAgent documentExtractionAgent;

    @Test
    void pollEndpointProcessesEmailedPdfIntoCaseAndExtractsFields() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        IncomingEmailMessage message = new IncomingEmailMessage(
                101L,
                "<email-intake-test@local>",
                "student@example.com",
                "Internship application PDF",
                new IncomingEmailAttachment("application.pdf", pdfContent));

        when(mailboxClient.fetchUnreadPdfMessages()).thenReturn(List.of(message));
        when(documentExtractionAgent.extract(any()))
                .thenReturn(new ExtractedApplicationData(
                        "Jan Kowalski",
                        "123456",
                        "Computer Engineering",
                        "Astana Kebab Sp. z o.o.",
                        "Anna Nowak",
                        "supervisor@example.com",
                        "2026-06-01",
                        "2026-11-30"));

        mockMvc.perform(post("/api/internal/email-intake/poll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount", is(1)))
                .andExpect(jsonPath("$.skippedCount", is(0)))
                .andExpect(jsonPath("$.caseIds", hasSize(1)));

        verify(mailboxClient).markAsProcessed(101L);

        var cases = applicationCaseRepository.findAll().stream()
                .filter(applicationCase -> applicationCase.getDatasetKey() == null)
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, cases.size());
        org.junit.jupiter.api.Assertions.assertEquals("Jan Kowalski", cases.getFirst().getStudentName());
        org.junit.jupiter.api.Assertions.assertEquals(1, cases.getFirst().getDocuments().size());

        org.junit.jupiter.api.Assertions.assertTrue(
                processedEmailMessageRepository.existsByMessageId("<email-intake-test@local>"));
    }
}
