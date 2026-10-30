package com.edushift.modules.admissions.mapper;

import com.edushift.modules.admissions.dto.CreateAdmissionFromInquiryRequest;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.shared.exception.BadRequestException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AdmissionApplicantMapper {
    public void update(AdmissionApplication application, CreateAdmissionFromInquiryRequest request) {
        String phone = request.guardianPhone().trim();
        int digits = phone.replaceAll("\\D", "").length();
        if (!phone.matches("^[+0-9()\\-\\s]{7,32}$") || digits < 7 || digits > 15) {
            throw new BadRequestException("ADMISSION_CONTACT_INVALID", "Ingresa un teléfono válido de 7 a 15 dígitos.");
        }
        application.setSource(request.source());
        application.setStudentDocumentType(request.studentDocumentType());
        application.setStudentDocumentNumber(request.studentDocumentNumber().trim().toUpperCase(Locale.ROOT));
        application.setStudentFirstName(request.studentFirstName().trim());
        application.setStudentLastName(request.studentLastName().trim());
        application.setStudentSecondLastName(clean(request.studentSecondLastName()));
        application.setStudentBirthDate(request.studentBirthDate());
        application.setStudentGender(request.studentGender());
        application.setGuardianDocumentType(request.guardianDocumentType());
        application.setGuardianDocumentNumber(request.guardianDocumentNumber().trim().toUpperCase(Locale.ROOT));
        application.setGuardianFirstName(request.guardianFirstName().trim());
        application.setGuardianLastName(request.guardianLastName().trim());
        String email = clean(request.guardianEmail());
        application.setGuardianEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        application.setGuardianPhone(phone);
        application.setGuardianRelationship(request.guardianRelationship());
    }

    public CreateAdmissionFromInquiryRequest toRequest(AdmissionApplication application) {
        if (application.getStudentDocumentNumber() == null || application.getStudent() != null) return null;
        return new CreateAdmissionFromInquiryRequest(application.getSource(), application.getStudentDocumentType(),
                application.getStudentDocumentNumber(), application.getStudentFirstName(), application.getStudentLastName(),
                application.getStudentSecondLastName(), application.getStudentBirthDate(), application.getStudentGender(),
                application.getGuardianDocumentType(), application.getGuardianDocumentNumber(), application.getGuardianFirstName(),
                application.getGuardianLastName(), application.getGuardianEmail(), application.getGuardianPhone(),
                application.getGuardianRelationship(), null, application.getAcademicYear().getPublicUuid(), application.getGrade().getPublicUuid());
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
