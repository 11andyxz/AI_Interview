package com.aiinterview.service;

import com.aiinterview.model.UserPreferences;
import com.aiinterview.repository.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    private UserPreferences testPreferences;

    @BeforeEach
    void setUp() {
        testPreferences = new UserPreferences();
        testPreferences.setId(1L);
        testPreferences.setUserId(1L);
        testPreferences.setTheme("dark");
        testPreferences.setLanguage("en");
        testPreferences.setEmailNotifications(true);
    }

    @Test
    void getUserPreferences_ExistingPreferences() {
        // Given
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.of(testPreferences));

        // When
        UserPreferences result = userPreferencesService.getUserPreferences(1L);

        // Then
        assertNotNull(result);
        assertEquals("dark", result.getTheme());
        assertEquals("en", result.getLanguage());
        verify(preferencesRepository).findByUserId(1L);
    }

    @Test
    void getUserPreferences_NewUser() {
        // Given
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(preferencesRepository.save(any(UserPreferences.class))).thenReturn(new UserPreferences());

        // When
        UserPreferences result = userPreferencesService.getUserPreferences(1L);

        // Then
        assertNotNull(result);
        verify(preferencesRepository).save(any(UserPreferences.class));
    }

    @Test
    void updateUserPreferences_Success() {
        // Given
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.of(testPreferences));
        when(preferencesRepository.save(any(UserPreferences.class))).thenReturn(testPreferences);

        Map<String, Object> updates = Map.of(
            "theme", "light",
            "language", "zh",
            "emailNotifications", false
        );

        // When
        UserPreferences result = userPreferencesService.updateUserPreferences(1L, updates);

        // Then
        assertNotNull(result);
        verify(preferencesRepository).save(argThat(prefs -> {
            return "light".equals(prefs.getTheme()) &&
                   "zh".equals(prefs.getLanguage()) &&
                   !prefs.getEmailNotifications();
        }));
    }

    @Test
    void updateUserPreferences_PartialUpdate() {
        // Given
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.of(testPreferences));
        when(preferencesRepository.save(any(UserPreferences.class))).thenReturn(testPreferences);

        Map<String, Object> updates = Map.of("theme", "light");

        // When
        UserPreferences result = userPreferencesService.updateUserPreferences(1L, updates);

        // Then
        assertNotNull(result);
        verify(preferencesRepository).save(argThat(prefs ->
            "light".equals(prefs.getTheme()) &&
            "en".equals(prefs.getLanguage()) // Should remain unchanged
        ));
    }

    @Test
    void resetUserPreferences_Success() {
        // Given
        UserPreferences existingPrefs = new UserPreferences();
        existingPrefs.setTheme("dark");

        UserPreferences defaultPrefs = new UserPreferences();
        defaultPrefs.setTheme("light"); // Default theme

        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.of(existingPrefs));
        when(preferencesRepository.save(any(UserPreferences.class))).thenReturn(defaultPrefs);

        // When
        UserPreferences result = userPreferencesService.resetUserPreferences(1L);

        // Then
        assertNotNull(result);
        assertEquals("light", result.getTheme());
        verify(preferencesRepository).delete(existingPrefs);
        verify(preferencesRepository).save(any(UserPreferences.class));
    }

    @Test
    void resetUserPreferences_NoExistingPreferences() {
        // Given
        UserPreferences defaultPrefs = new UserPreferences();
        defaultPrefs.setTheme("light");

        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(preferencesRepository.save(any(UserPreferences.class))).thenReturn(defaultPrefs);

        // When
        UserPreferences result = userPreferencesService.resetUserPreferences(1L);

        // Then
        assertNotNull(result);
        assertEquals("light", result.getTheme());
        verify(preferencesRepository, never()).delete(any());
        verify(preferencesRepository).save(any(UserPreferences.class));
    }
}
