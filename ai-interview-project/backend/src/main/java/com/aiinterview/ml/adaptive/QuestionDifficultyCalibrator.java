package com.aiinterview.ml.adaptive;

import com.aiinterview.ml.adaptive.model.QuestionDifficultyCalibration;
import com.aiinterview.ml.adaptive.repository.QuestionDifficultyCalibrationRepository;
import com.aiinterview.model.InterviewMessage;
import com.aiinterview.repository.InterviewMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Calibrates question difficulty from historical response data using
 * Welford's online algorithm for running mean and variance, then maps
 * to IRT parameters.
 *
 * difficulty_b = -ln(meanScore / (1 - meanScore))  (logit transform)
 * discrimination_a is estimated from the inverse of score standard deviation.
 */
@Service
public class QuestionDifficultyCalibrator {

    private static final Logger logger = LoggerFactory.getLogger(QuestionDifficultyCalibrator.class);

    private final QuestionDifficultyCalibrationRepository calibrationRepository;
    private final InterviewMessageRepository messageRepository;

    public QuestionDifficultyCalibrator(QuestionDifficultyCalibrationRepository calibrationRepository,
                                         InterviewMessageRepository messageRepository) {
        this.calibrationRepository = calibrationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Record a new response and update calibration incrementally using Welford's algorithm.
     * Welford's: for each new value x:
     *   n = n + 1
     *   delta = x - mean
     *   mean = mean + delta / n
     *   delta2 = x - mean
     *   M2 = M2 + delta * delta2
     *   variance = M2 / (n - 1)
     */
    @Transactional
    public void recordResponse(String questionId, String roleId, double normalizedScore) {
        QuestionDifficultyCalibration cal = calibrationRepository
                .findByQuestionIdAndRoleId(questionId, roleId)
                .orElseGet(() -> {
                    QuestionDifficultyCalibration newCal = new QuestionDifficultyCalibration();
                    newCal.setQuestionId(questionId);
                    newCal.setRoleId(roleId);
                    return newCal;
                });

        int n = cal.getResponseCount() + 1;
        double oldMean = cal.getMeanScore();
        double oldVariance = cal.getScoreVariance();

        double delta = normalizedScore - oldMean;
        double newMean = oldMean + delta / n;
        double delta2 = normalizedScore - newMean;

        double m2 = (n > 2) ? oldVariance * (n - 2) + delta * delta2 : delta * delta2;
        double newVariance = (n > 1) ? m2 / (n - 1) : 0.25;

        cal.setResponseCount(n);
        cal.setMeanScore(newMean);
        cal.setScoreVariance(newVariance);

        recalculateIrtParameters(cal);

        cal.setLastCalibratedAt(LocalDateTime.now());
        calibrationRepository.save(cal);
    }

    /**
     * Bootstrap initial calibrations from all existing interview_message evaluation data.
     */
    @Transactional
    public void bootstrapFromEvaluationData() {
        logger.info("Bootstrapping difficulty calibrations from historical data...");

        List<InterviewMessage> messages = messageRepository.findAll();
        Map<String, List<Double>> questionScores = new HashMap<>();

        for (InterviewMessage msg : messages) {
            if (msg.getEvaluationScore() == null || msg.getUserMessage() == null) continue;

            String questionKey = msg.getInterviewId() + ":" + msg.getUserMessage().hashCode();
            double normalizedScore = Math.max(0.0, Math.min(1.0, msg.getEvaluationScore() / 100.0));
            questionScores.computeIfAbsent(questionKey, k -> new ArrayList<>()).add(normalizedScore);
        }

        int calibrated = 0;
        for (Map.Entry<String, List<Double>> entry : questionScores.entrySet()) {
            List<Double> scores = entry.getValue();
            if (scores.size() < 3) continue;

            String questionId = entry.getKey();
            double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
            double variance = scores.stream().mapToDouble(s -> (s - mean) * (s - mean)).sum() / (scores.size() - 1);

            QuestionDifficultyCalibration cal = calibrationRepository
                    .findByQuestionIdAndRoleId(questionId, "bootstrap")
                    .orElseGet(() -> {
                        QuestionDifficultyCalibration newCal = new QuestionDifficultyCalibration();
                        newCal.setQuestionId(questionId);
                        newCal.setRoleId("bootstrap");
                        return newCal;
                    });

            cal.setResponseCount(scores.size());
            cal.setMeanScore(mean);
            cal.setScoreVariance(variance);
            recalculateIrtParameters(cal);
            cal.setLastCalibratedAt(LocalDateTime.now());
            calibrationRepository.save(cal);
            calibrated++;
        }

        logger.info("Bootstrapped {} question calibrations", calibrated);
    }

    /**
     * Recalibrate all questions for a specific role from stored aggregate statistics.
     */
    @Transactional
    public void calibrateFromHistory(String roleId) {
        List<QuestionDifficultyCalibration> calibrations = calibrationRepository.findByRoleId(roleId);
        for (QuestionDifficultyCalibration cal : calibrations) {
            recalculateIrtParameters(cal);
            cal.setLastCalibratedAt(LocalDateTime.now());
            calibrationRepository.save(cal);
        }
        logger.info("Recalibrated {} questions for role {}", calibrations.size(), roleId);
    }

    public Optional<QuestionDifficultyCalibration> getCalibration(String questionId, String roleId) {
        return calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId);
    }

    /**
     * Convert running statistics to IRT parameters.
     * difficulty_b: logit of (1 - meanScore), higher mean → easier → lower b
     * discrimination_a: inversely proportional to score spread
     */
    private void recalculateIrtParameters(QuestionDifficultyCalibration cal) {
        double mean = Math.max(0.01, Math.min(0.99, cal.getMeanScore()));
        double b = -Math.log(mean / (1.0 - mean));
        b = Math.max(-3.0, Math.min(3.0, b));
        cal.setDifficultyB(b);

        double sd = Math.sqrt(Math.max(cal.getScoreVariance(), 0.01));
        double a = 1.0 / (sd * 1.7);
        a = Math.max(0.2, Math.min(3.0, a));
        cal.setDiscriminationA(a);
    }
}
