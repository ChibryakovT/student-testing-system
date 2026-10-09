package ru.edu.testing.dto;

/** Статистика ответов на конкретный вопрос: помогает найти самые сложные вопросы. */
public record QuestionStatRow(Long questionId, String text, long answers, long correctAnswers) {

    public QuestionStatRow(Long questionId, String text, Number answers, Number correctAnswers) {
        this(questionId, text, answers.longValue(), correctAnswers == null ? 0 : correctAnswers.longValue());
    }

    public int correctRate() {
        return answers == 0 ? 0 : (int) Math.round(correctAnswers * 100.0 / answers);
    }
}
