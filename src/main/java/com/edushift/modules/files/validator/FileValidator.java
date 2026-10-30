package com.edushift.modules.files.validator;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.exception.FileTypeNotAllowedException;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reusable, framework-agnostic validation of an incoming upload
 * (Sprint 7a / BE-7a.0).
 *
 * <h3>Three checks</h3>
 * <ol>
 *   <li>Size — against {@link StorageProperties#getMaxFileSizeBytes()}.</li>
 *   <li>Content type — against the allow-list (case-insensitive).</li>
 *   <li>Magic numbers — validates the file signature matches the declared content type.</li>
 * </ol>
 */
@Component
public class FileValidator {

	private final StorageProperties props;

	private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
		"image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
		"image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
		"image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
		"application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}
	);

	public FileValidator(StorageProperties props) {
		this.props = props;
	}

	/**
	 * Validate a Spring {@code MultipartFile} (the usual entry point
	 * for controller-driven uploads).
	 */
	public void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new FileTypeNotAllowedException("<empty>");
		}
		if (file.getSize() > props.getMaxFileSizeBytes()) {
			throw new com.edushift.modules.files.exception.FileTooLargeException(
					file.getSize(), props.getMaxFileSizeBytes());
		}
		validateContentType(file.getContentType());
		validateMagicNumber(file);
	}

	/** Validate a content type string in isolation (e.g. raw stream uploads). */
	public void validateContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			throw new FileTypeNotAllowedException("<missing>");
		}
		String normalized = contentType.toLowerCase(Locale.ROOT);
		boolean allowed = props.getAllowedContentTypes().stream()
				.map(s -> s.toLowerCase(Locale.ROOT))
				.anyMatch(normalized::equals);
		if (!allowed) {
			throw new FileTypeNotAllowedException(contentType);
		}
	}

	/**
	 * Validates that the file's magic number (signature) matches the declared content type.
	 * This prevents attackers from uploading malicious files with spoofed content types.
	 */
	private void validateMagicNumber(MultipartFile file) {
		try {
			byte[] header = file.getBytes();
			if (header.length < 4) {
				throw new FileTypeNotAllowedException("<too small>");
			}

			String contentType = file.getContentType();
			byte[] expectedMagic = MAGIC_NUMBERS.get(contentType);
			if (expectedMagic == null) {
				return;  // Content-type without magic number validation
			}

			for (int i = 0; i < expectedMagic.length; i++) {
				if (header[i] != expectedMagic[i]) {
					throw new FileTypeNotAllowedException(file.getContentType());
				}
			}
		} catch (FileTypeNotAllowedException e) {
			throw e;
		} catch (Exception e) {
			throw new FileTypeNotAllowedException("<unreadable>");
		}
	}
}
