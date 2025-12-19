package com.aiinterview.repository;

import com.aiinterview.model.InterviewTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, Long> {
    List<InterviewTemplate> findByUserId(Long userId);
    List<InterviewTemplate> findByUserIdAndIsPublic(Long userId, Boolean isPublic);
    List<InterviewTemplate> findByIsPublic(Boolean isPublic);
    List<InterviewTemplate> findByTechStackAndLevel(String techStack, String level);
    List<InterviewTemplate> findByUserIdOrderByUsageCountDesc(Long userId);
}
