package com.aiinterview.session.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InterviewSession {
    private String id;
    private String roleId;
    private String level;
    private List<String> skills = new ArrayList<>();
    private List<QAHistory> history = new ArrayList<>();
    private String status; // ACTIVE / COMPLETED
    private LocalDateTime createdAt;
    
    // Enhanced fields for OpenAI integration
    private String candidateId;
    private Map<String, Object> candidateInfo; // Store candidate background
    private List<Map<String, String>> messages = new ArrayList<>(); // Store OpenAI message history

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<QAHistory> getHistory() {
        return history;
    }

    public void setHistory(List<QAHistory> history) {
        this.history = history;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public Map<String, Object> getCandidateInfo() {
        return candidateInfo;
    }

    public void setCandidateInfo(Map<String, Object> candidateInfo) {
        this.candidateInfo = candidateInfo;
    }

    public List<Map<String, String>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, String>> messages) {
        this.messages = messages;
    }
}

