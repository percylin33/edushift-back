package com.edushift.modules.files.validator;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.exception.FileTypeNotAllowedException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reusable, framework-agnostic validation of an incoming upload
 * (Sprint 7a / BE-7a.0).
 *
 * <h3>Two checks</h3>
 * <ol>
 *   <li>Size — against {@link StorageProperties#getMaxFileSizeBytes()}.
 *       Spring's multipart filter raises
 *       {@code MaxUploadSizeExceededException} first (already mapped to
 *       {@code 413 PAYLOAD_TOO_LARGE} by
 *       {@code GlobalExceptionHandler}); this is the defence-in-depth
 *       for callers that bypass Spring (e.g. direct {@code InputStream}
 *       put through the storage service).</li>
 *   <li>Content type — against the
 *       {@link StorageProperties#getAllowedContentTypes() allow-list}
 *       (case-insensitive). Mismatch throws
 *       {@link FileTypeNotAllowedException} (415).</li>
 * </ol>
 */
@Component
public class FileValidator {

	private final StorageProperties props;

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
}
