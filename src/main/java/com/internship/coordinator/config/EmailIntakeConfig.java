package com.internship.coordinator.config;

import com.internship.coordinator.service.ImapMailboxClient;
import com.internship.coordinator.service.MailboxClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EmailIntakeProperties.class)
@ConditionalOnProperty(prefix = "app.email-intake", name = "enabled", havingValue = "true")
public class EmailIntakeConfig {

    @Bean
    MailboxClient mailboxClient(EmailIntakeProperties properties) {
        validateProperties(properties);
        if (!"imap".equalsIgnoreCase(properties.mode())) {
            throw new IllegalStateException("Unsupported email intake mode: " + properties.mode());
        }
        return new ImapMailboxClient(properties.imap());
    }

    private void validateProperties(EmailIntakeProperties properties) {
        EmailIntakeProperties.Imap imap = properties.imap();
        if (!StringUtils.hasText(imap.username())) {
            throw new IllegalStateException("app.email-intake.imap.username is required when email intake is enabled");
        }
        if (!StringUtils.hasText(imap.password())) {
            throw new IllegalStateException("app.email-intake.imap.password is required when email intake is enabled");
        }
        if (!StringUtils.hasText(imap.host())) {
            throw new IllegalStateException("app.email-intake.imap.host is required when email intake is enabled");
        }
        if (!StringUtils.hasText(imap.folder())) {
            throw new IllegalStateException("app.email-intake.imap.folder is required when email intake is enabled");
        }
    }
}
