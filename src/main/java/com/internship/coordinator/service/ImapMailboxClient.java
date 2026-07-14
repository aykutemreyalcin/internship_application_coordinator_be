package com.internship.coordinator.service;

import com.internship.coordinator.config.EmailIntakeProperties;
import com.internship.coordinator.dto.IncomingEmailAttachment;
import com.internship.coordinator.dto.IncomingEmailMessage;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.SortTerm;
import org.springframework.util.StringUtils;

@Slf4j
public class ImapMailboxClient implements MailboxClient {

    private static final String DEFAULT_GMAIL_RAW_SEARCH_QUERY = "is:unread has:attachment filename:pdf";
    private static final String BROAD_GMAIL_RAW_SEARCH_QUERY = "is:unread has:attachment newer_than:180d";

    private final EmailIntakeProperties.Imap imap;

    public ImapMailboxClient(EmailIntakeProperties.Imap imap) {
        this.imap = imap;
    }

    @Override
    public List<IncomingEmailMessage> fetchUnreadPdfMessages() {
        try {
            return withFolder(Folder.READ_WRITE, this::readUnreadPdfMessages);
        } catch (MessagingException exception) {
            throw new EmailIntakeException("Failed to fetch unread email messages", exception);
        }
    }

    @Override
    public void markAsProcessed(long uid) {
        try {
            withFolder(Folder.READ_WRITE, folder -> {
                UIDFolder uidFolder = toUidFolder(folder);
                Message message = uidFolder.getMessageByUID(uid);
                if (message != null) {
                    message.setFlag(Flags.Flag.SEEN, true);
                }
                return null;
            });
        } catch (MessagingException exception) {
            throw new EmailIntakeException("Failed to mark email as processed: uid=" + uid, exception);
        }
    }

    private UIDFolder toUidFolder(Folder folder) throws MessagingException {
        if (!(folder instanceof UIDFolder uidFolder)) {
            throw new EmailIntakeException("IMAP folder does not support UID operations: " + folder.getFullName());
        }
        return uidFolder;
    }

    private List<IncomingEmailMessage> readUnreadPdfMessages(Folder folder) throws MessagingException {
        UIDFolder uidFolder = toUidFolder(folder);
        Message[] candidates = findCandidateMessages(folder);
        if (candidates.length == 0) {
            log.debug("No candidate unread messages in IMAP folder {}", folder.getFullName());
            return List.of();
        }

        int scanLimit = Math.max(1, imap.maxUnreadMessagesPerPoll());
        Message[] toScan = selectNewestMessages(candidates, scanLimit, folder);
        if (candidates.length > toScan.length) {
            log.info(
                    "Scanning newest {} of {} candidate unread messages in {} (increase EMAIL_INTAKE_MAX_UNREAD_PER_POLL to scan more)",
                    toScan.length,
                    candidates.length,
                    folder.getFullName());
        } else {
            log.debug("Scanning {} candidate unread messages in {}", toScan.length, folder.getFullName());
        }

        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.CONTENT_INFO);
        profile.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(toScan, profile);

        List<IncomingEmailMessage> results = new ArrayList<>();
        for (Message message : toScan) {
            IncomingEmailAttachment pdfAttachment = findFirstPdfAttachment(message);
            if (pdfAttachment == null) {
                continue;
            }

            results.add(new IncomingEmailMessage(
                    uidFolder.getUID(message),
                    resolveMessageId(message),
                    resolveSender(message),
                    message.getSubject(),
                    pdfAttachment));
        }

