package com.aiinterview.service;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Candidate;
import com.aiinterview.model.Interview;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.repository.CandidateRepository;
import com.aiinterview.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InterviewSessionService {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private OpenAiService openAiService;


    // 内存存储对话历史 (生产环境应该用Redis或数据库)
    private final Map<String, List<QAHistory>> sessionHistories = new ConcurrentHashMap<>();

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

                // 获取对话历史
                List<QAHistory> history = sessionHistories.getOrDefault(interviewId, new ArrayList<>());
                session.put("conversationHistory", history);

                return session;
            });
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

        // 添加对话历史
        List<QAHistory> history = sessionHistories.getOrDefault(interviewId, new ArrayList<>());
        if (request.getRecentHistory() != null) {
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
     * 保存聊天消息到历史
     */
    public void saveChatMessage(String interviewId, QAHistory qa) {
        sessionHistories.computeIfAbsent(interviewId, k -> new ArrayList<>()).add(qa);
    }

    /**
     * 获取对话历史
     */
    public List<QAHistory> getChatHistory(String interviewId) {
        return sessionHistories.getOrDefault(interviewId, new ArrayList<>());
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
