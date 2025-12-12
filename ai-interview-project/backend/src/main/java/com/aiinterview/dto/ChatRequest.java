package com.aiinterview.dto;

import java.util.List;

public class ChatRequest {
    private String userMessage;
    private String language;
    private List<QAHistory> recentHistory;

    public ChatRequest() {
    }

    public ChatRequest(String userMessage, String language, List<QAHistory> recentHistory) {
        this.userMessage = userMessage;
        this.language = language;
        this.recentHistory = recentHistory;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<QAHistory> getRecentHistory() {
        return recentHistory;
    }

    public void setRecentHistory(List<QAHistory> recentHistory) {
        this.recentHistory = recentHistory;
    }
}
