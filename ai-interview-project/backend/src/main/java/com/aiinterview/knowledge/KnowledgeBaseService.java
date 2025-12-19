package com.aiinterview.knowledge;

import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.knowledge.model.RubricItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service("questionKnowledgeBaseService")
public class KnowledgeBaseService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<QuestionItem>> questionsByRole = new ConcurrentHashMap<>();
    private final Map<String, List<RubricItem>> rubricsByRole = new ConcurrentHashMap<>();
    private final Map<String, String> feedbackTemplatesByRole = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadKnowledgeBase() {
        loadQuestions("knowledge-base/backend_java_mid.json");
        loadQuestions("knowledge-base/frontend_react_mid.json");
        loadRubrics("knowledge-base/rubrics.json");
        loadFeedbackTemplates("knowledge-base/feedback_templates.json");
    }

    private void loadQuestions(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String roleId = root.path("roleId").asText();
            List<QuestionItem> items = objectMapper.convertValue(root.path("questions"), new TypeReference<>() {});
            questionsByRole.put(roleId, items);
        } catch (IOException e) {
            System.err.println("Failed to load questions from " + path + ": " + e.getMessage());
        }
    }

    private void loadRubrics(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<RubricItem> items = objectMapper.convertValue(root.path("rubrics"), new TypeReference<>() {});
            Map<String, List<RubricItem>> byRole = new HashMap<>();
            for (RubricItem item : items) {
                byRole.computeIfAbsent(item.getRoleId(), k -> new ArrayList<>()).add(item);
            }
            rubricsByRole.putAll(byRole);
        } catch (IOException e) {
            System.err.println("Failed to load rubrics from " + path + ": " + e.getMessage());
        }
    }

    private void loadFeedbackTemplates(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root.path("templates")) {
                String roleId = node.path("roleId").asText();
                String pattern = node.path("pattern").asText();
                feedbackTemplatesByRole.put(roleId, pattern);
            }
        } catch (IOException e) {
            System.err.println("Failed to load feedback templates from " + path + ": " + e.getMessage());
        }
    }

    public List<QuestionItem> getQuestions(String roleId) {
        return questionsByRole.getOrDefault(roleId, Collections.emptyList());
    }

    public Optional<RubricItem> getRubric(String roleId, String skill) {
        return rubricsByRole.getOrDefault(roleId, Collections.emptyList())
                .stream()
                .filter(r -> skill.equals(r.getSkill()))
                .findFirst();
    }

    public Optional<String> getFeedbackTemplate(String roleId) {
        return Optional.ofNullable(feedbackTemplatesByRole.get(roleId));
    }
}

