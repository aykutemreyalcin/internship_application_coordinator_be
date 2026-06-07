package com.internship.coordinator.agent;

import com.internship.coordinator.dto.IncomingEmailAttachment;
import com.internship.coordinator.dto.IncomingEmailMessage;
import com.internship.coordinator.model.ProcessedEmailMessage;
import com.internship.coordinator.repository.ProcessedEmailMessageRepository;
import com.internship.coordinator.service.CaseService;
import com.internship.coordinator.service.MailboxClient;
import com.internship.coordinator.dto.CaseDetailResponse;
import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.dto.ValidationSummaryDto;
import com.internship.coordinator.dto.ValidationGroupDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailIntakeAgentTest {

    @Mock
    private MailboxClient mailboxClient;

    @Mock
    private CaseService caseService;

    @Mock
    private ProcessedEmailMessageRepository processedEmailMessageRepository;

    @InjectMocks
    private EmailIntakeAgent emailIntakeAgent;

    private byte[] pdfBytes;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        pdfBytes = "%PDF-1.4 test".getBytes();
        caseId = UUID.randomUUID();
    }

    @Test
    void pollMailboxCreatesCaseRunsPipelineAndMarksEmailProcessed() {
        IncomingEmailMessage message = new IncomingEmailMessage(
                42L,
                "<msg-1@test>",
                "student@example.com",
                "Internship application",
                new IncomingEmailAttachment("application.pdf", pdfBytes));

        when(mailboxClient.fetchUnreadPdfMessages()).thenReturn(List.of(message));
        when(processedEmailMessageRepository.existsByMessageId("<msg-1@test>")).thenReturn(false);
        when(caseService.createCaseFromEmail(
                        eq("application.pdf"),
                        eq(pdfBytes),
                        eq("student@example.com"),
                        eq("Internship application"),
                        eq("<msg-1@test>")))
                .thenReturn(sampleCase(CaseStatus.NEW));
        when(caseService.extractCase(caseId)).thenReturn(sampleCase(CaseStatus.NEW));

        var response = emailIntakeAgent.pollMailbox();

        assertEquals(1, response.processedCount());
        assertEquals(0, response.skippedCount());
        assertEquals(List.of(caseId), response.caseIds());

        verify(caseService).tryGenerateRecommendation(caseId);
        verify(mailboxClient).markAsProcessed(42L);

        ArgumentCaptor<ProcessedEmailMessage> processedCaptor = ArgumentCaptor.forClass(ProcessedEmailMessage.class);
        verify(processedEmailMessageRepository).save(processedCaptor.capture());
        assertEquals("<msg-1@test>", processedCaptor.getValue().getMessageId());
        assertEquals(caseId, processedCaptor.getValue().getCaseId());
    }

    @Test
    void pollMailboxSkipsAlreadyProcessedMessage() {
        IncomingEmailMessage message = new IncomingEmailMessage(
                7L,
                "<msg-duplicate@test>",
                "student@example.com",
                "Duplicate",
                new IncomingEmailAttachment("application.pdf", pdfBytes));

        when(mailboxClient.fetchUnreadPdfMessages()).thenReturn(List.of(message));
        when(processedEmailMessageRepository.existsByMessageId("<msg-duplicate@test>")).thenReturn(true);

        var response = emailIntakeAgent.pollMailbox();

        assertEquals(0, response.processedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(List.of(), response.caseIds());
        verify(caseService, never()).createCaseFromEmail(anyString(), any(), anyString(), anyString(), anyString());
        verify(mailboxClient).markAsProcessed(7L);
    }

    @Test
    void processMessageReturnsNullWhenAlreadyProcessed() {
        IncomingEmailMessage message = new IncomingEmailMessage(
                9L,
                "<already-done@test>",
                "student@example.com",
                "Done",
                new IncomingEmailAttachment("application.pdf", pdfBytes));

        when(processedEmailMessageRepository.existsByMessageId("<already-done@test>")).thenReturn(true);

        UUID result = emailIntakeAgent.processMessage(message);

        assertNull(result);
        verify(mailboxClient).markAsProcessed(9L);
    }

    private CaseDetailResponse sampleCase(CaseStatus status) {
        ValidationSummaryDto validation =
                new ValidationSummaryDto(new ValidationGroupDto(true, List.of()), new ValidationGroupDto(true, List.of()));
        return new CaseDetailResponse(
                caseId,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                validation,
                List.of(),
                null,
                null);
    }
}
