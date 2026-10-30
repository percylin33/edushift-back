package com.edushift.modules.students.provision;

import java.util.UUID;

/**
 * CRED-7: mass-create portal accounts for students (and optional guardians)
 * enrolled in a section, returning one-time credentials for a printable sheet.
 */
public interface SectionAccountProvisionService {

	ProvisionSectionAccountsResponse provision(
			UUID sectionPublicUuid,
			ProvisionSectionAccountsRequest request);

	byte[] downloadSheet(UUID jobPublicUuid);

	/**
	 * Family handout ZIP: folder {@code Accesos - {grado/sección}/} with one PDF per student.
	 */
	ProvisionHandoutDownload downloadHandoutZip(UUID jobPublicUuid);
}
