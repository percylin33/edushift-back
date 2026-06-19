/**
 * LMS Quizzes module (Sprint 7b / BE-7b.0).
 *
 * <p>BE-7b.0 entrega las 5 tablas del schema ({@code lms_quizzes},
 * {@code lms_quiz_questions}, {@code lms_quiz_options},
 * {@code lms_quiz_attempts}, {@code lms_quiz_answers}) con sus
 * entities JPA y repositories Spring Data. <strong>No incluye
 * controllers, DTOs, services, mappers ni exceptions</strong>; esos
 * llegan en BE-7b.1 (builder + listing + auto-grading engine) y
 * BE-7b.2 (player + manual grading).
 *
 * <p>Source of truth:
 * {@code docs/product/sprints/sprint-07b-lms-intelligence.md}.
 * Las decisiones D-QUIZ-01..D-QUIZ-11 estan documentadas en el
 * header de {@code V35__create_lms_quizzes.sql}.
 */
package com.edushift.modules.quizzes;
