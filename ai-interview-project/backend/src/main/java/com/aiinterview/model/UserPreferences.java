package com.aiinterview.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // Interview preferences
    @Column(name = "default_language")
    private String defaultLanguage = "en";

    @Column(name = "default_tech_stack")
    private String defaultTechStack = "Java";

    @Column(name = "default_level")
    private String defaultLevel = "mid";

    // Notification preferences
    @Column(name = "email_notifications")
    private Boolean emailNotifications = true;

    @Column(name = "interview_reminders")
    private Boolean interviewReminders = true;

    @Column(name = "weekly_reports")
    private Boolean weeklyReports = true;

    @Column(name = "progress_updates")
    private Boolean progressUpdates = true;

    // UI preferences
    @Column(name = "theme")
    private String theme = "light"; // light, dark, auto

    @Column(name = "language")
    private String language = "en";

    @Column(name = "timezone")
    private String timezone = "UTC";

    // Interview settings
    @Column(name = "auto_save")
    private Boolean autoSave = true;

    @Column(name = "show_hints")
    private Boolean showHints = true;

    @Column(name = "voice_enabled")
    private Boolean voiceEnabled = true;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Default constructor
    public UserPreferences() {}

    // Constructor with userId
    public UserPreferences(Long userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String getDefaultTechStack() {
        return defaultTechStack;
    }

    public void setDefaultTechStack(String defaultTechStack) {
        this.defaultTechStack = defaultTechStack;
    }

    public String getDefaultLevel() {
        return defaultLevel;
    }

    public void setDefaultLevel(String defaultLevel) {
        this.defaultLevel = defaultLevel;
    }

    public Boolean getEmailNotifications() {
        return emailNotifications;
    }

    public void setEmailNotifications(Boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public Boolean getInterviewReminders() {
        return interviewReminders;
    }

    public void setInterviewReminders(Boolean interviewReminders) {
        this.interviewReminders = interviewReminders;
    }

    public Boolean getWeeklyReports() {
        return weeklyReports;
    }

    public void setWeeklyReports(Boolean weeklyReports) {
        this.weeklyReports = weeklyReports;
    }

    public Boolean getProgressUpdates() {
        return progressUpdates;
    }

    public void setProgressUpdates(Boolean progressUpdates) {
        this.progressUpdates = progressUpdates;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Boolean getAutoSave() {
        return autoSave;
    }

    public void setAutoSave(Boolean autoSave) {
        this.autoSave = autoSave;
    }

    public Boolean getShowHints() {
        return showHints;
    }

    public void setShowHints(Boolean showHints) {
        this.showHints = showHints;
    }

    public Boolean getVoiceEnabled() {
        return voiceEnabled;
    }

    public void setVoiceEnabled(Boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
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
