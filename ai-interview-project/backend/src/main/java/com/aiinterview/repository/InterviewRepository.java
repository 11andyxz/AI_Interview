package com.aiinterview.repository;

import com.aiinterview.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, String> {
    List<Interview> findByCandidateId(Long candidateId);
    List<Interview> findByUserId(Long userId);
}

