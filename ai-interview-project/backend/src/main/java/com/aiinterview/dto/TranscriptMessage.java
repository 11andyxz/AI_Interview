package com.aiinterview.dto;

public class TranscriptMessage {
    private String interviewId;
    private String text;
    private String language;
    private boolean isFinal;

    public TranscriptMessage() {
    }

    public TranscriptMessage(String interviewId, String text, String language, boolean isFinal) {
        this.interviewId = interviewId;
        this.text = text;
        this.language = language;
        this.isFinal = isFinal;
    }

    public String getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}

