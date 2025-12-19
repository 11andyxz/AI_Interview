package com.aiinterview.repository;

import com.aiinterview.model.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockInterviewRepository extends JpaRepository<MockInterview, String> {
    List<MockInterview> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<MockInterview> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    Optional<MockInterview> findByIdAndUserId(String id, Long userId);
}

