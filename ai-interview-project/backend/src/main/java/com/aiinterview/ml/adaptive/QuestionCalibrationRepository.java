package com.aiinterview.ml.adaptive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for question difficulty calibration
 */
@Repository
public interface QuestionCalibrationRepository extends JpaRepository<QuestionCalibration, Long> {
    
    /**
     * Find calibration by question and role
     */
    Optional<QuestionCalibration> findByQuestionIdAndRoleId(String questionId, String roleId);
    
    /**
     * Find all calibrations for a role
     */
    List<QuestionCalibration> findByRoleId(String roleId);
    
    /**
     * Find calibrations with sufficient response data
     */
    @Query("SELECT qc FROM QuestionCalibration qc " +
           "WHERE qc.roleId = :roleId AND qc.responseCount >= :minResponses " +
           "ORDER BY qc.responseCount DESC")
    List<QuestionCalibration> findWellCalibratedQuestions(
        @Param("roleId") String roleId,
        @Param("minResponses") int minResponses
    );
    
    /**
     * Find questions within difficulty range
     */
    @Query("SELECT qc FROM QuestionCalibration qc " +
           "WHERE qc.roleId = :roleId " +
           "AND qc.difficultyB BETWEEN :minDifficulty AND :maxDifficulty " +
           "AND qc.responseCount >= :minResponses")
    List<QuestionCalibration> findQuestionsInDifficultyRange(
        @Param("roleId") String roleId,
        @Param("minDifficulty") double minDifficulty,
        @Param("maxDifficulty") double maxDifficulty,
        @Param("minResponses") int minResponses
    );
}
