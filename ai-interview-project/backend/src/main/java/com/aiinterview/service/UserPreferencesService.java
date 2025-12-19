package com.aiinterview.service;

import com.aiinterview.model.UserPreferences;
import com.aiinterview.repository.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class UserPreferencesService {

    @Autowired
    private UserPreferencesRepository userPreferencesRepository;

    /**
     * Get user preferences for a user, creating default if not exists
     */
    public UserPreferences getUserPreferences(Long userId) {
        Optional<UserPreferences> preferences = userPreferencesRepository.findByUserId(userId);
        if (preferences.isPresent()) {
            return preferences.get();
        }

        // Create default preferences
        UserPreferences defaultPrefs = new UserPreferences(userId);
        return userPreferencesRepository.save(defaultPrefs);
    }

    /**
     * Update user preferences
     */
    @Transactional
    public UserPreferences updateUserPreferences(Long userId, Map<String, Object> updates) {
        UserPreferences preferences = getUserPreferences(userId);

        // Update interview preferences
        if (updates.containsKey("defaultLanguage")) {
            preferences.setDefaultLanguage((String) updates.get("defaultLanguage"));
        }
        if (updates.containsKey("defaultTechStack")) {
            preferences.setDefaultTechStack((String) updates.get("defaultTechStack"));
        }
        if (updates.containsKey("defaultLevel")) {
            preferences.setDefaultLevel((String) updates.get("defaultLevel"));
        }

        // Update notification preferences
        if (updates.containsKey("emailNotifications")) {
            preferences.setEmailNotifications((Boolean) updates.get("emailNotifications"));
        }
        if (updates.containsKey("interviewReminders")) {
            preferences.setInterviewReminders((Boolean) updates.get("interviewReminders"));
        }
        if (updates.containsKey("weeklyReports")) {
            preferences.setWeeklyReports((Boolean) updates.get("weeklyReports"));
        }
        if (updates.containsKey("progressUpdates")) {
            preferences.setProgressUpdates((Boolean) updates.get("progressUpdates"));
        }

        // Update UI preferences
        if (updates.containsKey("theme")) {
            preferences.setTheme((String) updates.get("theme"));
        }
        if (updates.containsKey("language")) {
            preferences.setLanguage((String) updates.get("language"));
        }
        if (updates.containsKey("timezone")) {
            preferences.setTimezone((String) updates.get("timezone"));
        }

        // Update interview settings
        if (updates.containsKey("autoSave")) {
            preferences.setAutoSave((Boolean) updates.get("autoSave"));
        }
        if (updates.containsKey("showHints")) {
            preferences.setShowHints((Boolean) updates.get("showHints"));
        }
        if (updates.containsKey("voiceEnabled")) {
            preferences.setVoiceEnabled((Boolean) updates.get("voiceEnabled"));
        }

        return userPreferencesRepository.save(preferences);
    }

    /**
     * Reset user preferences to defaults
     */
    @Transactional
    public UserPreferences resetUserPreferences(Long userId) {
        Optional<UserPreferences> existing = userPreferencesRepository.findByUserId(userId);
        if (existing.isPresent()) {
            userPreferencesRepository.delete(existing.get());
        }

        // Create new default preferences
        UserPreferences defaultPrefs = new UserPreferences(userId);
        return userPreferencesRepository.save(defaultPrefs);
    }
}
