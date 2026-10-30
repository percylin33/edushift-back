package com.edushift.modules.me.service;

import com.edushift.modules.me.dto.MeGradeResponse;
import com.edushift.modules.me.dto.MeGradesSummaryResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse;
import com.edushift.modules.me.dto.MeSectionPeriodResponse;
import com.edushift.modules.me.dto.MeSectionResponse;
import java.util.List;
import java.util.UUID;

/**
 * STUDENT-facing academic service surface (DEBT-STUDENT-PRIVACY / Fase 2 + V105).
 */
public interface MeAcademicService {

    /**
     * One card per active teacher assignment in the caller's enrolled sections
     * for the resolved period (current when {@code periodPublicUuid} is null).
     */
    List<MeSectionResponse> listMySections(UUID periodPublicUuid);

    /** @deprecated prefer {@link #listMySections(UUID)} */
    default List<MeSectionResponse> listMySections() {
        return listMySections(null);
    }

    /**
     * Distinct academic periods where the caller has at least one active
     * teacher assignment in an enrolled section.
     */
    List<MeSectionPeriodResponse> listMySectionPeriods();

    /**
     * Section/course detail. When {@code assignmentPublicUuid} is present,
     * content is filtered to that assignment (PUBLISHED tasks/quizzes only).
     */
    MeSectionDetailResponse getMySection(UUID sectionPublicUuid, UUID assignmentPublicUuid);

    /** @deprecated prefer {@link #getMySection(UUID, UUID)} */
    default MeSectionDetailResponse getMySection(UUID sectionPublicUuid) {
        return getMySection(sectionPublicUuid, null);
    }

    List<MeGradeResponse> listMyGrades();

    /**
     * Period summary for the caller. When {@code periodPublicUuid} is null,
     * the current academic period is used. Averages match the grade book
     * for each of the caller's assignments. Other students are never loaded.
     */
    MeGradesSummaryResponse summarizeMyGrades(UUID periodPublicUuid);
}
