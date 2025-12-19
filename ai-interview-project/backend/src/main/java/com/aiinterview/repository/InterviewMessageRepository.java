package com.aiinterview.repository;

import com.aiinterview.model.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {
    List<InterviewMessage> findByInterviewIdOrderByCreatedAtAsc(String interviewId);
    void deleteByInterviewId(String interviewId);
}

