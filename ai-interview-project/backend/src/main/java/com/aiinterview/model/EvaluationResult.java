package com.aiinterview.model;

import java.util.List;
import java.util.Map;

public class EvaluationResult {
    private Double score; // 0-100
    private String rubricLevel; // excellent/good/average/poor
    private Integer technicalAccuracy; // 0-10
    private Integer depth; // 0-10
    private Integer experience; // 0-10
    private Integer communication; // 0-10
    private List<String> strengths;
    private List<String> improvements;
    private List<String> followUpQuestions;

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getRubricLevel() {
        return rubricLevel;
    }

    public void setRubricLevel(String rubricLevel) {
        this.rubricLevel = rubricLevel;
    }

    public Integer getTechnicalAccuracy() {
        return technicalAccuracy;
    }

    public void setTechnicalAccuracy(Integer technicalAccuracy) {
        this.technicalAccuracy = technicalAccuracy;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getExperience() {
        return experience;
    }

    public void setExperience(Integer experience) {
        this.experience = experience;
    }

    public Integer getCommunication() {
        return communication;
    }

    public void setCommunication(Integer communication) {
        this.communication = communication;
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

    public Map<String, Integer> getDetailedScores() {
        return Map.of(
            "technicalAccuracy", technicalAccuracy != null ? technicalAccuracy : 0,
            "depth", depth != null ? depth : 0,
            "experience", experience != null ? experience : 0,
            "communication", communication != null ? communication : 0
        );
    }
}

