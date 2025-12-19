package com.aiinterview.repository;

import com.aiinterview.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);
    List<KnowledgeBase> findByTypeAndIsActiveTrueOrderByCreatedAtDesc(String type);
    List<KnowledgeBase> findByUserIdAndTypeAndIsActiveTrueOrderByCreatedAtDesc(Long userId, String type);
    List<KnowledgeBase> findByTypeOrderByCreatedAtDesc(String type); // For system knowledge bases
}

