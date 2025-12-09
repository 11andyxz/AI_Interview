package com.aiinterview.session.model;

public class QAHistory {
    private String questionId;
    private String questionText;
    private String answerText;
    private String evalComment;
    private String rubricLevel;

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
}

