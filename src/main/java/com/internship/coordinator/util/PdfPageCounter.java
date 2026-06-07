package com.internship.coordinator.util;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

@Component
public class PdfPageCounter {

    public int countPages(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read PDF page count", exception);
        }
    }
}
