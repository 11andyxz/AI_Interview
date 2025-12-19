package com.aiinterview.service;

import com.aiinterview.model.InterviewTemplate;
import com.aiinterview.repository.InterviewTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InterviewTemplateService {

    @Autowired
    private InterviewTemplateRepository templateRepository;

    /**
     * Get all templates for a user (both owned and public)
     */
    public List<InterviewTemplate> getUserTemplates(Long userId) {
        List<InterviewTemplate> userTemplates = templateRepository.findByUserId(userId);
        List<InterviewTemplate> publicTemplates = templateRepository.findByIsPublic(true);

        // Combine and remove duplicates
        List<InterviewTemplate> result = new ArrayList<>(userTemplates);
        result.addAll(publicTemplates);
        return result.stream()
            .distinct()
            .toList();
    }

    /**
     * Get only user's own templates
     */
    public List<InterviewTemplate> getUserOwnedTemplates(Long userId) {
        return templateRepository.findByUserId(userId);
    }

    /**
     * Get template by ID
     */
    public Optional<InterviewTemplate> getTemplateById(Long templateId) {
        return templateRepository.findById(templateId);
    }

    /**
     * Create a new template
     */
    @Transactional
    public InterviewTemplate createTemplate(InterviewTemplate template) {
        return templateRepository.save(template);
    }

    /**
     * Update an existing template
     */
    @Transactional
    public InterviewTemplate updateTemplate(Long templateId, Map<String, Object> updates) {
        Optional<InterviewTemplate> existingTemplate = templateRepository.findById(templateId);
        if (existingTemplate.isEmpty()) {
            throw new RuntimeException("Template not found");
        }

        InterviewTemplate template = existingTemplate.get();

        // Update fields
        if (updates.containsKey("name")) {
            template.setName((String) updates.get("name"));
        }
        if (updates.containsKey("description")) {
            template.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("techStack")) {
            template.setTechStack((String) updates.get("techStack"));
        }
        if (updates.containsKey("level")) {
            template.setLevel((String) updates.get("level"));
        }
        if (updates.containsKey("roleTitle")) {
            template.setRoleTitle((String) updates.get("roleTitle"));
        }
        if (updates.containsKey("durationMinutes")) {
            template.setDurationMinutes((Integer) updates.get("durationMinutes"));
        }
        if (updates.containsKey("language")) {
            template.setLanguage((String) updates.get("language"));
        }
        if (updates.containsKey("isPublic")) {
            template.setIsPublic((Boolean) updates.get("isPublic"));
        }
        if (updates.containsKey("configuration")) {
            template.setConfiguration((String) updates.get("configuration"));
        }
        if (updates.containsKey("tags")) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) updates.get("tags");
            template.setTags(tags);
        }

        return templateRepository.save(template);
    }

    /**
     * Delete a template
     */
    @Transactional
    public void deleteTemplate(Long templateId, Long userId) {
        Optional<InterviewTemplate> template = templateRepository.findById(templateId);
        if (template.isPresent() && template.get().getUserId().equals(userId)) {
            templateRepository.delete(template.get());
        } else {
            throw new RuntimeException("Template not found or access denied");
        }
    }

    /**
     * Increment usage count for a template
     */
    @Transactional
    public void incrementUsageCount(Long templateId) {
        Optional<InterviewTemplate> template = templateRepository.findById(templateId);
        if (template.isPresent()) {
            InterviewTemplate t = template.get();
            t.setUsageCount(t.getUsageCount() + 1);
            templateRepository.save(t);
        }
    }

    /**
     * Get popular templates (by usage count)
     */
    public List<InterviewTemplate> getPopularTemplates(int limit) {
        List<InterviewTemplate> publicTemplates = templateRepository.findByIsPublic(true);
        return publicTemplates.stream()
            .sorted((a, b) -> b.getUsageCount().compareTo(a.getUsageCount()))
            .limit(limit)
            .toList();
    }

    /**
     * Search templates by tech stack and level
     */
    public List<InterviewTemplate> searchTemplates(String techStack, String level) {
        if (techStack != null && level != null) {
            return templateRepository.findByTechStackAndLevel(techStack, level);
        } else if (techStack != null) {
            return templateRepository.findByTechStackAndLevel(techStack, "mid"); // Default level
        } else if (level != null) {
            return templateRepository.findByTechStackAndLevel("Java", level); // Default tech stack
        }
        return templateRepository.findByIsPublic(true);
    }

    /**
     * Add questions to an existing question set
     */
    @Transactional
    public InterviewTemplate addQuestionsToSet(Long questionSetId, List<String> newQuestions) {
        Optional<InterviewTemplate> questionSet = templateRepository.findById(questionSetId);
        if (questionSet.isEmpty()) {
            throw new RuntimeException("Question set not found");
        }

        InterviewTemplate set = questionSet.get();
        List<String> existingQuestions = set.getQuestions();
        List<String> questionsList;
        if (existingQuestions == null) {
            questionsList = new ArrayList<>();
        } else {
            questionsList = new ArrayList<>(existingQuestions);
        }
        questionsList.addAll(newQuestions);
        set.setQuestions(questionsList);

        return templateRepository.save(set);
    }

    /**
     * Remove questions from a question set
     */
    @Transactional
    public InterviewTemplate removeQuestionsFromSet(Long questionSetId, List<String> questionsToRemove) {
        Optional<InterviewTemplate> questionSet = templateRepository.findById(questionSetId);
        if (questionSet.isEmpty()) {
            throw new RuntimeException("Question set not found");
        }

        InterviewTemplate set = questionSet.get();
        List<String> existingQuestions = set.getQuestions();
        if (existingQuestions != null) {
            existingQuestions.removeAll(questionsToRemove);
            set.setQuestions(existingQuestions);
        }

        return templateRepository.save(set);
    }
}
