package com.edushift.modules.students.service.impl;

import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.service.SectionService;
import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.dto.BulkImportPreviewResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.mapper.BulkImportJobMapper;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.modules.students.service.bulk.BulkImportException;
import com.edushift.modules.students.service.bulk.BulkImportHeaderAliases;
import com.edushift.modules.students.service.bulk.StudentBulkImportOptions;
import com.edushift.modules.students.service.bulk.StudentBulkImportParser;
import com.edushift.modules.students.service.bulk.StudentBulkImportRunner;
import com.edushift.modules.students.service.bulk.StudentTemplateGenerator;
import com.edushift.modules.teachers.bulk.TeacherBulkImportHeaderAliases;
import com.edushift.modules.teachers.bulk.TeacherBulkImportParser;
import com.edushift.modules.teachers.bulk.TeacherBulkImportRunner;
import com.edushift.modules.teachers.bulk.TeacherTemplateGenerator;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Default {@link BulkImportService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportServiceImpl implements BulkImportService {

	private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("xlsx");

	private final BulkImportJobRepository jobRepository;
	private final BulkImportJobMapper mapper;
	private final StudentBulkImportRunner runner;
	private final TeacherBulkImportRunner teacherRunner;
	private final StudentTemplateGenerator templateGenerator;
	private final TeacherTemplateGenerator teacherTemplateGenerator;
	private final StudentBulkImportParser parser;
	private final TeacherBulkImportParser teacherParser;
	private final SectionService sectionService;
	private final ObjectMapper objectMapper;

	@Override
	public BulkImportPreviewResponse previewStudentsImport(MultipartFile file) {
		validateUpload(file);
		byte[] payload = readPayload(file);
		try {
			return parser.preview(new ByteArrayInputStream(payload));
		}
		catch (BulkImportException e) {
			throw new BusinessException(e.getCode(), e.getMessage());
		}
	}

	@Override
	@Transactional
	public BulkImportJobResponse enqueueStudentsImport(MultipartFile file) {
		return enqueueStudentsImport(file, StudentBulkImportOptions.none());
	}

	@Override
	@Transactional
	public BulkImportJobResponse enqueueStudentsImport(MultipartFile file,
			StudentBulkImportOptions options) {
		validateUpload(file);
		StudentBulkImportOptions resolved = resolveOptions(options);
		byte[] payload = readPayload(file);
		BulkImportJob saved = persistPendingJob(BulkImportJobType.STUDENTS, file);
		dispatchStudentsAfterCommit(saved, payload, resolved);
		log.info("[bulk-import] enqueued -- jobId={} fileName={} size={} bytes section={}",
				saved.getPublicUuid(), saved.getFileName(), saved.getFileSizeBytes(),
				resolved.sectionPublicUuid());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public BulkImportJobResponse enqueueTeachersImport(MultipartFile file) {
		return enqueueTeachersImport(file, null);
	}

	@Override
	@Transactional
	public BulkImportJobResponse enqueueTeachersImport(MultipartFile file,
			Map<String, String> columnMapping) {
		validateUpload(file);
		Map<String, String> mapping = normalizeTeacherMapping(columnMapping);
		byte[] payload = readPayload(file);
		BulkImportJob saved = persistPendingJob(BulkImportJobType.TEACHERS, file);
		dispatchTeachersAfterCommit(saved, payload, mapping);
		log.info("[bulk-import/teachers] enqueued -- jobId={} fileName={} size={} bytes",
				saved.getPublicUuid(), saved.getFileName(), saved.getFileSizeBytes());
		return mapper.toResponse(saved);
	}

	@Override
	public BulkImportPreviewResponse previewTeachersImport(MultipartFile file) {
		validateUpload(file);
		byte[] payload = readPayload(file);
		try {
			return teacherParser.preview(new ByteArrayInputStream(payload));
		}
		catch (BulkImportException e) {
			throw new BusinessException(e.getCode(), e.getMessage());
		}
	}

	private StudentBulkImportOptions resolveOptions(StudentBulkImportOptions options) {
		StudentBulkImportOptions base = options == null
				? StudentBulkImportOptions.none() : options;

		Map<String, String> mapping = base.columnMapping();
		if (mapping != null && !mapping.isEmpty()) {
			validateColumnMapping(mapping);
		}

		UUID sectionUuid = base.sectionPublicUuid();
		UUID yearUuid = base.academicYearPublicUuid();
		LocalDate enrolledAt = base.enrolledAt();

		if (sectionUuid != null) {
			SectionResponse section = sectionService.getSection(sectionUuid);
			if (yearUuid == null) {
				yearUuid = section.academicYearPublicUuid();
			}
			else if (!yearUuid.equals(section.academicYearPublicUuid())) {
				throw new BusinessException("SECTION_YEAR_MISMATCH",
						"academicYearPublicUuid does not match the section's year");
			}
			if (enrolledAt == null) {
				enrolledAt = LocalDate.now();
			}
		}
		else if (yearUuid != null) {
			throw new BusinessException("INVALID_FILE",
					"academicYearPublicUuid requires sectionPublicUuid");
		}

		return new StudentBulkImportOptions(mapping, sectionUuid, yearUuid, enrolledAt);
	}

	private void validateColumnMapping(Map<String, String> mapping) {
		Set<String> known = Set.copyOf(BulkImportHeaderAliases.allCanonical());
		for (String key : mapping.keySet()) {
			if (!known.contains(key)) {
				throw new BusinessException("UNKNOWN_COLUMN",
						"Unknown canonical field in columnMapping: " + key);
			}
		}
		List<String> missingStudent = BulkImportHeaderAliases.unmappedRequired(mapping);
		if (!missingStudent.isEmpty()) {
			throw new BusinessException("MISSING_COLUMN_MAPPING",
					"columnMapping must include required field: " + missingStudent.getFirst());
		}
		if (BulkImportHeaderAliases.hasAnyGuardianMapping(mapping)) {
			List<String> missingGuardian = BulkImportHeaderAliases.unmappedGuardianRequired(mapping);
			if (!missingGuardian.isEmpty()) {
				throw new BusinessException("MISSING_COLUMN_MAPPING",
						"guardian columns require mapping for: " + missingGuardian.getFirst());
			}
		}
	}

	@Override
	public Map<String, String> parseColumnMappingJson(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			Map<String, String> raw = objectMapper.readValue(json,
					new TypeReference<Map<String, String>>() { });
			Map<String, String> cleaned = new LinkedHashMap<>();
			for (Map.Entry<String, String> e : raw.entrySet()) {
				if (e.getKey() == null || e.getKey().isBlank()) {
					continue;
				}
				if (e.getValue() == null || e.getValue().isBlank()) {
					continue;
				}
				cleaned.put(e.getKey().trim(), e.getValue().trim());
			}
			return cleaned.isEmpty() ? null : cleaned;
		}
		catch (IOException e) {
			throw new BusinessException("INVALID_FILE",
					"columnMapping must be a JSON object of string→string");
		}
	}

	private byte[] readPayload(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new BusinessException("INVALID_FILE",
					"Could not read the uploaded file: " + e.getMessage());
		}
	}

	private BulkImportJob persistPendingJob(BulkImportJobType type, MultipartFile file) {
		BulkImportJob job = new BulkImportJob();
		job.setJobType(type);
		job.setFileName(safeFileName(file));
		job.setFileSizeBytes(file.getSize());
		return jobRepository.saveAndFlush(job);
	}

	private void dispatchStudentsAfterCommit(BulkImportJob saved, byte[] payload,
			StudentBulkImportOptions options) {
		final UUID tenantId = TenantContext.currentRequired();
		final UUID jobId = saved.getId();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							runner.run(jobId, tenantId, payload, options);
						}
					});
		}
		else {
			runner.run(jobId, tenantId, payload, options);
		}
	}

	private Map<String, String> normalizeTeacherMapping(Map<String, String> columnMapping) {
		if (columnMapping == null || columnMapping.isEmpty()) {
			return null;
		}
		Map<String, String> cleaned = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : columnMapping.entrySet()) {
			String canonical = e.getKey();
			String header = e.getValue();
			if (canonical == null || canonical.isBlank() || header == null || header.isBlank()) {
				continue;
			}
			if (!TeacherBulkImportHeaderAliases.allCanonical().contains(canonical)) {
				throw new BusinessException("INVALID_MAPPING",
						"Unknown canonical field in columnMapping: " + canonical);
			}
			cleaned.put(canonical, header);
		}
		List<String> missing = TeacherBulkImportHeaderAliases.unmappedRequired(cleaned);
		if (!missing.isEmpty()) {
			throw new BusinessException("INVALID_MAPPING",
					"columnMapping must include required field: "
							+ TeacherBulkImportHeaderAliases.displayLabelEs(missing.getFirst()));
		}
		return cleaned;
	}

	private void dispatchTeachersAfterCommit(BulkImportJob saved, byte[] payload,
			Map<String, String> columnMapping) {
		final UUID tenantId = TenantContext.currentRequired();
		final UUID jobId = saved.getId();
		final Map<String, String> mapping = columnMapping;
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							teacherRunner.run(jobId, tenantId, payload, mapping);
						}
					});
		}
		else {
			teacherRunner.run(jobId, tenantId, payload, mapping);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public BulkImportJobResponse getJob(UUID publicUuid) {
		BulkImportJob job = jobRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("BulkImportJob", publicUuid));
		return mapper.toResponse(job);
	}

	@Override
	public byte[] generateStudentsTemplate() {
		return templateGenerator.generate();
	}

	@Override
	public byte[] generateTeachersTemplate() {
		return teacherTemplateGenerator.generate();
	}

	private void validateUpload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("INVALID_FILE",
					"Upload must be a non-empty .xlsx file");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new BusinessException("FILE_TOO_LARGE",
					"Upload exceeds the " + MAX_FILE_BYTES + " byte limit");
		}
		String extension = extensionOf(file.getOriginalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new BusinessException("UNSUPPORTED_FILE_TYPE",
					"Only .xlsx uploads are accepted (got: ." + extension + ")");
		}
	}

	private static String extensionOf(String name) {
		if (name == null) {
			return "";
		}
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx == name.length() - 1) {
			return "";
		}
		return name.substring(idx + 1).toLowerCase(Locale.ROOT);
	}

	private static String safeFileName(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (original == null || original.isBlank()) {
			return "upload.xlsx";
		}
		int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
		String trimmed = slash >= 0 ? original.substring(slash + 1) : original;
		return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
	}
}
