package com.aiinterview.dto;

import java.util.List;

public class CreateInterviewRequest {
    private Integer candidateId;
    private String positionType;
    private List<String> programmingLanguages;
    private String language;
    private boolean useCustomKnowledge;

    public Integer getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Integer candidateId) {
        this.candidateId = candidateId;
    }

    public String getPositionType() {
        return positionType;
    }

    public void setPositionType(String positionType) {
        this.positionType = positionType;
    }

    public List<String> getProgrammingLanguages() {
        return programmingLanguages;
    }

    public void setProgrammingLanguages(List<String> programmingLanguages) {
        this.programmingLanguages = programmingLanguages;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isUseCustomKnowledge() {
        return useCustomKnowledge;
    }

    public void setUseCustomKnowledge(boolean useCustomKnowledge) {
        this.useCustomKnowledge = useCustomKnowledge;
    }
}

