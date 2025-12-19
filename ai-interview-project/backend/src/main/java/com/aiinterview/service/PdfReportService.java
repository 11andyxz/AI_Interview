package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Interview;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PdfReportService {

    @Autowired
    private ReportService reportService;

    /**
     * Generate PDF report for an interview
     */
    public byte[] generatePdfReport(String interviewId) throws IOException {
        Map<String, Object> reportData = reportService.generateReport(interviewId);
        Interview interview = getInterviewFromReport(reportData);

        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        try {
                // Set fonts
                var fontTitle = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                var fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                var fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;

                // Title
                contentStream.setFont(fontTitle, 18);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("AI Interview Report");
                contentStream.endText();

                yPosition -= 40;

                // Interview Information Section
                contentStream.setFont(fontBold, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Interview Information");
                contentStream.endText();

                yPosition -= 25;
                contentStream.setFont(fontNormal, 12);

                // Interview details
                String title = (String) reportData.getOrDefault("title", "N/A");
                String status = (String) reportData.getOrDefault("status", "N/A");
                String date = formatDate(reportData.get("date"));
                String language = (String) reportData.getOrDefault("language", "N/A");
                String techStack = (String) reportData.getOrDefault("techStack", "N/A");

                yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition, "Title:", title);
                yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition, "Status:", status);
                yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition, "Date:", date);
                yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition, "Language:", language);
                yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition, "Tech Stack:", techStack);

                // Statistics Section
                yPosition -= 20;
                contentStream.setFont(fontBold, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Statistics");
                contentStream.endText();

                yPosition -= 25;
                contentStream.setFont(fontNormal, 12);

                @SuppressWarnings("unchecked")
                Map<String, Object> statistics = (Map<String, Object>) reportData.get("statistics");
                if (statistics != null) {
                    Integer totalExchanges = (Integer) statistics.getOrDefault("totalExchanges", 0);
                    Object avgLength = statistics.get("averageAnswerLength");

                    yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition,
                        "Total Questions:", totalExchanges.toString());
                    yPosition = addTextLine(contentStream, fontBold, fontNormal, margin, yPosition,
                        "Average Answer Length:", avgLength != null ? String.format("%.0f characters", avgLength) : "N/A");
                }

                // Feedback Section
                yPosition -= 20;
                if (yPosition < 200) { // Check if we need a new page
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = page.getMediaBox().getHeight() - margin;
                }

                contentStream.setFont(fontBold, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("AI Feedback");
                contentStream.endText();

                yPosition -= 25;
                contentStream.setFont(fontNormal, 12);

                String feedback = (String) reportData.getOrDefault("feedback", "No feedback available");
                yPosition = addWrappedText(contentStream, fontNormal, margin, yPosition, feedback, 500);

                // Conversation History Section
                yPosition -= 20;
                contentStream.setFont(fontBold, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Conversation History");
                contentStream.endText();

                yPosition -= 25;
                contentStream.setFont(fontNormal, 12);

                @SuppressWarnings("unchecked")
                List<QAHistory> history = (List<QAHistory>) reportData.get("conversationHistory");
                if (history != null && !history.isEmpty()) {
                    for (int i = 0; i < history.size() && i < 10; i++) { // Limit to first 10 Q&As for PDF
                        QAHistory qa = history.get(i);

                        if (yPosition < 150) { // Check if we need a new page
                            contentStream.close();
                            page = new PDPage();
                            document.addPage(page);
                            contentStream = new PDPageContentStream(document, page);
                            yPosition = page.getMediaBox().getHeight() - margin;
                        }

                        contentStream.setFont(fontBold, 11);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(margin, yPosition);
                        contentStream.showText("Q" + (i + 1) + ": " + truncateText(qa.getQuestionText(), 80));
                        contentStream.endText();

                        yPosition -= 18;
                        contentStream.setFont(fontNormal, 10);
                        String answer = qa.getAnswerText() != null ? qa.getAnswerText() : "No answer provided";
                        yPosition = addWrappedText(contentStream, fontNormal, margin + 20, yPosition,
                            "A: " + truncateText(answer, 200), 450);

                        yPosition -= 15;
                    }
                } else {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("No conversation history available");
                    contentStream.endText();
                }

            // Close the last content stream
            contentStream.close();

            // Save to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            document.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Ensure resources are closed on error
            try {
                if (contentStream != null) {
                    contentStream.close();
                }
            } catch (IOException ignored) {}
            try {
                if (document != null) {
                    document.close();
                }
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private float addTextLine(PDPageContentStream contentStream, PDType1Font fontBold, PDType1Font fontNormal,
                           float margin, float yPosition, String label, String value) throws IOException {
        contentStream.setFont(fontBold, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText(label);
        contentStream.endText();

        contentStream.setFont(fontNormal, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(margin + 80, yPosition);
        contentStream.showText(value != null ? value : "N/A");
        contentStream.endText();

        return yPosition - 18;
    }

    private float addWrappedText(PDPageContentStream contentStream, PDType1Font font, float margin,
                               float yPosition, String text, float maxWidth) throws IOException {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float leading = 15;

        for (String word : words) {
            String testLine = line + word + " ";
            float textWidth = getTextWidth(testLine, font, 12);

            if (textWidth > maxWidth && line.length() > 0) {
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(line.toString().trim());
                contentStream.endText();
                yPosition -= leading;
                line = new StringBuilder(word + " ");
            } else {
                line.append(word).append(" ");
            }
        }

        if (line.length() > 0) {
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText(line.toString().trim());
            contentStream.endText();
            yPosition -= leading;
        }

        return yPosition;
    }

    private float getTextWidth(String text, PDType1Font font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private String formatDate(Object dateObj) {
        if (dateObj == null) return "N/A";
        if (dateObj instanceof String) {
            return (String) dateObj;
        }
        // Handle LocalDate or other date types
        return dateObj.toString();
    }

    private Interview getInterviewFromReport(Map<String, Object> reportData) {
        // This is a simplified version - in real implementation you'd fetch the Interview entity
        Interview interview = new Interview();
        interview.setTitle((String) reportData.get("title"));
        interview.setLanguage((String) reportData.get("language"));
        interview.setTechStack((String) reportData.get("techStack"));
        return interview;
    }
}
