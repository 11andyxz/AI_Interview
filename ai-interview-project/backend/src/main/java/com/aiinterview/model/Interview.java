package com.aiinterview.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "interview")
public class Interview {

    @Id
    private String id;

    @Column(name = "candidate_id")
    private Integer candidateId;

    private String title; // e.g., "Internet / AI / Artificial Intelligence"
    private String language; // e.g., "English"
    @Column(name = "tech_stack")
    private String techStack; // e.g., "JavaScript, Python, Java, Kotlin"

    @Column(name = "programming_languages", columnDefinition = "json")
    private String programmingLanguages; // stored as JSON string

    private LocalDate date;
    private String status; // e.g., "Completed", "Scheduled"

    @Column(name = "use_custom_knowledge")
    private boolean useCustomKnowledge;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (date == null) {
            date = LocalDate.now();
        }
        if (status == null) {
            status = "In Progress";
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors for compatibility
    public Interview(String id, String title, String language, String techStack, LocalDate date, String status) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.techStack = techStack;
        this.date = date;
        this.status = status;
    }

    public Interview() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Integer candidateId) {
        this.candidateId = candidateId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTechStack() {
        return techStack;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public String getProgrammingLanguages() {
        return programmingLanguages;
    }

    public void setProgrammingLanguages(String programmingLanguages) {
        this.programmingLanguages = programmingLanguages;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUseCustomKnowledge() {
        return useCustomKnowledge;
    }

    public void setUseCustomKnowledge(boolean useCustomKnowledge) {
        this.useCustomKnowledge = useCustomKnowledge;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

