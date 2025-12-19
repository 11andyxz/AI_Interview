package com.aiinterview.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class QAHistory {
    private String questionText;
    private String answerText;
    private String rubricLevel;
    private Double score;
    private Map<String, Integer> detailedScores;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> followUpQuestions;
    private LocalDateTime createdAt;

    public QAHistory() {
        this.createdAt = LocalDateTime.now();
    }

    public QAHistory(String questionText, String answerText) {
        this.questionText = questionText;
        this.answerText = answerText;
        this.createdAt = LocalDateTime.now();
    }

    public QAHistory(String questionText, String answerText, String rubricLevel, Double score) {
        this.questionText = questionText;
        this.answerText = answerText;
        this.rubricLevel = rubricLevel;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getRubricLevel() {
        return rubricLevel;
    }

    public void setRubricLevel(String rubricLevel) {
        this.rubricLevel = rubricLevel;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Integer> getDetailedScores() {
        return detailedScores;
    }

    public void setDetailedScores(Map<String, Integer> detailedScores) {
        this.detailedScores = detailedScores;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getImprovements() {
        return improvements;
    }

    public void setImprovements(List<String> improvements) {
        this.improvements = improvements;
    }

    public List<String> getFollowUpQuestions() {
        return followUpQuestions;
    }

    public void setFollowUpQuestions(List<String> followUpQuestions) {
        this.followUpQuestions = followUpQuestions;
    }
}
