package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillTrackingService {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewSessionService interviewSessionService;

    /**
     * Get comprehensive skill progress for a user
     */
    public Map<String, Object> getSkillProgress(Long userId) {
        Map<String, Object> result = new HashMap<>();

        // Get all completed interviews for the user
        List<Interview> interviews = interviewRepository.findByCandidateId(userId)
            .stream()
            .filter(i -> "Completed".equals(i.getStatus()))
            .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
            .collect(Collectors.toList());

        if (interviews.isEmpty()) {
            result.put("totalInterviews", 0);
            result.put("skillAverages", new HashMap<>());
            result.put("skillHistory", new ArrayList<>());
            return result;
        }

        result.put("totalInterviews", interviews.size());

        // Calculate skill averages across all interviews
        Map<String, Object> skillAverages = calculateSkillAverages(interviews);
        result.putAll(skillAverages);

        // Generate skill history over time
        List<Map<String, Object>> skillHistory = generateSkillHistory(interviews);
        result.put("skillHistory", skillHistory);

        // Calculate skill trends (improvement rates)
        Map<String, Double> skillTrends = calculateSkillTrends(skillHistory);
        result.put("skillTrends", skillTrends);

        return result;
    }

    /**
     * Calculate average scores for each skill across all interviews
     */
    private Map<String, Object> calculateSkillAverages(List<Interview> interviews) {
        Map<String, Double> skillSums = new HashMap<>();
        Map<String, Integer> skillCounts = new HashMap<>();
        String[] skills = {"technicalAccuracy", "depth", "experience", "communication"};

        for (Interview interview : interviews) {
            List<QAHistory> history = interviewSessionService.getChatHistory(interview.getId());

            for (QAHistory qa : history) {
                if (qa.getDetailedScores() != null) {
                    for (String skill : skills) {
                        Integer score = qa.getDetailedScores().get(skill);
                        if (score != null) {
                            skillSums.put(skill, skillSums.getOrDefault(skill, 0.0) + score);
                            skillCounts.put(skill, skillCounts.getOrDefault(skill, 0) + 1);
                        }
                    }
                }
            }
        }

        Map<String, Double> averages = new HashMap<>();
        for (String skill : skills) {
            double sum = skillSums.getOrDefault(skill, 0.0);
            int count = skillCounts.getOrDefault(skill, 0);
            averages.put(skill, count > 0 ? Math.round((sum / count) * 100.0) / 100.0 : 0.0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("skillAverages", averages);
        result.put("totalQuestions", skillCounts.values().stream().mapToInt(Integer::intValue).sum());

        return result;
    }

    /**
     * Generate skill progress history over time
     */
    private List<Map<String, Object>> generateSkillHistory(List<Interview> interviews) {
        List<Map<String, Object>> history = new ArrayList<>();

        // Group interviews by month
        Map<String, List<Interview>> interviewsByMonth = interviews.stream()
            .collect(Collectors.groupingBy(interview -> {
                LocalDate date = interview.getDate() != null ?
                    interview.getDate() : interview.getCreatedAt().toLocalDate();
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }));

        // Sort months chronologically
        List<String> sortedMonths = interviewsByMonth.keySet().stream()
            .sorted()
            .collect(Collectors.toList());

        for (String month : sortedMonths) {
            List<Interview> monthInterviews = interviewsByMonth.get(month);
            Map<String, Double> monthlySkillAverages = calculateMonthlySkillAverages(monthInterviews);

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("date", month);
            monthData.put("interviewCount", monthInterviews.size());
            monthData.put("skills", monthlySkillAverages);

            // Calculate average overall score for the month
            double avgScore = monthInterviews.stream()
                .mapToDouble(interview -> {
                    List<QAHistory> history_items = interviewSessionService.getChatHistory(interview.getId());
                    return history_items.stream()
                        .mapToDouble(qa -> qa.getScore() != null ? qa.getScore() : 0)
                        .average().orElse(0);
                })
                .average().orElse(0);

            monthData.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
            history.add(monthData);
        }

        return history;
    }

    /**
     * Calculate skill averages for interviews in a specific month
     */
    private Map<String, Double> calculateMonthlySkillAverages(List<Interview> interviews) {
        Map<String, Double> skillSums = new HashMap<>();
        Map<String, Integer> skillCounts = new HashMap<>();
        String[] skills = {"technicalAccuracy", "depth", "experience", "communication"};

        for (Interview interview : interviews) {
            List<QAHistory> history = interviewSessionService.getChatHistory(interview.getId());

            for (QAHistory qa : history) {
                if (qa.getDetailedScores() != null) {
                    for (String skill : skills) {
                        Integer score = qa.getDetailedScores().get(skill);
                        if (score != null) {
                            skillSums.put(skill, skillSums.getOrDefault(skill, 0.0) + score);
                            skillCounts.put(skill, skillCounts.getOrDefault(skill, 0) + 1);
                        }
                    }
                }
            }
        }

        Map<String, Double> averages = new HashMap<>();
        for (String skill : skills) {
            double sum = skillSums.getOrDefault(skill, 0.0);
            int count = skillCounts.getOrDefault(skill, 0);
            averages.put(skill, count > 0 ? Math.round((sum / count) * 100.0) / 100.0 : 0.0);
        }

        return averages;
    }

    /**
     * Calculate skill improvement trends
     */
    private Map<String, Double> calculateSkillTrends(List<Map<String, Object>> skillHistory) {
        Map<String, Double> trends = new HashMap<>();

        if (skillHistory.size() < 2) {
            // Not enough data for trends
            String[] skills = {"technicalAccuracy", "depth", "experience", "communication"};
            for (String skill : skills) {
                trends.put(skill, 0.0);
            }
            return trends;
        }

        String[] skills = {"technicalAccuracy", "depth", "experience", "communication"};

        for (String skill : skills) {
            List<Double> skillScores = skillHistory.stream()
                .filter(month -> ((Map<String, Double>) month.get("skills")).containsKey(skill))
                .map(month -> ((Map<String, Double>) month.get("skills")).get(skill))
                .collect(Collectors.toList());

            if (skillScores.size() >= 2) {
                // Calculate linear trend (simplified)
                double first = skillScores.get(0);
                double last = skillScores.get(skillScores.size() - 1);
                double trend = (last - first) / (skillScores.size() - 1);
                trends.put(skill, Math.round(trend * 100.0) / 100.0);
            } else {
                trends.put(skill, 0.0);
            }
        }

        return trends;
    }

    /**
     * Get personalized skill recommendations
     */
    public Map<String, Object> getSkillRecommendations(Long userId) {
        Map<String, Object> progress = getSkillProgress(userId);
        Map<String, Object> recommendations = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Double> skillAverages = (Map<String, Double>) progress.get("skillAverages");

        if (skillAverages == null || skillAverages.isEmpty()) {
            recommendations.put("recommendations", new ArrayList<>());
            return recommendations;
        }

        List<Map<String, Object>> recList = new ArrayList<>();

        // Find skills that need improvement
        skillAverages.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue())
            .limit(2)
            .forEach(entry -> {
                String skill = entry.getKey();
                double score = entry.getValue();

                if (score < 6.0) { // Below average
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("skill", skill);
                    rec.put("currentScore", score);
                    rec.put("priority", "high");
                    rec.put("recommendation", generateSkillRecommendation(skill));
                    recList.add(rec);
                }
            });

        recommendations.put("recommendations", recList);
        return recommendations;
    }

    /**
     * Generate specific recommendation for a skill
     */
    private String generateSkillRecommendation(String skill) {
        switch (skill) {
            case "technicalAccuracy":
                return "Focus on fundamental technical concepts and practice coding problems regularly.";
            case "depth":
                return "Explore advanced topics in your tech stack and study system design principles.";
            case "experience":
                return "Document your project experiences and practice articulating technical decisions.";
            case "communication":
                return "Practice clear communication using frameworks like STAR method for behavioral questions.";
            default:
                return "Continue practicing and focus on comprehensive technical knowledge.";
        }
    }

    /**
     * Get skill improvement trends data
     */
    public Map<String, Object> getSkillTrends(Long userId) {
        Map<String, Object> progress = getSkillProgress(userId);
        @SuppressWarnings("unchecked")
        Map<String, Double> skillTrends = (Map<String, Double>) progress.get("skillTrends");

        Map<String, Object> result = new HashMap<>();
        result.put("trends", skillTrends != null ? skillTrends : new HashMap<>());
        return result;
    }
}
