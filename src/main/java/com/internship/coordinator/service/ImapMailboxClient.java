package com.internship.coordinator.service;

import com.internship.coordinator.config.EmailIntakeProperties;
import com.internship.coordinator.dto.IncomingEmailAttachment;
import com.internship.coordinator.dto.IncomingEmailMessage;
import jakarta.mail.Address;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImapMailboxClient implements MailboxClient {

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
        Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        List<IncomingEmailMessage> results = new ArrayList<>();

        for (Message message : messages) {
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

        return results;
    }

    private IncomingEmailAttachment findFirstPdfAttachment(Part part) throws MessagingException {
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
            return null;
        }

        String fileName = decodeFileName(part.getFileName());
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return null;
        }

        try {
            byte[] content = readAllBytes(part.getInputStream());
            return new IncomingEmailAttachment(fileName, content);
        } catch (IOException exception) {
            throw new EmailIntakeException("Failed to read PDF attachment: " + fileName, exception);
        }
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
