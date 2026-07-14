package com.internship.coordinator.service;

import com.internship.coordinator.dto.IncomingEmailMessage;
import java.util.List;

public interface MailboxClient {

    List<IncomingEmailMessage> fetchUnreadPdfMessages();

    void markAsProcessed(long uid);
}
