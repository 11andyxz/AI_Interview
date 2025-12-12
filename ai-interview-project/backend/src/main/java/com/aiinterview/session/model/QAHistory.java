package com.aiinterview.session.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class QAHistory {
    private String questionId;
    private String questionText;
    private String answerText;
    private String evalComment;
    private String rubricLevel;
    
    // Enhanced fields
    private Double score; // 0-100 score
    private Map<String, Integer> detailedScores; // technicalAccuracy, depth, experience, communication
    private List<String> strengths;
    private List<String> improvements;
    private List<String> followUpQuestions;
    private LocalDateTime answeredAt;

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
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

    public String getEvalComment() {
        return evalComment;
    }

    public void setEvalComment(String evalComment) {
        this.evalComment = evalComment;
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

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}

