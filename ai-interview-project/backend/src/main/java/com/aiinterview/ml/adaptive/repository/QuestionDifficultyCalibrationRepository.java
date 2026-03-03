package com.aiinterview.ml.adaptive.repository;

import com.aiinterview.ml.adaptive.model.QuestionDifficultyCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionDifficultyCalibrationRepository extends JpaRepository<QuestionDifficultyCalibration, Long> {
    Optional<QuestionDifficultyCalibration> findByQuestionIdAndRoleId(String questionId, String roleId);
    List<QuestionDifficultyCalibration> findByRoleId(String roleId);
    List<QuestionDifficultyCalibration> findByRoleIdAndResponseCountGreaterThanEqual(String roleId, int minResponses);
}
