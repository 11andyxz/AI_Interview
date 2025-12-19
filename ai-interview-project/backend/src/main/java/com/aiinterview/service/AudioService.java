package com.aiinterview.service;

import com.aiinterview.model.InterviewRecording;
import com.aiinterview.repository.InterviewRecordingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AudioService {

    @Value("${app.audio.upload-dir:uploads/audio}")
    private String uploadDir;

    @Value("${app.audio.max-file-size:50MB}")
    private String maxFileSize;

    @Autowired
    private InterviewRecordingRepository recordingRepository;

    /**
     * Save uploaded audio file and create recording record
     */
    public InterviewRecording saveAudioFile(MultipartFile file, String interviewId, Long userId) throws IOException {
        // Validate file
        validateAudioFile(file);

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFilename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create recording record
        InterviewRecording recording = new InterviewRecording(
            interviewId,
            userId,
            uniqueFilename,
            filePath.toString()
        );

        recording.setOriginalFilename(originalFilename);
        recording.setFileSize(file.getSize());
        recording.setFormat(fileExtension);
        recording.setStartTime(LocalDateTime.now());

        return recordingRepository.save(recording);
    }

    /**
     * Save audio blob data from frontend recording
     */
    public InterviewRecording saveAudioBlob(byte[] audioData, String interviewId, Long userId,
                                          String filename, Integer durationSeconds) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String fileExtension = getFileExtension(filename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFilename);

        // Save audio data
        Files.write(filePath, audioData);

        // Create recording record
        InterviewRecording recording = new InterviewRecording(
            interviewId,
            userId,
            uniqueFilename,
            filePath.toString()
        );

        recording.setOriginalFilename(filename);
        recording.setFileSize((long) audioData.length);
        recording.setFormat(fileExtension);
        recording.setDurationSeconds(durationSeconds);
        recording.setStartTime(LocalDateTime.now());
        recording.setEndTime(LocalDateTime.now());

        return recordingRepository.save(recording);
    }

    /**
     * Get recording by ID
     */
    public Optional<InterviewRecording> getRecordingById(Long recordingId) {
        return recordingRepository.findById(recordingId);
    }

    /**
     * Get recordings for an interview
     */
    public List<InterviewRecording> getRecordingsForInterview(String interviewId) {
        return recordingRepository.findByInterviewId(interviewId);
    }

    /**
     * Get recordings for a user
     */
    public List<InterviewRecording> getRecordingsForUser(Long userId) {
        return recordingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Delete recording and file
     */
    public void deleteRecording(Long recordingId, Long userId) throws IOException {
        Optional<InterviewRecording> recording = recordingRepository.findById(recordingId);
        if (recording.isPresent() && recording.get().getUserId().equals(userId)) {
            InterviewRecording rec = recording.get();

            // Delete file from filesystem
            Path filePath = Paths.get(rec.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }

            // Delete record from database
            recordingRepository.delete(rec);
        }
    }

    /**
     * Update recording metadata
     */
    public InterviewRecording updateRecordingMetadata(Long recordingId, Integer durationSeconds,
                                                    String status, String errorMessage) {
        Optional<InterviewRecording> recording = recordingRepository.findById(recordingId);
        if (recording.isPresent()) {
            InterviewRecording rec = recording.get();
            if (durationSeconds != null) {
                rec.setDurationSeconds(durationSeconds);
            }
            if (status != null) {
                rec.setStatus(status);
            }
            if (errorMessage != null) {
                rec.setErrorMessage(errorMessage);
            }
            return recordingRepository.save(rec);
        }
        return null;
    }

    /**
     * Increment usage count for a recording
     */
    @Transactional
    public void incrementUsageCount(Long recordingId) {
        Optional<InterviewRecording> recording = recordingRepository.findById(recordingId);
        if (recording.isPresent()) {
            InterviewRecording rec = recording.get();
            rec.setUsageCount(rec.getUsageCount() + 1);
            recordingRepository.save(rec);
        }
    }

    /**
     * Get audio file as byte array for download/streaming
     */
    public byte[] getAudioFile(Long recordingId) throws IOException {
        Optional<InterviewRecording> recording = recordingRepository.findById(recordingId);
        if (recording.isPresent()) {
            Path filePath = Paths.get(recording.get().getFilePath());
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
        }
        throw new IOException("Recording file not found");
    }

    /**
     * Validate audio file
     */
    private void validateAudioFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size (50MB limit)
        long maxSize = 50 * 1024 * 1024; // 50MB in bytes
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 50MB");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !isValidAudioType(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only audio files are allowed.");
        }
    }

    /**
     * Check if content type is valid audio type
     */
    private boolean isValidAudioType(String contentType) {
        return contentType.startsWith("audio/") ||
               contentType.equals("video/webm") || // WebM audio
               contentType.equals("application/octet-stream"); // Generic binary
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "webm";
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1).toLowerCase() : "webm";
    }

    /**
     * Get supported audio formats
     */
    public List<String> getSupportedFormats() {
        return List.of("webm", "mp3", "wav", "ogg", "m4a", "aac");
    }

    /**
     * Clean up old recordings (optional maintenance method)
     */
    public void cleanupOldRecordings(int daysOld) {
        // Implementation for cleaning up old recordings
        // This could be called by a scheduled job
    }
}
