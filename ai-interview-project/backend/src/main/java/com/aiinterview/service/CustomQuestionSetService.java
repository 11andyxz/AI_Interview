package com.aiinterview.service;

import com.aiinterview.model.CustomQuestionSet;
import com.aiinterview.repository.CustomQuestionSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CustomQuestionSetService {

    @Autowired
    private CustomQuestionSetRepository questionSetRepository;

    /**
     * Get all question sets available to user (both owned and public)
     */
    public List<CustomQuestionSet> getUserQuestionSets(Long userId) {
        List<CustomQuestionSet> userSets = questionSetRepository.findByUserId(userId);
        List<CustomQuestionSet> publicSets = questionSetRepository.findByIsPublic(true);

        // Combine and remove duplicates
        List<CustomQuestionSet> result = new ArrayList<>(userSets);
        result.addAll(publicSets);
        return result.stream()
            .distinct()
            .toList();
    }

    /**
     * Get only user's own question sets
     */
    public List<CustomQuestionSet> getUserOwnedQuestionSets(Long userId) {
        return questionSetRepository.findByUserId(userId);
    }

    /**
     * Get question set by ID
     */
    public Optional<CustomQuestionSet> getQuestionSetById(Long questionSetId) {
        return questionSetRepository.findById(questionSetId);
    }

    /**
     * Create a new question set
     */
    @Transactional
    public CustomQuestionSet createQuestionSet(CustomQuestionSet questionSet) {
        return questionSetRepository.save(questionSet);
    }

    /**
     * Update an existing question set
     */
    @Transactional
    public CustomQuestionSet updateQuestionSet(Long questionSetId, Map<String, Object> updates) {
        Optional<CustomQuestionSet> existingSet = questionSetRepository.findById(questionSetId);
        if (existingSet.isEmpty()) {
            throw new RuntimeException("Question set not found");
        }

        CustomQuestionSet questionSet = existingSet.get();

        // Update fields
        if (updates.containsKey("name")) {
            questionSet.setName((String) updates.get("name"));
        }
        if (updates.containsKey("description")) {
            questionSet.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("techStack")) {
            questionSet.setTechStack((String) updates.get("techStack"));
        }
        if (updates.containsKey("level")) {
            questionSet.setLevel((String) updates.get("level"));
        }
        if (updates.containsKey("isPublic")) {
            questionSet.setIsPublic((Boolean) updates.get("isPublic"));
        }
        if (updates.containsKey("questions")) {
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) updates.get("questions");
            questionSet.setQuestions(questions);
        }
        if (updates.containsKey("tags")) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) updates.get("tags");
            questionSet.setTags(tags);
        }

        return questionSetRepository.save(questionSet);
    }

    /**
     * Delete a question set
     */
    @Transactional
    public void deleteQuestionSet(Long questionSetId, Long userId) {
        Optional<CustomQuestionSet> questionSet = questionSetRepository.findById(questionSetId);
        if (questionSet.isPresent() && questionSet.get().getUserId().equals(userId)) {
            questionSetRepository.delete(questionSet.get());
        } else {
            throw new RuntimeException("Question set not found or access denied");
        }
    }

    /**
     * Increment usage count for a question set
     */
    @Transactional
    public void incrementUsageCount(Long questionSetId) {
        Optional<CustomQuestionSet> questionSet = questionSetRepository.findById(questionSetId);
        if (questionSet.isPresent()) {
            CustomQuestionSet set = questionSet.get();
            set.setUsageCount(set.getUsageCount() + 1);
            questionSetRepository.save(set);
        }
    }

    /**
     * Get popular question sets (by usage count)
     */
    public List<CustomQuestionSet> getPopularQuestionSets(int limit) {
        List<CustomQuestionSet> publicSets = questionSetRepository.findByIsPublic(true);
        return publicSets.stream()
            .sorted((a, b) -> b.getUsageCount().compareTo(a.getUsageCount()))
            .limit(limit)
            .toList();
    }

    /**
     * Search question sets by tech stack and level
     */
    public List<CustomQuestionSet> searchQuestionSets(String techStack, String level) {
        if (techStack != null && level != null) {
            return questionSetRepository.findByTechStackAndLevel(techStack, level);
        } else if (techStack != null) {
            return questionSetRepository.findByTechStackAndLevel(techStack, "mid"); // Default level
        } else if (level != null) {
            return questionSetRepository.findByTechStackAndLevel("Java", level); // Default tech stack
        }
        return questionSetRepository.findByIsPublic(true);
    }

    /**
     * Add questions to an existing question set
     */
    @Transactional
    public CustomQuestionSet addQuestionsToSet(Long questionSetId, List<String> newQuestions) {
        Optional<CustomQuestionSet> questionSet = questionSetRepository.findById(questionSetId);
        if (questionSet.isEmpty()) {
            throw new RuntimeException("Question set not found");
        }

        CustomQuestionSet set = questionSet.get();
        List<String> existingQuestions = set.getQuestions();
        List<String> questionsList;
        if (existingQuestions == null) {
            questionsList = new ArrayList<>();
        } else {
            questionsList = new ArrayList<>(existingQuestions);
        }
        questionsList.addAll(newQuestions);
        set.setQuestions(questionsList);

        return questionSetRepository.save(set);
    }

    /**
     * Remove questions from a question set
     */
    @Transactional
    public CustomQuestionSet removeQuestionsFromSet(Long questionSetId, List<String> questionsToRemove) {
        Optional<CustomQuestionSet> questionSet = questionSetRepository.findById(questionSetId);
        if (questionSet.isEmpty()) {
            throw new RuntimeException("Question set not found");
        }

        CustomQuestionSet set = questionSet.get();
        List<String> existingQuestions = set.getQuestions();
        if (existingQuestions != null) {
            // Create a mutable copy to avoid UnsupportedOperationException
            List<String> questionsList = new ArrayList<>(existingQuestions);
            questionsList.removeAll(questionsToRemove);
            set.setQuestions(questionsList);
        }

        return questionSetRepository.save(set);
    }
}
