package com.aiinterview.repository;

import com.aiinterview.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, String> {
    List<Interview> findByCandidateId(Long candidateId);
    List<Interview> findByUserId(Long userId);
    List<Interview> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Interview> findByIdAndUserId(String id, Long userId);
}

