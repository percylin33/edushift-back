package com.edushift.modules.admin.prep;

/**
 * Builds the personalized school data-prep ZIP for Super Admin download.
 */
public interface PrepPackageService {

	/**
	 * @param schoolName display name; blank falls back to a generic placeholder
	 * @param variant    PILOT or PRODUCTION (null → PRODUCTION)
	 */
	PrepPackageDownload build(String schoolName, PrepPackageVariant variant);
}
