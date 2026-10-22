package com.edushift.modules.admissions.dto;

import java.util.UUID;

public record AdmissionSectionVacancyResponse(UUID publicUuid, String sectionName,
        Integer capacity, long activeEnrollments, Integer availableSeats) {}
