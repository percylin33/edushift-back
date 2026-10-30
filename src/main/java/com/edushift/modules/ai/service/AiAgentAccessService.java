package com.edushift.modules.ai.service;

import com.edushift.modules.ai.entity.AiAgentType;
import com.edushift.modules.ai.exception.ChatSessionNotFoundException;
import com.edushift.modules.family.service.FamilyService;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rechecks the authenticated user's relation to the subject on every operation. */
@Service
@RequiredArgsConstructor
public class AiAgentAccessService {

    private final StudentRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final FamilyService family;
    private final AiEntitlementService entitlements;

    public void requirePlan(AiAgentType type) {
        entitlements.require(feature(type));
    }

    @Transactional(readOnly = true)
    public UUID requireSubject(AiAgentType type, UUID userId, UUID requestedSubject) {
        entitlements.require(feature(type));
        return switch (type) {
            case TUTOR -> {
                var student = students.findByUserId(userId)
                        .orElseThrow(ChatSessionNotFoundException::new);
                if (requestedSubject != null
                        && !requestedSubject.equals(student.getPublicUuid())) {
                    throw new ChatSessionNotFoundException();
                }
                if (enrollments.findActiveByStudentFetchSection(student).isEmpty()) {
                    throw new ChatSessionNotFoundException();
                }
                yield student.getPublicUuid();
            }
            case TEACHER -> {
                if (requestedSubject != null) throw new ChatSessionNotFoundException();
                yield null;
            }
            case FAMILY -> {
                if (requestedSubject == null || family.listChildren(userId).stream()
                        .noneMatch(child -> requestedSubject.equals(child.publicUuid()))) {
                    throw new ChatSessionNotFoundException();
                }
                yield requestedSubject;
            }
        };
    }

    public static String feature(AiAgentType type) {
        return switch (type) {
            case TUTOR -> AiEntitlementService.TUTOR;
            case TEACHER -> AiEntitlementService.TEACHER;
            case FAMILY -> AiEntitlementService.FAMILY;
        };
    }
}
