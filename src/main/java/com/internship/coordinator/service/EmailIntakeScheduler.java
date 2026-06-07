package com.internship.coordinator.service;

import com.internship.coordinator.agent.EmailIntakeAgent;
import com.internship.coordinator.config.EmailIntakeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email-intake", name = "enabled", havingValue = "true")
public class EmailIntakeScheduler {

    private final EmailIntakeAgent emailIntakeAgent;
    private final EmailIntakeProperties emailIntakeProperties;

    @Scheduled(fixedDelayString = "${app.email-intake.poll-interval-seconds:60}000")
    public void pollMailbox() {
        log.debug("Polling mailbox for internship application emails");
        var result = emailIntakeAgent.pollMailbox();
        if (result.processedCount() > 0 || result.skippedCount() > 0) {
            log.info(
                    "Email intake poll finished: processed={}, skipped={}, intervalSeconds={}",
                    result.processedCount(),
                    result.skippedCount(),
                    emailIntakeProperties.pollIntervalSeconds());
        }
    }
}
