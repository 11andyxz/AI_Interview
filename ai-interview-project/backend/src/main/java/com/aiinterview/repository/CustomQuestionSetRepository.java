package com.aiinterview.repository;

import com.aiinterview.model.CustomQuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomQuestionSetRepository extends JpaRepository<CustomQuestionSet, Long> {
    List<CustomQuestionSet> findByUserId(Long userId);
    List<CustomQuestionSet> findByUserIdAndIsPublic(Long userId, Boolean isPublic);
    List<CustomQuestionSet> findByIsPublic(Boolean isPublic);
    List<CustomQuestionSet> findByTechStackAndLevel(String techStack, String level);
    List<CustomQuestionSet> findByUserIdOrderByUsageCountDesc(Long userId);
}
