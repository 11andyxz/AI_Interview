package com.aiinterview.ml.adaptive;

public class ResponseRecord {
    private String questionId;
    private double difficulty;
    private double discrimination;
    private double normalizedScore;

    public ResponseRecord() {}

    public ResponseRecord(String questionId, double difficulty, double discrimination, double normalizedScore) {
        this.questionId = questionId;
        this.difficulty = difficulty;
        this.discrimination = discrimination;
        this.normalizedScore = normalizedScore;
    }

    /**
     * Map a 0-100 evaluation score to a 0.0-1.0 normalized score.
     */
    public static ResponseRecord fromEvaluationScore(String questionId, double difficulty,
                                                      double discrimination, double evaluationScore) {
        return new ResponseRecord(questionId, difficulty, discrimination,
                Math.max(0.0, Math.min(1.0, evaluationScore / 100.0)));
    }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public double getDifficulty() { return difficulty; }
    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }

    public double getDiscrimination() { return discrimination; }
    public void setDiscrimination(double discrimination) { this.discrimination = discrimination; }

    public double getNormalizedScore() { return normalizedScore; }
    public void setNormalizedScore(double normalizedScore) { this.normalizedScore = normalizedScore; }
}
