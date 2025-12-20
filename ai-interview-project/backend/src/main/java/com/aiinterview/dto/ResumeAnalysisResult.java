package com.aiinterview.dto;

import java.util.List;

/**
 * DTO for storing structured resume analysis results from AI
 */
public class ResumeAnalysisResult {

    private String level; // junior/mid/senior
    private List<String> techStack; // e.g., ["Java", "Spring", "MySQL"]
    private Integer experienceYears; // years of experience
    private List<String> skills; // specific skills mentioned
    private List<String> mainSkillAreas; // main areas of expertise
    private String education; // education level
    private String summary; // brief summary of the candidate

    // Default constructor
    public ResumeAnalysisResult() {
    }

    // Constructor with all fields
    public ResumeAnalysisResult(String level, List<String> techStack, Integer experienceYears,
                               List<String> skills, List<String> mainSkillAreas, String education, String summary) {
        this.level = level;
        this.techStack = techStack;
        this.experienceYears = experienceYears;
        this.skills = skills;
        this.mainSkillAreas = mainSkillAreas;
        this.education = education;
        this.summary = summary;
    }

    // Getters and Setters
    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<String> getTechStack() {
        return techStack;
    }

    public void setTechStack(List<String> techStack) {
        this.techStack = techStack;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getMainSkillAreas() {
        return mainSkillAreas;
    }

    public void setMainSkillAreas(List<String> mainSkillAreas) {
        this.mainSkillAreas = mainSkillAreas;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "ResumeAnalysisResult{" +
                "level='" + level + '\'' +
                ", techStack=" + techStack +
                ", experienceYears=" + experienceYears +
                ", skills=" + skills +
                ", mainSkillAreas=" + mainSkillAreas +
                ", education='" + education + '\'' +
                ", summary='" + summary + '\'' +
                '}';
    }
}
