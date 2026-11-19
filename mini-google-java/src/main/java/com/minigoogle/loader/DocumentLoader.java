package com.minigoogle.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

/**
 * Utility to load document content from various file types.
 * Supported extensions: txt, md, pdf, docx.
 */
public class DocumentLoader {
    public static String load(InputStream is, String fileName) throws IOException {
        String ext = getExtension(fileName).toLowerCase();
        switch (ext) {
            case "txt":
            case "md":
                // plain text (UTF-8)
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            case "pdf":
                // In PDFBox 3.x, use Loader.loadPDF
                byte[] bytes = is.readAllBytes();
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            case "docx":
                try (XWPFDocument doc = new XWPFDocument(is);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    return extractor.getText();
                }
            default:
                throw new IllegalArgumentException("Unsupported file type: " + ext);
        }
    }

    private static String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return (i == -1) ? "" : name.substring(i + 1);
    }
}
