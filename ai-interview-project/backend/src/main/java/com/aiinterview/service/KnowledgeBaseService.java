package com.aiinterview.service;

import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.repository.KnowledgeBaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    /**
     * Get all knowledge bases for a user (user + system)
     */
    public List<KnowledgeBase> getKnowledgeBases(Long userId, String type) {
        if (type != null && "system".equals(type)) {
            return knowledgeBaseRepository.findByTypeAndIsActiveTrueOrderByCreatedAtDesc("system");
        } else if (type != null && "user".equals(type)) {
            return knowledgeBaseRepository.findByUserIdAndTypeAndIsActiveTrueOrderByCreatedAtDesc(userId, "user");
        }
        // Return both user and system
        List<KnowledgeBase> userKb = knowledgeBaseRepository.findByUserIdAndTypeAndIsActiveTrueOrderByCreatedAtDesc(userId, "user");
        List<KnowledgeBase> systemKb = knowledgeBaseRepository.findByTypeAndIsActiveTrueOrderByCreatedAtDesc("system");
        List<KnowledgeBase> result = new ArrayList<>(userKb);
        result.addAll(systemKb);
        return result;
    }
    
    /**
     * Get system knowledge bases only
     */
    public List<KnowledgeBase> getSystemKnowledgeBases() {
        return knowledgeBaseRepository.findByTypeOrderByCreatedAtDesc("system");
    }
    
    /**
     * Get knowledge base by ID
     */
    public Optional<KnowledgeBase> getKnowledgeBaseById(Long id, Long userId) {
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(id);
        if (kbOpt.isPresent()) {
            KnowledgeBase kb = kbOpt.get();
            // System knowledge bases are accessible to all users
            if ("system".equals(kb.getType()) || (kb.getUserId() != null && kb.getUserId().equals(userId))) {
                return kbOpt;
            }
        }
        return Optional.empty();
    }
    
    /**
     * Create user knowledge base
     */
    public KnowledgeBase createKnowledgeBase(Long userId, String name, String description, String content) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setType("user");
        kb.setName(name);
        kb.setDescription(description);
        kb.setContent(content);
        kb.setIsActive(true);
        return knowledgeBaseRepository.save(kb);
    }
    
    /**
     * Update knowledge base
     */
    public KnowledgeBase updateKnowledgeBase(Long id, Long userId, String name, String description, String content) {
        Optional<KnowledgeBase> kbOpt = getKnowledgeBaseById(id, userId);
        if (kbOpt.isEmpty()) {
            throw new RuntimeException("Knowledge base not found");
        }
        
        KnowledgeBase kb = kbOpt.get();
        // System knowledge bases cannot be updated by users
        if ("system".equals(kb.getType())) {
            throw new RuntimeException("System knowledge bases cannot be updated");
        }
        
        if (name != null) {
            kb.setName(name);
        }
        if (description != null) {
            kb.setDescription(description);
        }
        if (content != null) {
            kb.setContent(content);
        }
        return knowledgeBaseRepository.save(kb);
    }
    
    /**
     * Delete knowledge base (only user knowledge bases)
     */
    public boolean deleteKnowledgeBase(Long id, Long userId) {
        Optional<KnowledgeBase> kbOpt = getKnowledgeBaseById(id, userId);
        if (kbOpt.isEmpty()) {
            return false;
        }
        
        KnowledgeBase kb = kbOpt.get();
        // System knowledge bases cannot be deleted
        if ("system".equals(kb.getType())) {
            throw new RuntimeException("System knowledge bases cannot be deleted");
        }
        
        knowledgeBaseRepository.delete(kb);
        return true;
    }
}

