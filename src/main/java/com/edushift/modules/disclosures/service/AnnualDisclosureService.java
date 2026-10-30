package com.edushift.modules.disclosures.service;

import com.edushift.modules.disclosures.dto.DisclosureContent;
import com.edushift.modules.disclosures.dto.DisclosureField;
import com.edushift.modules.disclosures.dto.DisclosureAttachment;
import com.edushift.modules.disclosures.dto.DisclosurePackageResponse;
import com.edushift.modules.disclosures.dto.DisclosurePublicationResponse;
import com.edushift.modules.disclosures.dto.DisclosureVersionSummary;
import java.util.List;
import java.util.UUID;

public interface AnnualDisclosureService {
	DisclosurePackageResponse getWorkspace(UUID academicYearPublicUuid);
	DisclosurePackageResponse save(UUID academicYearPublicUuid, DisclosureContent content);
	DisclosurePackageResponse saveField(UUID academicYearPublicUuid, String fieldKey,
			DisclosureField field, List<DisclosureAttachment> attachments);

	DisclosurePackageResponse copyPreviousYear(UUID academicYearPublicUuid);
	DisclosurePackageResponse submitForReview(UUID academicYearPublicUuid);
	DisclosurePackageResponse requestChanges(UUID academicYearPublicUuid, String note);
	DisclosurePackageResponse approve(UUID academicYearPublicUuid);
	DisclosurePackageResponse publish(UUID academicYearPublicUuid);
	List<DisclosureVersionSummary> listVersions(UUID academicYearPublicUuid);
	DisclosurePublicationResponse getPublication(UUID academicYearPublicUuid, Integer versionNumber);
	List<DisclosurePublicationResponse> listFamilyPublications(UUID parentUserUuid);
	DisclosurePublicationResponse getFamilyPublication(UUID academicYearPublicUuid, UUID parentUserUuid);
	byte[] renderWorkspacePdf(UUID academicYearPublicUuid);
	byte[] renderFamilyPdf(UUID academicYearPublicUuid, UUID parentUserUuid);
}
