package com.aiinterview.service;

import com.aiinterview.session.model.QAHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class PromptService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode systemPrompts;
    private JsonNode rolePrompts;

    @PostConstruct
    public void loadPrompts() {
        try (InputStream sysIn = new ClassPathResource("prompts/system-prompts.json").getInputStream();
             InputStream roleIn = new ClassPathResource("prompts/role-prompts.json").getInputStream()) {
            systemPrompts = objectMapper.readTree(sysIn);
            rolePrompts = objectMapper.readTree(roleIn);
        } catch (IOException e) {
            System.err.println("Failed to load prompts: " + e.getMessage());
        }
    }

    /**
     * Build complete system prompt for question generation
     */
    public String buildSystemPrompt(String roleId, String level, Map<String, Object> candidateInfo) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. Base system prompt
        prompt.append(getBasePrompt()).append("\n\n");
        
        // 2. Role-specific prompt
        prompt.append(buildRoleSpecificPrompt(roleId, level)).append("\n\n");
        
        // 3. Candidate context if available
        if (candidateInfo != null) {
            prompt.append(buildCandidateContextPrompt(candidateInfo));
        }
        
        return prompt.toString();
    }

    /**
     * Build evaluation system prompt
     */
    public String buildEvaluationSystemPrompt() {
        return systemPrompts.path("evaluation").asText();
    }

    /**
     * Get base system prompt
     */
    public String getBasePrompt() {
        return systemPrompts.path("base").asText();
    }

    /**
     * Build role-specific prompt based on roleId and level
     */
    public String buildRoleSpecificPrompt(String roleId, String level) {
        StringBuilder prompt = new StringBuilder();
        
        JsonNode role = rolePrompts.path("roles").path(roleId);
        if (role.isMissingNode()) {
            return "请根据候选人的背景提出相关的技术问题。";
        }
        
        String roleName = role.path("name").asText();
        String description = role.path("description").asText();
        
        prompt.append("当前面试岗位：").append(roleName).append("\n");
        prompt.append("岗位描述：").append(description).append("\n\n");
        
        // Focus areas
        prompt.append("重点考察领域：\n");
        JsonNode focusAreas = role.path("focus_areas");
        if (focusAreas.isArray()) {
            for (JsonNode area : focusAreas) {
                prompt.append("- ").append(area.asText()).append("\n");
            }
        }
        
        // Level expectations
        JsonNode levelNode = role.path("levels").path(level);
        if (!levelNode.isMissingNode()) {
            String levelName = levelNode.path("name").asText();
            String expectations = levelNode.path("expectations").asText();
            String questionStyle = levelNode.path("question_style").asText();
            
            prompt.append("\n岗位级别：").append(levelName).append("\n");
            prompt.append("级别期望：").append(expectations).append("\n");
            prompt.append("提问风格：").append(questionStyle).append("\n");
        }
        
        return prompt.toString();
    }

    /**
     * Build candidate context prompt from resume info
     */
    public String buildCandidateContextPrompt(Map<String, Object> candidateInfo) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("\n候选人背景信息：\n");
        
        // Work experience
        if (candidateInfo.containsKey("workExperience")) {
            prompt.append("\n工作经验：\n");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> experiences = 
                (List<Map<String, String>>) candidateInfo.get("workExperience");
            for (Map<String, String> exp : experiences) {
                prompt.append(String.format("- %s @ %s (%s)\n  %s\n",
                    exp.get("role"),
                    exp.get("company"),
                    exp.get("duration"),
                    exp.get("description")
                ));
            }
        }
        
        // Projects
        if (candidateInfo.containsKey("projects")) {
            prompt.append("\n项目经验：\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> projects = 
                (List<Map<String, Object>>) candidateInfo.get("projects");
            for (Map<String, Object> proj : projects) {
                @SuppressWarnings("unchecked")
                List<String> techStack = (List<String>) proj.get("techStack");
                prompt.append(String.format("- %s\n  技术栈: %s\n  描述: %s\n",
                    proj.get("title"),
                    techStack != null ? String.join(", ", techStack) : "",
                    proj.get("description")
                ));
            }
        }
        
        prompt.append("\n请根据候选人的实际工作经验和项目背景，提出有针对性的技术问题。");
        prompt.append("问题应该能够让候选人展示他们在简历中提到的技能和经验。\n");
        
        return prompt.toString();
    }

    /**
     * Build conversation history prompt
     */
    public String buildConversationHistoryPrompt(List<QAHistory> history, int maxMessages) {
        if (history == null || history.isEmpty()) {
            return "这是面试的开始。请先进行简单的开场白，然后提出第一个技术问题。";
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("之前的对话历史（最近").append(Math.min(history.size(), maxMessages))
              .append("轮）：\n\n");
        
        // Only include recent history to save tokens
        int startIndex = Math.max(0, history.size() - maxMessages);
        List<QAHistory> recentHistory = history.subList(startIndex, history.size());
        
        for (int i = 0; i < recentHistory.size(); i++) {
            QAHistory qa = recentHistory.get(i);
            prompt.append(String.format("第%d轮：\n", i + 1));
            prompt.append(String.format("问题：%s\n", qa.getQuestionText()));
            prompt.append(String.format("回答：%s\n", qa.getAnswerText()));
            
            if (qa.getRubricLevel() != null) {
                prompt.append(String.format("评估：%s", qa.getRubricLevel()));
                if (qa.getScore() != null) {
                    prompt.append(String.format(" (%.0f分)", qa.getScore()));
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("基于以上对话，请继续提出下一个问题。");
        prompt.append("可以是新的技术主题，也可以是对之前回答的深入追问。");
        prompt.append("确保问题有助于全面评估候选人的技术能力。\n");
        
        return prompt.toString();
    }

    /**
     * Build evaluation prompt
     */
    public String buildEvaluationPrompt(String question, String answer, String roleId, String level) {
        return String.format("""
            请评估以下面试回答的质量：
            
            问题：%s
            
            候选人回答：%s
            
            岗位：%s
            级别：%s
            
            请严格按照以下JSON格式返回评估结果（不要包含任何其他文字）：
            {
                "score": <0-100的总分>,
                "rubricLevel": "<excellent/good/average/poor>",
                "technicalAccuracy": <0-10>,
                "depth": <0-10>,
                "experience": <0-10>,
                "communication": <0-10>,
                "strengths": ["优点1", "优点2"],
                "improvements": ["改进建议1", "改进建议2"],
                "followUpQuestions": ["追问1", "追问2"]
            }
            """, question, answer, roleId, level);
    }

    /**
     * Get role display name
     */
    public String getRoleDisplayName(String roleId) {
        JsonNode role = rolePrompts.path("roles").path(roleId);
        if (!role.isMissingNode()) {
            return role.path("name").asText(roleId);
        }
        return roleId;
    }
}

