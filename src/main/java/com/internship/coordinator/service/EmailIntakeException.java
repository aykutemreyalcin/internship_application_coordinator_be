package com.internship.coordinator.service;

public class EmailIntakeException extends RuntimeException {

    public EmailIntakeException(String message) {
        super(message);
    }

    public EmailIntakeException(String message, Throwable cause) {
        super(message, cause);
    }
}
