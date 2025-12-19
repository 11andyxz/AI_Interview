package com.aiinterview.repository;

import com.aiinterview.model.InterviewRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRecordingRepository extends JpaRepository<InterviewRecording, Long> {
    List<InterviewRecording> findByInterviewId(String interviewId);
    List<InterviewRecording> findByUserId(Long userId);
    List<InterviewRecording> findByInterviewIdAndStatus(String interviewId, String status);
    Optional<InterviewRecording> findByInterviewIdAndRecordingType(String interviewId, String recordingType);
    List<InterviewRecording> findByUserIdOrderByCreatedAtDesc(Long userId);
}
