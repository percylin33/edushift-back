package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.schedule.daytemplate.dto.CommitBootstrapRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.ScheduleSourceDocumentResponse;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleParseStatus;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleSourceDocument;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleSourceKind;
import com.edushift.modules.schedule.daytemplate.mapper.ScheduleSourceDocumentMapper;
import com.edushift.modules.schedule.daytemplate.repository.ScheduleSourceDocumentRepository;
import com.edushift.modules.schedule.daytemplate.service.DayScheduleTemplateService;
import com.edushift.modules.schedule.daytemplate.service.ScheduleBootstrapService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleBootstrapServiceImpl implements ScheduleBootstrapService {

	private static final String MODULE = "schedule-bootstrap";

	private final AcademicYearRepository yearRepository;
	private final ScheduleSourceDocumentRepository documentRepository;
	private final ScheduleSourceDocumentMapper documentMapper;
	private final DayScheduleTemplateService templateService;
	private final FileObjectService fileObjectService;

	@Override
	@Transactional
	public ScheduleSourceDocumentResponse upload(UUID yearUuid, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("FILE_REQUIRED", "Upload file is required");
		}
		AcademicYear year = yearRepository.findByPublicUuid(yearUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearUuid));

		String originalName = file.getOriginalFilename() != null
				? file.getOriginalFilename() : "upload.bin";
		String contentType = file.getContentType();
		ScheduleSourceKind kind = detectKind(originalName, contentType);
		UUID tenantId = TenantContext.currentRequired();

		FileObject stored = fileObjectService.store(tenantId, MODULE, file);
		fileObjectService.acquireReference(stored.getPublicUuid());

		ScheduleSourceDocument doc = new ScheduleSourceDocument();
		doc.setPublicUuid(stored.getPublicUuid());
		doc.setAcademicYear(year);
		doc.setKind(kind);
		doc.setOriginalFilename(originalName);
		doc.setContentType(contentType);
		doc.setStorageKey(stored.getRemoteKey());
		doc.setFileSizeBytes(stored.getSizeBytes());

		applyParse(doc, file, kind);

		ScheduleSourceDocument saved = documentRepository.saveAndFlush(doc);
		log.info("[schedule.bootstrap] uploaded -- uuid={} kind={} status={} key={}",
				saved.getPublicUuid(), kind, saved.getParseStatus(), saved.getStorageKey());
		return documentMapper.toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleSourceDocumentResponse> list(UUID yearUuid) {
		yearRepository.findByPublicUuid(yearUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearUuid));
		return documentRepository.findByAcademicYear_PublicUuidOrderByCreatedAtDesc(yearUuid)
				.stream()
				.map(documentMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public List<DayTemplateResponse> commit(UUID yearUuid, UUID documentUuid,
			CommitBootstrapRequest request) {
		AcademicYear year = yearRepository.findByPublicUuid(yearUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearUuid));
		ScheduleSourceDocument doc = documentRepository.findByPublicUuid(documentUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"ScheduleSourceDocument", documentUuid));
		if (!doc.getAcademicYear().getId().equals(year.getId())) {
			throw new BadRequestException("DOCUMENT_YEAR_MISMATCH",
					"Document does not belong to the given academic year");
		}

		// v1: seed default templates; jornada draft rows reserved for a later parser.
		templateService.seedDefaultTemplatesForYear(year);
		doc.setParseStatus(ScheduleParseStatus.COMMITTED);
		if (request != null && request.corrections() != null && !request.corrections().isEmpty()) {
			Map<String, Object> draft = doc.getParsedDraftJson() == null
					? new HashMap<>()
					: new HashMap<>(doc.getParsedDraftJson());
			draft.put("corrections", request.corrections());
			doc.setParsedDraftJson(draft);
		}
		documentRepository.save(doc);
		return templateService.listByYear(yearUuid);
	}

	private void applyParse(ScheduleSourceDocument doc, MultipartFile file, ScheduleSourceKind kind) {
		Map<String, Object> draft = emptyDraft();
		switch (kind) {
			case PRIOR_YEAR_PDF, PRIOR_YEAR_IMAGE -> {
				doc.setParseStatus(ScheduleParseStatus.REFERENCE_ONLY);
				doc.setParsedDraftJson(draft);
				doc.setParseError(null);
			}
			case PRIOR_YEAR_CSV -> {
				try {
					List<String> lines = readCsvLines(file);
					draft.put("csvLines", lines);
					draft.put("note", "CSV lines captured; map columns in FE before commit");
					doc.setParseStatus(ScheduleParseStatus.PARSED);
					doc.setParsedDraftJson(draft);
					doc.setParseError(null);
				} catch (Exception ex) {
					doc.setParseStatus(ScheduleParseStatus.FAILED);
					doc.setParseError(trimError(ex.getMessage()));
					doc.setParsedDraftJson(draft);
				}
			}
			case PRIOR_YEAR_XLSX -> {
				// Lightweight v1: empty draft + hint to use CSV (full POI mapping later).
				draft.put("note",
						"XLSX accepted; use CSV for row-level draft in v1, or paste rows after download template");
				doc.setParseStatus(ScheduleParseStatus.PARSED);
				doc.setParsedDraftJson(draft);
				doc.setParseError(null);
			}
			default -> {
				doc.setParseStatus(ScheduleParseStatus.UPLOADED);
				doc.setParsedDraftJson(draft);
			}
		}
	}

	private static Map<String, Object> emptyDraft() {
		Map<String, Object> draft = new LinkedHashMap<>();
		draft.put("jornadaRows", new ArrayList<>());
		draft.put("horarioRows", new ArrayList<>());
		draft.put("note", "Paste rows after download template");
		return draft;
	}

	private static List<String> readCsvLines(MultipartFile file) throws Exception {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			int max = 500;
			while ((line = reader.readLine()) != null && lines.size() < max) {
				if (!line.isBlank()) {
					lines.add(line);
				}
			}
		}
		return lines;
	}

	private static ScheduleSourceKind detectKind(String filename, String contentType) {
		String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (name.endsWith(".csv") || ct.contains("csv") || ct.contains("text/plain")) {
			return ScheduleSourceKind.PRIOR_YEAR_CSV;
		}
		if (name.endsWith(".xlsx") || name.endsWith(".xls")
				|| ct.contains("spreadsheet") || ct.contains("excel")) {
			return ScheduleSourceKind.PRIOR_YEAR_XLSX;
		}
		if (name.endsWith(".pdf") || ct.contains("pdf")) {
			return ScheduleSourceKind.PRIOR_YEAR_PDF;
		}
		if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
				|| name.endsWith(".webp") || ct.startsWith("image/")) {
			return ScheduleSourceKind.PRIOR_YEAR_IMAGE;
		}
		throw new BadRequestException("UNSUPPORTED_SCHEDULE_FILE",
				"Supported types: csv, xlsx, pdf, png/jpg/webp");
	}

	private static String trimError(String message) {
		if (message == null) {
			return "Parse failed";
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}
}
