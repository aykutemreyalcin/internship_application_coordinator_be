package com.internship.coordinator.agent;

import com.internship.coordinator.dto.EmailIntakePollResponse;
import com.internship.coordinator.dto.IncomingEmailMessage;
import com.internship.coordinator.model.ProcessedEmailMessage;
import com.internship.coordinator.repository.ProcessedEmailMessageRepository;
import com.internship.coordinator.service.CaseService;
import com.internship.coordinator.service.MailboxClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email-intake", name = "enabled", havingValue = "true")
public class EmailIntakeAgent {

    private final MailboxClient mailboxClient;
    private final CaseService caseService;
    private final ProcessedEmailMessageRepository processedEmailMessageRepository;
    private final ReentrantLock pollLock = new ReentrantLock();

    public EmailIntakePollResponse pollMailbox() {
        if (!pollLock.tryLock()) {
            log.warn("Email intake poll already in progress, skipping overlapping request");
            return new EmailIntakePollResponse(0, 0, List.of());
        }
        try {
            return pollMailboxUnlocked();
        } finally {
            pollLock.unlock();
        }
    }

    private EmailIntakePollResponse pollMailboxUnlocked() {
        List<IncomingEmailMessage> messages = mailboxClient.fetchUnreadPdfMessages();
        int processedCount = 0;
        int skippedCount = 0;
        List<UUID> caseIds = new ArrayList<>();

        for (IncomingEmailMessage message : messages) {
            try {
                UUID caseId = processMessage(message);
                if (caseId == null) {
                    skippedCount++;
                } else {
                    processedCount++;
                    caseIds.add(caseId);
                }
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to process email messageId={} subject={}",
                        message.messageId(),
                        message.subject(),
                        exception);
            }
        }

        return new EmailIntakePollResponse(processedCount, skippedCount, caseIds);
    }


    @Transactional
    UUID processMessage(IncomingEmailMessage message) {
        if (processedEmailMessageRepository.existsByMessageId(message.messageId())) {
            mailboxClient.markAsProcessed(message.uid());
            return null;
        }

        var createdCase = caseService.createCaseFromEmail(
                message.pdfAttachment().fileName(),
                message.pdfAttachment().content(),
                message.sender(),
                message.subject(),
                message.messageId());

        var extractedCase = caseService.extractCase(createdCase.caseId());
        caseService.tryGenerateRecommendation(extractedCase.caseId());

        processedEmailMessageRepository.save(ProcessedEmailMessage.builder()
                .messageId(message.messageId())
                .caseId(createdCase.caseId())
                .sender(message.sender())
                .subject(message.subject())
                .build());

        mailboxClient.markAsProcessed(message.uid());
        return createdCase.caseId();
    }
}
