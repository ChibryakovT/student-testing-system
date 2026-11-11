package ru.edu.testing.dto;

/**
 * Качество вопроса по первым попыткам студентов: доля верных ответов и различающая способность
 * (корреляция балла за вопрос с баллом за остальной тест). level: good, warn, bad или na (мало данных).
 */
public record QuestionQuality(Long questionId, int students, Integer facility, Double discrimination,
                              String level, String verdict) {
}
