package com.edushift.modules.admissions.service;

import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.CreatePresentialAdmissionRequest;
import com.edushift.modules.admissions.dto.AdmissionInquiryResponse;
import com.edushift.modules.admissions.dto.AdmissionInquiryEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionFromInquiryRequest;
import com.edushift.modules.admissions.dto.OfferLinkResponse;
import com.edushift.modules.admissions.dto.PublicAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.PublicAdmissionCatalogResponse;
import com.edushift.modules.admissions.dto.PublicInquiryReceipt;
import com.edushift.modules.admissions.dto.PublicOfferResponse;
import com.edushift.modules.admissions.dto.UpdateAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.CreatePresentialInquiryRequest;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import java.util.List;
import java.util.UUID;

public interface AdmissionWorkflowService {
	AdmissionApplicationResponse createPresential(UUID yearPublicUuid,
			CreatePresentialAdmissionRequest request);

	PublicAdmissionCatalogResponse publicCatalog(String tenantName);

	PublicInquiryReceipt submitPublicInquiry(PublicAdmissionInquiryRequest request);

	AdmissionInquiryResponse createPresentialInquiry(UUID academicYearPublicUuid,
			CreatePresentialInquiryRequest request);

	List<AdmissionInquiryResponse> listInquiries(UUID academicYearPublicUuid);

	List<AdmissionInquiryEventResponse> inquiryHistory(UUID inquiryPublicUuid);

	AdmissionInquiryResponse updateInquiry(UUID inquiryPublicUuid, UpdateAdmissionInquiryRequest request);

	AdmissionApplicationResponse convertInquiry(UUID inquiryPublicUuid, CreateAdmissionFromInquiryRequest request);

	OfferLinkResponse issueOfferLink(UUID applicationPublicUuid);

	PublicOfferResponse getPublicOffer(String tenantName, String token);

	PublicOfferResponse respondToOffer(String token, boolean accepted);

	void openOffer(AdmissionApplication application);

	void processDueOfferReminders();

	AdmissionApplicationResponse completeEnrollment(UUID applicationPublicUuid,
			com.edushift.modules.admissions.dto.CompleteAdmissionEnrollmentRequest request);

	AdmissionApplicationResponse enrolledApplicationForRetry(UUID applicationPublicUuid);
}