        log.info("Found {} unread PDF message(s) in {}", results.size(), folder.getFullName());
        return results;
    }

    private Message[] findCandidateMessages(Folder folder) throws MessagingException {
        if (folder instanceof IMAPFolder imapFolder && GmailImapSupport.isGmailHost(imap.host())) {
            String query = StringUtils.hasText(imap.gmailRawSearchQuery())
                    ? imap.gmailRawSearchQuery()
                    : DEFAULT_GMAIL_RAW_SEARCH_QUERY;
            try {
                Message[] gmailMatches = GmailImapSupport.searchRaw(imapFolder, query);
                if (gmailMatches != null) {
                    log.debug("Gmail RAW search '{}' matched {} message(s)", query, gmailMatches.length);
                    if (gmailMatches.length == 0 && query.contains("filename:pdf")) {
                        Message[] broaderMatches = GmailImapSupport.searchRaw(imapFolder, BROAD_GMAIL_RAW_SEARCH_QUERY);
                        if (broaderMatches != null && broaderMatches.length > 0) {
                            log.debug(
                                    "Gmail RAW fallback '{}' matched {} message(s)",
                                    BROAD_GMAIL_RAW_SEARCH_QUERY,
                                    broaderMatches.length);
                            return broaderMatches;
                        }
                    }
                    return gmailMatches;
                }
            } catch (MessagingException exception) {
                log.warn("Gmail RAW search failed, falling back to IMAP UNSEEN search", exception);
            }
        }

        SearchTerm unread = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        if (folder instanceof IMAPFolder imapFolder) {
            try {
                return imapFolder.getSortedMessages(new SortTerm[] {SortTerm.REVERSE, SortTerm.ARRIVAL}, unread);
            } catch (MessagingException exception) {
                log.warn("IMAP SORT unavailable, scanning recent mailbox window for unread messages", exception);
                return findRecentUnreadMessages(folder, Math.max(imap.maxUnreadMessagesPerPoll() * 10, 500));
            }
        }
        return folder.search(unread);
    }

    private Message[] findRecentUnreadMessages(Folder folder, int windowSize) throws MessagingException {
        int messageCount = folder.getMessageCount();
        if (messageCount == 0) {
            return new Message[0];
        }

        int window = Math.min(messageCount, Math.max(windowSize, 1));
        int start = messageCount - window + 1;
        Message[] recentMessages = folder.getMessages(start, messageCount);

        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        folder.fetch(recentMessages, profile);

        List<Message> unreadMessages = new ArrayList<>();
        for (int index = recentMessages.length - 1; index >= 0; index--) {
            Message message = recentMessages[index];
            if (!message.isSet(Flags.Flag.SEEN)) {
                unreadMessages.add(message);
            }
        }

        log.debug(
                "Found {} unread message(s) in the newest {} messages of {}",
                unreadMessages.size(),
                window,
                folder.getFullName());
        return unreadMessages.toArray(Message[]::new);
    }

    private Message[] selectNewestMessages(Message[] messages, int scanLimit, Folder folder) throws MessagingException {
        if (messages.length <= scanLimit) {
            return messages;
        }

        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        folder.fetch(messages, profile);
        Message[] sorted = messages.clone();
        Arrays.sort(sorted, Comparator.comparing(this::messageDateSafe, Comparator.nullsLast(Comparator.reverseOrder())));
        return Arrays.copyOf(sorted, scanLimit);
    }

    private Date messageDateSafe(Message message) {
        try {
            Date received = message.getReceivedDate();
            if (received != null) {
                return received;
            }
            return message.getSentDate();
        } catch (MessagingException exception) {
            return null;
        }
    }

    private IncomingEmailAttachment findFirstPdfAttachment(Part part) throws MessagingException {
        if (isPdfPart(part)) {
            String fileName = resolvePdfFileName(part);
            try {
                byte[] content = readAllBytes(part.getInputStream());
                return new IncomingEmailAttachment(fileName, content);
            } catch (IOException exception) {
                throw new EmailIntakeException("Failed to read PDF attachment: " + fileName, exception);
            }
        }

        if (part.isMimeType("multipart/*")) {
            Multipart multipart;
            try {
                multipart = (Multipart) part.getContent();
            } catch (IOException exception) {
                throw new EmailIntakeException("Failed to read multipart email content", exception);
            }
            for (int index = 0; index < multipart.getCount(); index++) {
                IncomingEmailAttachment attachment = findFirstPdfAttachment(multipart.getBodyPart(index));
                if (attachment != null) {
                    return attachment;
                }
            }
        } else if (part.isMimeType("message/rfc822")) {
            try {
                return findFirstPdfAttachment((Part) part.getContent());
            } catch (IOException exception) {
                throw new EmailIntakeException("Failed to read forwarded email content", exception);
            }
        }

        return null;
    }

    private boolean isPdfPart(Part part) throws MessagingException {
        if (part.isMimeType("application/pdf") || part.isMimeType("application/x-pdf")) {
            return true;
        }

        String fileName = decodeFileName(part.getFileName());
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String resolvePdfFileName(Part part) throws MessagingException {
        String fileName = decodeFileName(part.getFileName());
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return "attachment.pdf";
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (inputStream) {
            return inputStream.readAllBytes();
        }
    }

    private String decodeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(fileName);
        } catch (Exception exception) {
            return fileName;
        }
    }

    private String resolveMessageId(Message message) throws MessagingException {
        String[] messageIds = message.getHeader("Message-ID");
        if (messageIds != null && messageIds.length > 0 && messageIds[0] != null && !messageIds[0].isBlank()) {
            return messageIds[0].trim();
        }
        return "uid-" + message.getMessageNumber() + "@" + imap.folder();
    }

    private String resolveSender(Message message) throws MessagingException {
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses == null || fromAddresses.length == 0) {
            return "(unknown)";
        }
        return fromAddresses[0].toString();
    }

    private <T> T withFolder(int mode, FolderCallback<T> callback) throws MessagingException {
        Store store = null;
        Folder folder = null;
        try {
            Session session = Session.getInstance(createSessionProperties());
            store = session.getStore("imaps");
            log.debug("Connecting to IMAP {}:{} folder={}", imap.host(), imap.port(), imap.folder());
            store.connect(imap.host(), imap.port(), imap.username(), imap.password());
            folder = store.getFolder(imap.folder());
            folder.open(mode);
            return callback.apply(folder);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private Properties createSessionProperties() {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.host());
        properties.put("mail.imaps.port", String.valueOf(imap.port()));
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.connectiontimeout", String.valueOf(imap.connectionTimeoutMs()));
        properties.put("mail.imaps.timeout", String.valueOf(imap.readTimeoutMs()));
        properties.put("mail.imaps.writetimeout", String.valueOf(imap.readTimeoutMs()));
        return properties;
    }

    private void closeQuietly(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (MessagingException exception) {
            log.warn("Failed to close IMAP folder", exception);
        }
    }

    private void closeQuietly(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException exception) {
            log.warn("Failed to close IMAP store", exception);
        }
    }

    @FunctionalInterface
    private interface FolderCallback<T> {
        T apply(Folder folder) throws MessagingException;
    }
}
