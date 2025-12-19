package com.aiinterview.repository;

import com.aiinterview.model.MockInterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockInterviewMessageRepository extends JpaRepository<MockInterviewMessage, Long> {
    List<MockInterviewMessage> findByMockInterviewIdOrderByCreatedAtAsc(String mockInterviewId);
}

