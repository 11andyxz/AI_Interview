package com.aiinterview.session.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InterviewSession {
    private String id;
    private String roleId;
    private String level;
    private List<String> skills = new ArrayList<>();
    private List<QAHistory> history = new ArrayList<>();
    private String status; // ACTIVE / COMPLETED
    private LocalDateTime createdAt;

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
}

