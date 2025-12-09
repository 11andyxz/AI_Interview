package com.aiinterview.session;

import com.aiinterview.knowledge.KnowledgeBaseService;
import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.knowledge.model.RubricItem;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final Map<String, InterviewSession> sessions = new ConcurrentHashMap<>();
    private final KnowledgeBaseService knowledgeBaseService;
    private final Random random = new Random();

    public SessionService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public InterviewSession createSession(String roleId, String level, List<String> skills) {
        InterviewSession session = new InterviewSession();
        session.setId(UUID.randomUUID().toString());
        session.setRoleId(roleId);
        session.setLevel(level);
        session.setSkills(skills != null ? skills : new ArrayList<>());
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now());
        sessions.put(session.getId(), session);
        return session;
    }

    public Optional<InterviewSession> getSession(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Optional<QuestionItem> pickNextQuestion(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        List<QuestionItem> questions = knowledgeBaseService.getQuestions(session.getRoleId());
        Set<String> asked = session.getHistory().stream()
                .map(QAHistory::getQuestionId)
                .collect(Collectors.toSet());
        List<QuestionItem> remaining = questions.stream()
                .filter(q -> !asked.contains(q.getId()))
                .toList();
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(remaining.get(random.nextInt(remaining.size())));
    }

    public QAHistory recordAnswer(String sessionId, QuestionItem question, String answerText) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }
        QAHistory qa = new QAHistory();
        qa.setQuestionId(question.getId());
        qa.setQuestionText(question.getText());
        qa.setAnswerText(answerText);

        // Simple mock evaluation; TODO(ML): replace with LLM-based scoring using rubric
        RubricItem rubric = knowledgeBaseService.getRubric(session.getRoleId(), firstSkill(question))
                .orElse(null);
        if (answerText != null && answerText.length() > 40) {
            qa.setRubricLevel("excellent");
            qa.setEvalComment("Good depth and completeness.");
        } else if (answerText != null && answerText.length() > 15) {
            qa.setRubricLevel("average");
            qa.setEvalComment("Covers basics; could add more detail.");
        } else {
            qa.setRubricLevel("poor");
            qa.setEvalComment("Needs more depth.");
        }
        if (rubric != null && rubric.getLevels() != null) {
            qa.setEvalComment(rubric.getLevels().getOrDefault(qa.getRubricLevel(), qa.getEvalComment()));
        }

        session.getHistory().add(qa);
        return qa;
    }

    public String buildFeedback(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }
        String template = knowledgeBaseService.getFeedbackTemplate(session.getRoleId())
                .orElse("Overall feedback: {assessment}");
        StringBuilder assessment = new StringBuilder();
        for (QAHistory qa : session.getHistory()) {
            assessment.append("- Q: ").append(qa.getQuestionText())
                    .append(" | Eval: ").append(qa.getRubricLevel())
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
}

