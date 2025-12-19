package com.aiinterview.service;

import com.aiinterview.model.InterviewRecording;
import com.aiinterview.repository.InterviewRecordingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private InterviewRecordingRepository recordingRepository;

    @InjectMocks
    private AudioService audioService;

    private MockMultipartFile validAudioFile;
    private MockMultipartFile invalidFile;

    @BeforeEach
    void setUp() {
        // Set the upload directory to temp directory
        ReflectionTestUtils.setField(audioService, "uploadDir", tempDir.toString());

        // Create mock files
        validAudioFile = new MockMultipartFile(
            "audio",
            "test.webm",
            "audio/webm",
            "audio content".getBytes()
        );

        invalidFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "text content".getBytes()
        );
    }

    @Test
    void saveAudioFile_Success() throws IOException {
        // Given
        InterviewRecording savedRecording = new InterviewRecording();
        savedRecording.setId(1L);
        when(recordingRepository.save(any(InterviewRecording.class))).thenReturn(savedRecording);

        // When
        InterviewRecording result = audioService.saveAudioFile(validAudioFile, "interview1", 1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(recordingRepository).save(any(InterviewRecording.class));

        // Verify file was created
        assertTrue(Files.exists(tempDir));
        assertTrue(Files.list(tempDir).count() > 0);
    }

    @Test
    void saveAudioFile_InvalidFileType() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            audioService.saveAudioFile(invalidFile, "interview1", 1L)
        );
        assertTrue(exception.getMessage().contains("Invalid file type"));
    }

    @Test
    void saveAudioFile_FileTooLarge() {
        // Given
        byte[] largeContent = new byte[60 * 1024 * 1024]; // 60MB
        MockMultipartFile largeFile = new MockMultipartFile(
            "audio",
            "large.webm",
            "audio/webm",
            largeContent
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            audioService.saveAudioFile(largeFile, "interview1", 1L)
        );
        assertTrue(exception.getMessage().contains("File size exceeds maximum limit"));
    }

    @Test
    void saveAudioBlob_Success() throws IOException {
        // Given
        byte[] audioData = "audio content".getBytes();
        InterviewRecording savedRecording = new InterviewRecording();
        savedRecording.setId(1L);
        when(recordingRepository.save(any(InterviewRecording.class))).thenReturn(savedRecording);

        // When
        InterviewRecording result = audioService.saveAudioBlob(audioData, "interview1", 1L, "recording.webm", 120);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(120, result.getDurationSeconds());
        verify(recordingRepository).save(any(InterviewRecording.class));
    }

    @Test
    void getRecordingsForInterview_Success() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        List<InterviewRecording> recordings = List.of(recording);
        when(recordingRepository.findByInterviewId("interview1")).thenReturn(recordings);

        // When
        List<InterviewRecording> result = audioService.getRecordingsForInterview("interview1");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getRecordingsForUser_Success() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        List<InterviewRecording> recordings = List.of(recording);
        when(recordingRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(recordings);

        // When
        List<InterviewRecording> result = audioService.getRecordingsForUser(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getRecordingById_Success() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

        // When
        Optional<InterviewRecording> result = audioService.getRecordingById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(recording, result.get());
    }

    @Test
    void deleteRecording_Success() throws IOException {
        // Given
        InterviewRecording recording = new InterviewRecording();
        recording.setId(1L);
        recording.setUserId(1L);
        recording.setFilePath(tempDir.resolve("test.webm").toString());

        // Create the file
        Files.write(tempDir.resolve("test.webm"), "content".getBytes());

        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

        // When
        audioService.deleteRecording(1L, 1L);

        // Then
        verify(recordingRepository).delete(recording);
        assertFalse(Files.exists(tempDir.resolve("test.webm")));
    }

    @Test
    void deleteRecording_WrongUser() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        recording.setUserId(2L); // Different user
        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

        // When & Then
        assertThrows(RuntimeException.class, () ->
            audioService.deleteRecording(1L, 1L)
        );
        verify(recordingRepository, never()).delete(any());
    }

    @Test
    void getAudioFile_Success() throws IOException {
        // Given
        InterviewRecording recording = new InterviewRecording();
        recording.setFilePath(tempDir.resolve("test.webm").toString());
        byte[] expectedContent = "audio content".getBytes();

        // Create the file
        Files.write(tempDir.resolve("test.webm"), expectedContent);

        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

        // When
        byte[] result = audioService.getAudioFile(1L);

        // Then
        assertNotNull(result);
        assertArrayEquals(expectedContent, result);
    }

    @Test
    void getAudioFile_NotFound() {
        // Given
        when(recordingRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        IOException exception = assertThrows(IOException.class, () ->
            audioService.getAudioFile(1L)
        );
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void updateRecordingMetadata_Success() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(InterviewRecording.class))).thenReturn(recording);

        // When
        InterviewRecording result = audioService.updateRecordingMetadata(1L, 120, "completed", null);

        // Then
        assertNotNull(result);
        verify(recordingRepository).save(argThat(r ->
            r.getDurationSeconds() == 120 &&
            "completed".equals(r.getStatus())
        ));
    }

    @Test
    void incrementUsageCount_Success() {
        // Given
        InterviewRecording recording = new InterviewRecording();
        recording.setUsageCount(5);
        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));
        when(recordingRepository.save(any(InterviewRecording.class))).thenReturn(recording);

        // When
        audioService.incrementUsageCount(1L);

        // Then
        verify(recordingRepository).save(argThat(r -> r.getUsageCount() == 6));
    }

    @Test
    void getSupportedFormats_ReturnsList() {
        // When
        List<String> formats = audioService.getSupportedFormats();

        // Then
        assertNotNull(formats);
        assertTrue(formats.contains("webm"));
        assertTrue(formats.contains("mp3"));
        assertTrue(formats.contains("wav"));
    }
}
