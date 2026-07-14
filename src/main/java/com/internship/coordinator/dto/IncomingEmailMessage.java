package com.internship.coordinator.dto;

public record IncomingEmailMessage(long uid, String messageId, String sender, String subject, IncomingEmailAttachment pdfAttachment) {}
