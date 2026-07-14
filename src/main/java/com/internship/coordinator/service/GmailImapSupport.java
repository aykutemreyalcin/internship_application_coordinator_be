package com.internship.coordinator.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.eclipse.angus.mail.iap.Argument;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.IMAPResponse;

final class GmailImapSupport {

    private GmailImapSupport() {}

    static boolean isGmailHost(String host) {
        return host != null && host.toLowerCase(Locale.ROOT).contains("gmail");
    }

    static Message[] searchRaw(IMAPFolder folder, String query) throws MessagingException {
        try {
            Object result = folder.doOptionalCommand("X-GM-RAW", protocol -> {
                Argument args = new Argument();
                args.writeAtom("X-GM-RAW");
                args.writeString(query);

                Response[] responses = protocol.command("UID SEARCH", args);
                Response status = responses[responses.length - 1];
                if (status.isBAD() || status.isNO()) {
                    throw new RuntimeException(new MessagingException("Gmail RAW search failed: " + status));
                }

                List<Long> uids = new ArrayList<>();
                for (Response response : responses) {
                    if (response instanceof IMAPResponse imapResponse && imapResponse.keyEquals("SEARCH")) {
                        while (true) {
                            long uid = imapResponse.readLong();
                            if (uid == -1) {
                                break;
                            }
                            uids.add(uid);
                        }
                    }
                }

                if (uids.isEmpty()) {
                    return new Message[0];
                }

                long[] uidArray = uids.stream().mapToLong(Long::longValue).toArray();
                try {
                    return folder.getMessagesByUID(uidArray);
                } catch (MessagingException exception) {
                    throw new RuntimeException(exception);
                }
            });
            return result instanceof Message[] messages ? messages : null;
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof MessagingException messagingException) {
                throw messagingException;
            }
            throw exception;
        }
    }
}
