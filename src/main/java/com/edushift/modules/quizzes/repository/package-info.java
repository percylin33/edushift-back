/**
 * Spring Data repositories for the LMS Quizzes module
 * (Sprint 7b / BE-7b.0). All five repositories
 * ({@code QuizRepository}, {@code QuizQuestionRepository},
 * {@code QuizOptionRepository}, {@code QuizAttemptRepository},
 * {@code QuizAnswerRepository}) are tenant-scoped by
 * Hibernate's {@code @TenantId} discriminator — no method
 * writes raw SQL bypassing it, and no query trusts a
 * client-supplied {@code tenant_id}.
 */
package com.edushift.modules.quizzes.repository;
