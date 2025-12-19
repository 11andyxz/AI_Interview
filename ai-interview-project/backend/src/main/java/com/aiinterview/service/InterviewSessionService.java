package com.aiinterview.service;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.knowledge.KnowledgeBaseService;
import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.knowledge.model.RubricItem;
import com.aiinterview.model.Candidate;
import com.aiinterview.model.Interview;
import com.aiinterview.model.InterviewMessage;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.repository.CandidateRepository;
import com.aiinterview.repository.InterviewMessageRepository;
import com.aiinterview.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class InterviewSessionService {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private InterviewMessageRepository interviewMessageRepository;

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // In-memory cache for active sessions (backed by Redis and DB)
    private final Map<String, List<QAHistory>> sessionHistories = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> askedQuestions = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * 获取面试会话信息
     */
    public Optional<Map<String, Object>> getInterviewSession(String interviewId) {
        return interviewRepository.findById(interviewId)
            .map(interview -> {
                Map<String, Object> session = new HashMap<>();
                session.put("interview", interview);

                // 获取候选人信息
                Optional<Candidate> candidateOpt = candidateRepository.findById(interview.getCandidateId());
                if (candidateOpt.isPresent()) {
                    session.put("candidate", candidateOpt.get());
                }

                // 获取对话历史（从数据库和缓存）
                List<QAHistory> history = loadChatHistory(interviewId);
                session.put("conversationHistory", history);

                return session;
            });
    }
    
    /**
     * 加载对话历史（优先从Redis，然后数据库）
     */
    private List<QAHistory> loadChatHistory(String interviewId) {
        // Try Redis first
        if (redisTemplate != null) {
            try {
                @SuppressWarnings("unchecked")
                List<QAHistory> cached = (List<QAHistory>) redisTemplate.opsForValue().get("session:history:" + interviewId);
                if (cached != null) {
                    return cached;
                }
            } catch (Exception e) {
                // Redis unavailable, fallback to database
                // Log error but continue
            }
        }
        
        // Try in-memory cache
        if (sessionHistories.containsKey(interviewId)) {
            return sessionHistories.get(interviewId);
        }
        
        // Load from database
        List<InterviewMessage> messages = interviewMessageRepository.findByInterviewIdOrderByCreatedAtAsc(interviewId);
        List<QAHistory> history = messages.stream()
            .map(msg -> new QAHistory(msg.getUserMessage(), msg.getAiMessage()))
            .collect(Collectors.toList());
        
        // Cache in memory and Redis
        sessionHistories.put(interviewId, history);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("session:history:" + interviewId, history, Duration.ofHours(24));
            } catch (Exception e) {
                // Redis unavailable, continue without caching
            }
        }
        
        return history;
    }

    /**
     * 生成智能回答
     */
    public Mono<String> generatePersonalizedResponse(String interviewId, ChatRequest request) {
        Optional<Interview> interviewOpt = interviewRepository.findById(interviewId);
        if (interviewOpt.isEmpty()) {
            return Mono.just("面试会话不存在");
        }

        Interview interview = interviewOpt.get();
        Optional<Candidate> candidateOpt = candidateRepository.findById(interview.getCandidateId());
        if (candidateOpt.isEmpty()) {
            return Mono.just("候选人信息不存在");
        }

        Candidate candidate = candidateOpt.get();

        // 构建系统提示
        String systemPrompt = buildInterviewSystemPrompt(interview, candidate);

        // 构建消息列表
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", systemPrompt));

        // 添加对话历史（从持久化存储加载）
        List<QAHistory> history = loadChatHistory(interviewId);
        if (request.getRecentHistory() != null && !request.getRecentHistory().isEmpty()) {
            history = new ArrayList<>(request.getRecentHistory());
        }

        for (QAHistory qa : history) {
            messages.add(new OpenAiMessage("user", qa.getQuestionText()));
            messages.add(new OpenAiMessage("assistant", qa.getAnswerText()));
        }

        // 添加当前用户消息
        messages.add(new OpenAiMessage("user", request.getUserMessage()));

        // 调用OpenAI生成回答
        return openAiService.chat(messages)
            .doOnNext(aiResponse -> {
                // 保存到对话历史
                QAHistory qa = new QAHistory(request.getUserMessage(), aiResponse);
                saveChatMessage(interviewId, qa);
            });
    }

    /**
     * 保存聊天消息到历史（数据库 + Redis + 内存）
     */
    public void saveChatMessage(String interviewId, QAHistory qa) {
        // Save to database
        InterviewMessage message = new InterviewMessage();
        message.setInterviewId(interviewId);
        message.setUserMessage(qa.getQuestionText());
        message.setAiMessage(qa.getAnswerText());
        message.setMessageType("chat");
        interviewMessageRepository.save(message);
        
        // Update in-memory cache
        sessionHistories.computeIfAbsent(interviewId, k -> new ArrayList<>()).add(qa);
        
        // Update Redis cache
        if (redisTemplate != null) {
            try {
                List<QAHistory> history = sessionHistories.get(interviewId);
                redisTemplate.opsForValue().set("session:history:" + interviewId, history, Duration.ofHours(24));
            } catch (Exception e) {
                // Redis unavailable, continue without caching
            }
        }
    }
    
    /**
     * 从知识库选择下一个问题（合并自SessionService）
     */
    public Optional<QuestionItem> pickNextQuestion(String interviewId, String roleId) {
        Set<String> asked = askedQuestions.computeIfAbsent(interviewId, k -> new HashSet<>());
        List<QuestionItem> questions = knowledgeBaseService.getQuestions(roleId);
        List<QuestionItem> remaining = questions.stream()
                .filter(q -> !asked.contains(q.getId()))
                .collect(Collectors.toList());
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        QuestionItem selected = remaining.get(random.nextInt(remaining.size()));
        asked.add(selected.getId());
        return Optional.of(selected);
    }
    
    /**
     * 记录答案并评估（合并自SessionService）
     */
    public QAHistory recordAnswer(String interviewId, QuestionItem question, String answerText, String roleId) {
        QAHistory qa = new QAHistory();
        qa.setQuestionText(question.getText());
        qa.setAnswerText(answerText);
        
        // Simple evaluation (can be enhanced with LLM)
        RubricItem rubric = knowledgeBaseService.getRubric(roleId, firstSkill(question)).orElse(null);
        if (answerText != null && answerText.length() > 40) {
            qa.setRubricLevel("excellent");
        } else if (answerText != null && answerText.length() > 15) {
            qa.setRubricLevel("average");
        } else {
            qa.setRubricLevel("poor");
        }
        
        if (rubric != null && rubric.getLevels() != null) {
            // Use rubric feedback if available
            // Note: QAHistory DTO doesn't have evalComment field, but rubric level is set
            rubric.getLevels().getOrDefault(qa.getRubricLevel(), "Needs improvement.");
        }
        
        // Save to history
        saveChatMessage(interviewId, qa);
        return qa;
    }
    
    /**
     * 构建反馈（合并自SessionService）
     */
    public String buildFeedback(String interviewId, String roleId) {
        List<QAHistory> history = loadChatHistory(interviewId);
        String template = knowledgeBaseService.getFeedbackTemplate(roleId)
                .orElse("Overall feedback: {assessment}");
        StringBuilder assessment = new StringBuilder();
        for (QAHistory qa : history) {
            assessment.append("- Q: ").append(qa.getQuestionText())
                    .append(" | Answer: ").append(qa.getAnswerText() != null ? 
                        (qa.getAnswerText().length() > 50 ? qa.getAnswerText().substring(0, 50) + "..." : qa.getAnswerText()) 
                        : "No answer")
                    .append("\n");
        }
        return template.replace("{assessment}", assessment.toString())
                .replace("{skill}", "overall")
                .replace("{next_steps}", "Focus on weaker areas identified above.");
    }
    
    private String firstSkill(QuestionItem question) {
        return (question.getSkills() != null && !question.getSkills().isEmpty())
                ? question.getSkills().get(0)
                : "general";
    }

    /**
     * 获取对话历史（从持久化存储）
     */
    public List<QAHistory> getChatHistory(String interviewId) {
        return loadChatHistory(interviewId);
    }

    /**
     * 构建OpenAI消息列表（用于WebSocket流式响应）
     */
    public List<OpenAiMessage> buildMessagesForOpenAI(String interviewId, ChatRequest request) {
        Optional<Interview> interviewOpt = interviewRepository.findById(interviewId);
        if (interviewOpt.isEmpty()) {
            return List.of();
        }

        Interview interview = interviewOpt.get();
        Optional<Candidate> candidateOpt = candidateRepository.findById(interview.getCandidateId());
        if (candidateOpt.isEmpty()) {
            return List.of();
        }

        Candidate candidate = candidateOpt.get();
        String systemPrompt = buildInterviewSystemPrompt(interview, candidate);

        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", systemPrompt));

        // 添加对话历史
        List<QAHistory> history = loadChatHistory(interviewId);
        if (request.getRecentHistory() != null && !request.getRecentHistory().isEmpty()) {
            history = new ArrayList<>(request.getRecentHistory());
        }

        for (QAHistory qa : history) {
            messages.add(new OpenAiMessage("user", qa.getQuestionText()));
            messages.add(new OpenAiMessage("assistant", qa.getAnswerText()));
        }

        // 添加当前用户消息
        messages.add(new OpenAiMessage("user", request.getUserMessage()));

        return messages;
    }
    
    /**
     * 构建面试系统提示
     */
    private String buildInterviewSystemPrompt(Interview interview, Candidate candidate) {
        StringBuilder prompt = new StringBuilder();

        // 基础面试官角色
        prompt.append("你是一个专业的AI面试官，正在进行技术面试。\n\n");

        // 岗位信息
        prompt.append("当前面试岗位：").append(interview.getTitle()).append("\n");
        if (interview.getTechStack() != null) {
            prompt.append("技术栈：").append(interview.getTechStack()).append("\n");
        }
        prompt.append("\n");

        // 候选人背景信息
        prompt.append("候选人背景：\n");
        prompt.append("- 姓名：").append(candidate.getName()).append("\n");
        if (candidate.getExperienceYears() != null) {
            prompt.append("- 工作年限：").append(candidate.getExperienceYears()).append("年\n");
        }
        if (candidate.getEducation() != null) {
            prompt.append("- 学历：").append(candidate.getEducation()).append("\n");
        }
        if (candidate.getSkills() != null) {
            prompt.append("- 技能：").append(candidate.getSkills()).append("\n");
        }
        if (candidate.getResumeText() != null) {
            prompt.append("- 简历摘要：").append(candidate.getResumeText()).append("\n");
        }
        prompt.append("\n");

        // 面试指导
        prompt.append("面试要求：\n");
        prompt.append("1. 基于候选人的实际经验提出有针对性的问题\n");
        prompt.append("2. 问题难度要适中，既不过于简单也不过于复杂\n");
        prompt.append("3. 关注候选人在简历中提到的技能和项目经验\n");
        prompt.append("4. 回答要专业、友好、具有建设性\n");
        prompt.append("5. 如果候选人回答不完整，可以适当追问\n");
        prompt.append("6. 保持对话的流畅性和连贯性\n");

        if ("Chinese".equals(interview.getLanguage())) {
            prompt.append("7. 请用中文进行面试\n");
        } else {
            prompt.append("7. 请用英文进行面试，除非候选人主动用中文提问\n");
        }

        return prompt.toString();
    }
}
