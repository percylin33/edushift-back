package com.edushift.modules.admissions.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdmissionApplicationDetailResponse(
        AdmissionApplicationResponse application,
        StudentDetails student,
        List<GuardianDetails> guardians,
        InquiryDetails inquiry,
        List<AdmissionStatusEventResponse> history,
        CreateAdmissionFromInquiryRequest editableApplicant
) {
    public record StudentDetails(String fullName, DocumentType documentType, String documentNumber,
            LocalDate birthDate, Gender gender) {}
    public record GuardianDetails(String fullName, DocumentType documentType, String documentNumber,
            String email, String phone, RelationshipType relationship) {}
    public record InquiryDetails(UUID publicUuid, String contactName, String contactValue,
            String academicYearName, String gradeName, Instant createdAt, String note,
            List<AdmissionInquiryEventResponse> history) {}
}
