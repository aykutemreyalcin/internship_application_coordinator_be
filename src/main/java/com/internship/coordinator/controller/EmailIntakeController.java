package com.internship.coordinator.controller;

import com.internship.coordinator.agent.EmailIntakeAgent;
import com.internship.coordinator.dto.EmailIntakePollResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/email-intake")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email-intake", name = "enabled", havingValue = "true")
public class EmailIntakeController {

    private final EmailIntakeAgent emailIntakeAgent;

    @PostMapping("/poll")
    public EmailIntakePollResponse poll() {
        return emailIntakeAgent.pollMailbox();
    }
}
