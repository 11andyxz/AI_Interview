package com.aiinterview.controller;

import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "http://localhost:3000")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<InterviewSession> create(@RequestBody Map<String, Object> body) {
        String roleId = (String) body.getOrDefault("roleId", "backend_java");
        String level = (String) body.getOrDefault("level", "mid");
        Object skillsObj = body.get("skills");
        List<String> skills = (skillsObj instanceof List<?> list)
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        InterviewSession session = sessionService.createSession(roleId, level, skills);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewSession> get(@PathVariable String id) {
        Optional<InterviewSession> session = sessionService.getSession(id);
        return session.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/next-question")
    public ResponseEntity<?> nextQuestion(@PathVariable String id) {
        Optional<QuestionItem> q = sessionService.pickNextQuestion(id);
        return q.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "No more questions")));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<?> answer(@PathVariable String id, @RequestBody Map<String, String> body) {
        String questionId = body.get("questionId");
        String questionText = body.get("questionText");
        String answer = body.get("answerText");
        // We need a QuestionItem to record; build minimal placeholder
        QuestionItem item = new QuestionItem();
        item.setId(questionId != null ? questionId : "unknown");
        item.setText(questionText != null ? questionText : "");
        QAHistory qa = sessionService.recordAnswer(id, item, answer);
        return ResponseEntity.ok(qa);
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<?> feedback(@PathVariable String id) {
        String fb = sessionService.buildFeedback(id);
        return ResponseEntity.ok(Map.of("feedback", fb));
    }
}

