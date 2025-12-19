package com.aiinterview.dto;

public class StreamResponse {
    private String interviewId;
    private String content;
    private boolean isComplete;
    private String type; // "chunk" or "complete"

    public StreamResponse() {
    }

    public StreamResponse(String interviewId, String content, boolean isComplete, String type) {
        this.interviewId = interviewId;
        this.content = content;
        this.isComplete = isComplete;
        this.type = type;
    }

    public String getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

