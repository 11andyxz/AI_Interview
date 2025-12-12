package com.aiinterview.dto;

import java.time.LocalDateTime;

public class QAHistory {
    private String questionText;
    private String answerText;
    private String rubricLevel;
    private Double score;
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
}
