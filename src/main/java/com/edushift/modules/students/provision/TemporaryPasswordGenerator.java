package com.edushift.modules.students.provision;

/**
 * @deprecated Use {@link com.edushift.modules.auth.util.TemporaryPasswordGenerator}.
 * Kept as a thin delegate so existing CRED-7 call sites keep compiling
 * until imports are fully migrated.
 */
@Deprecated(forRemoval = false)
public final class TemporaryPasswordGenerator {

	private TemporaryPasswordGenerator() {}

	public static String generate() {
		return com.edushift.modules.auth.util.TemporaryPasswordGenerator.generate();
	}
}
