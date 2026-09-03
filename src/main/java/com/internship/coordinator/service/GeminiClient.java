package com.internship.coordinator.service;

public interface GeminiClient {

    String generateText(String prompt);

    String generateJson(String prompt);

    String generateFromPdf(byte[] pdfBytes, String prompt);

    String generateFromDocx(byte[] docxBytes, String prompt);

    String generateJsonFromDocumentText(String documentText, String prompt);
}
