package com.edushift.modules.admin.prep;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrepPackageServiceImpl implements PrepPackageService {

	private final PrepPackageZipExporter zipExporter;

	@Override
	public PrepPackageDownload build(String schoolName, PrepPackageVariant variant) {
		String resolvedName = resolveSchoolName(schoolName);
		PrepPackageVariant resolvedVariant = variant == null
				? PrepPackageVariant.PRODUCTION
				: variant;
		return zipExporter.export(resolvedName, resolvedVariant);
	}

	static String resolveSchoolName(String schoolName) {
		if (schoolName == null || schoolName.isBlank()) {
			return "tu institucion";
		}
		return schoolName.trim();
	}
}
