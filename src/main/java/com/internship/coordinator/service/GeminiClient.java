package com.internship.coordinator.service;

public interface GeminiClient {

    String generateText(String prompt);

    String generateFromPdf(byte[] pdfBytes, String prompt);
}
