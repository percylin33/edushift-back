package com.edushift.modules.disclosures.service.impl;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.admissions.dto.GradeVacancyResponse;
import com.edushift.modules.admissions.service.AdmissionService;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.disclosures.dto.DisclosureAttachment;
import com.edushift.modules.disclosures.dto.DisclosureCatalog;
import com.edushift.modules.disclosures.dto.DisclosureContent;
import com.edushift.modules.disclosures.dto.DisclosureField;
import com.edushift.modules.disclosures.dto.DisclosureFieldDefinition;
import com.edushift.modules.disclosures.dto.DisclosureFieldState;
import com.edushift.modules.disclosures.dto.DisclosurePackageResponse;
import com.edushift.modules.disclosures.dto.DisclosurePublicationResponse;
import com.edushift.modules.disclosures.dto.DisclosureVersionSummary;
import com.edushift.modules.disclosures.entity.AnnualDisclosurePackage;
import com.edushift.modules.disclosures.entity.AnnualDisclosurePublication;
import com.edushift.modules.disclosures.entity.DisclosureStatus;
import com.edushift.modules.disclosures.mapper.AnnualDisclosureMapper;
import com.edushift.modules.disclosures.pdf.DisclosurePdfGenerator;
import com.edushift.modules.disclosures.repository.AnnualDisclosurePackageRepository;
import com.edushift.modules.disclosures.repository.AnnualDisclosurePublicationRepository;
import com.edushift.modules.disclosures.service.AnnualDisclosureService;
import com.edushift.modules.files.entity.FileUploadStatus;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.entity.BillingPlanRule;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.repository.BillingPlanRuleRepository;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleBlockRepository;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleTemplateRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates state transitions, tenant-owned files, audit history and family delivery. */
@Service
@RequiredArgsConstructor
public class AnnualDisclosureServiceImpl implements AnnualDisclosureService {
	private static final String RESOURCE = "AnnualDisclosurePackage";
	private static final Set<String> SYSTEM_MANAGED_FIELDS = Set.of("enrollment_fee", "monthly_tuition",
			"tuition_payment_dates", "tuition_history", "vacancies", "max_students_per_class", "school_calendar",
			"school_schedule");
	private static final DateTimeFormatter SCHEDULE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private final AnnualDisclosurePackageRepository packageRepository;
	private final AnnualDisclosurePublicationRepository publicationRepository;
	private final AcademicYearRepository academicYearRepository;
	private final AcademicPeriodRepository academicPeriodRepository;
	private final SectionRepository sectionRepository;
	private final AdmissionService admissionService;
	private final PaymentConceptRepository paymentConceptRepository;
	private final BillingPlanRuleRepository billingPlanRuleRepository;
	private final AcademicLevelRepository academicLevelRepository;
	private final GradeRepository gradeRepository;
	private final DayScheduleTemplateRepository dayScheduleTemplateRepository;
	private final DayScheduleBlockRepository dayScheduleBlockRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final FileObjectService fileObjectService;
	private final TenantRepository tenantRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AnnualDisclosureMapper mapper;
	private final DisclosurePdfGenerator pdfGenerator;
	private final AuditLogger auditLogger;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional(readOnly = true)
	public DisclosurePackageResponse getWorkspace(UUID academicYearPublicUuid) {
		AcademicYear year = findYear(academicYearPublicUuid);
		return packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid)
				.map(pkg -> withSystemData(mapper.toResponse(pkg, latestVersion(pkg.getId())), year))
				.orElseGet(() -> withSystemData(mapper.emptyResponse(year), year));
	}

	@Override
	@Transactional
	public DisclosurePackageResponse save(UUID academicYearPublicUuid, DisclosureContent content) {
		AcademicYear year = findYear(academicYearPublicUuid);
		content = applySystemData(year, content);
		validateContent(content);
		UUID actor = actorId();
		AnnualDisclosurePackage pkg = packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid)
				.orElseGet(() -> {
					AnnualDisclosurePackage fresh = new AnnualDisclosurePackage();
					fresh.setAcademicYear(year);
					fresh.setContent(mapper.toMap(DisclosureContent.empty()));
					return fresh;
				});
		boolean created = pkg.getId() == null;
		if (pkg.getStatus() == DisclosureStatus.IN_REVIEW || pkg.getStatus() == DisclosureStatus.APPROVED) {
			throw new ConflictException("DISCLOSURE_LOCKED", "Solicita cambios al expediente antes de editarlo.");
		}
		if (pkg.getStatus() == DisclosureStatus.PUBLISHED) {
			pkg.setRevisionNumber(latestVersion(pkg.getId()) + 1);
			pkg.setStatus(DisclosureStatus.DRAFT);
			pkg.setSubmittedAt(null);
			pkg.setSubmittedBy(null);
			pkg.setApprovedAt(null);
			pkg.setApprovedBy(null);
			pkg.setReviewNote(null);
		}
		pkg.setContent(mapper.toMap(content));
		AnnualDisclosurePackage saved = packageRepository.save(pkg);
		auditLogger.log(created ? AuditAction.CREATE : AuditAction.UPDATE, RESOURCE,
				saved.getPublicUuid(), created ? "Se creó el expediente anual" : "Se guardó el expediente anual",
				Map.of("academicYear", year.getName(), "revision", saved.getRevisionNumber()), "disclosures");
		return withSystemData(mapper.toResponse(saved, latestVersion(saved.getId())), year);
	}

	@Override
	@Transactional
	public DisclosurePackageResponse saveField(UUID academicYearPublicUuid, String fieldKey,
			DisclosureField field, List<DisclosureAttachment> attachments) {
		AcademicYear year = findYear(academicYearPublicUuid);
		DisclosureFieldDefinition definition = DisclosureCatalog.byKey().get(fieldKey);
		if (definition == null) {
			throw new BadRequestException("DISCLOSURE_FIELD_UNKNOWN", "El campo no pertenece al catálogo vigente.");
		}
		if (SYSTEM_MANAGED_FIELDS.contains(fieldKey)) {
			throw new BadRequestException("DISCLOSURE_SYSTEM_FIELD_READ_ONLY", "Este dato se completa automáticamente desde EduShift.");
		}
		if (field == null || field.state() == null) {
			throw new BadRequestException("DISCLOSURE_FIELD_STATE_REQUIRED", "Indica el estado de la respuesta.");
		}

		AnnualDisclosurePackage pkg = packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid)
				.orElseGet(() -> {
					AnnualDisclosurePackage fresh = new AnnualDisclosurePackage();
					fresh.setAcademicYear(year);
					fresh.setContent(mapper.toMap(DisclosureContent.empty()));
					return fresh;
				});
		boolean created = pkg.getId() == null;
		if (pkg.getStatus() == DisclosureStatus.IN_REVIEW || pkg.getStatus() == DisclosureStatus.APPROVED) {
			throw new ConflictException("DISCLOSURE_LOCKED", "Solicita cambios al expediente antes de editarlo.");
		}
		if (pkg.getStatus() == DisclosureStatus.PUBLISHED) {
			pkg.setRevisionNumber(latestVersion(pkg.getId()) + 1);
			pkg.setStatus(DisclosureStatus.DRAFT);
			pkg.setSubmittedAt(null);
			pkg.setSubmittedBy(null);
			pkg.setApprovedAt(null);
			pkg.setApprovedBy(null);
			pkg.setReviewNote(null);
		}

		DisclosureContent current = mapper.toContent(pkg.getContent());
		Map<String, DisclosureField> fields = new LinkedHashMap<>(current.fields() == null ? Map.of() : current.fields());
		fields.put(fieldKey, new DisclosureField(field.state(), field.value(), field.note(), field.sources(),
				field.documentOnly(), false, null));
		List<DisclosureAttachment> mergedAttachments = attachments == null ? current.attachments() : attachments;
		DisclosureContent updated = applySystemData(year, new DisclosureContent(fields, mergedAttachments,
				current.enrollmentDisclosureDeadline(), current.yearCloseDisclosureDeadline(), current.additionalInformation()));
		validateContent(updated);
		pkg.setContent(mapper.toMap(updated));
		AnnualDisclosurePackage saved = packageRepository.save(pkg);
		auditLogger.log(created ? AuditAction.CREATE : AuditAction.UPDATE, RESOURCE, saved.getPublicUuid(),
				created ? "Se creó el expediente guardando un paso" : "Se guardó un paso del expediente",
				Map.of("academicYear", year.getName(), "field", definition.label(), "revision", saved.getRevisionNumber()), "disclosures");
		return withSystemData(mapper.toResponse(saved, latestVersion(saved.getId())), year);
	}

	@Override
	@Transactional
	public DisclosurePackageResponse copyPreviousYear(UUID academicYearPublicUuid) {
		AcademicYear targetYear = findYear(academicYearPublicUuid);
		AnnualDisclosurePackage target = packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid).orElse(null);
		if (target != null && (target.getStatus() != DisclosureStatus.DRAFT
				|| latestVersion(target.getId()) > 0 || !isEmptyContent(mapper.toContent(target.getContent())))) {
			throw new ConflictException("DISCLOSURE_COPY_REQUIRES_EMPTY_DRAFT", "La copia del año anterior solo está disponible en un borrador vacío.");
		}
		AcademicYear previousYear = academicYearRepository.findAllByOrderByStartDateDesc().stream()
				.filter(year -> year.getStartDate().isBefore(targetYear.getStartDate())).findFirst()
				.orElseThrow(() -> new NotFoundException("DISCLOSURE_PREVIOUS_YEAR_NOT_FOUND", "No se encontró un año académico anterior para copiar."));
		AnnualDisclosurePackage previousPackage = packageRepository.findByAcademicYear_PublicUuid(previousYear.getPublicUuid())
				.orElseThrow(() -> new NotFoundException("DISCLOSURE_PREVIOUS_YEAR_NOT_FOUND", "El año académico anterior aún no tiene un expediente para copiar."));
		Map<String, Object> source = publicationRepository.findFirstByPackageIdOrderByVersionNumberDesc(previousPackage.getId())
				.map(AnnualDisclosurePublication::getContent).orElse(previousPackage.getContent());
		DisclosureContent copied = copyForNewYear(mapper.toContent(source), previousYear.getName());
		if (target == null) {
			target = new AnnualDisclosurePackage();
			target.setAcademicYear(targetYear);
		}
		target.setContent(mapper.toMap(copied));
		target.setStatus(DisclosureStatus.DRAFT);
		target.setSubmittedAt(null);
		target.setSubmittedBy(null);
		target.setApprovedAt(null);
		target.setApprovedBy(null);
		target.setPublishedAt(null);
		target.setPublishedBy(null);
		target.setReviewNote(null);
		AnnualDisclosurePackage saved = packageRepository.save(target);
		auditLogger.log(AuditAction.CUSTOM, RESOURCE, saved.getPublicUuid(), "Se inició el expediente desde el año anterior",
				Map.of("academicYear", targetYear.getName(), "copiedFromAcademicYear", previousYear.getName()), "disclosures");
		return withSystemData(mapper.toResponse(saved, latestVersion(saved.getId())), targetYear);
	}

	@Override
	@Transactional
	public DisclosurePackageResponse submitForReview(UUID academicYearPublicUuid) {
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		requireStatus(pkg, DisclosureStatus.DRAFT, "DISCLOSURE_NOT_DRAFT");
		DisclosureContent currentContent = applySystemData(pkg.getAcademicYear(), mapper.toContent(pkg.getContent()));
		validateContent(currentContent);
		pkg.setContent(mapper.toMap(currentContent));
		pkg.setStatus(DisclosureStatus.IN_REVIEW);
		pkg.setSubmittedAt(Instant.now());
		pkg.setSubmittedBy(actorId());
		pkg.setReviewNote(null);
		auditLogger.log(AuditAction.CUSTOM, RESOURCE, pkg.getPublicUuid(), "Expediente enviado a revisión",
				Map.of("revision", pkg.getRevisionNumber()), "disclosures");
		return mapper.toResponse(pkg, latestVersion(pkg.getId()));
	}

	@Override
	@Transactional
	public DisclosurePackageResponse requestChanges(UUID academicYearPublicUuid, String note) {
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		requireStatus(pkg, DisclosureStatus.IN_REVIEW, "DISCLOSURE_NOT_IN_REVIEW");
		pkg.setStatus(DisclosureStatus.DRAFT);
		pkg.setReviewNote(note.trim());
		pkg.setSubmittedAt(null);
		pkg.setSubmittedBy(null);
		auditLogger.log(AuditAction.CUSTOM, RESOURCE, pkg.getPublicUuid(), "Se solicitaron cambios al expediente",
				Map.of("revision", pkg.getRevisionNumber()), "disclosures");
		return mapper.toResponse(pkg, latestVersion(pkg.getId()));
	}

	@Override
	@Transactional
	public DisclosurePackageResponse approve(UUID academicYearPublicUuid) {
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		requireStatus(pkg, DisclosureStatus.IN_REVIEW, "DISCLOSURE_NOT_IN_REVIEW");
		pkg.setStatus(DisclosureStatus.APPROVED);
		pkg.setApprovedAt(Instant.now());
		pkg.setApprovedBy(actorId());
		auditLogger.log(AuditAction.CUSTOM, RESOURCE, pkg.getPublicUuid(), "Expediente aprobado para publicación",
				Map.of("revision", pkg.getRevisionNumber()), "disclosures");
		return mapper.toResponse(pkg, latestVersion(pkg.getId()));
	}

	@Override
	@Transactional
	public DisclosurePackageResponse publish(UUID academicYearPublicUuid) {
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		requireStatus(pkg, DisclosureStatus.APPROVED, "DISCLOSURE_NOT_APPROVED");
		if (pkg.getApprovedBy() == null || pkg.getApprovedAt() == null) {
			throw new ConflictException("DISCLOSURE_APPROVAL_REQUIRED", "El expediente debe ser aprobado antes de publicar.");
		}
		UUID actor = actorId();
		int versionNumber = latestVersion(pkg.getId()) + 1;
		Instant now = Instant.now();
		AnnualDisclosurePublication publication = new AnnualDisclosurePublication();
		publication.setPackageId(pkg.getId());
		publication.setVersionNumber(versionNumber);
		publication.setContent(copyContent(pkg.getContent()));
		publication.setContentSha256(sha256(publication.getContent()));
		publication.setApprovedAt(pkg.getApprovedAt());
		publication.setApprovedBy(pkg.getApprovedBy());
		publication.setPublishedAt(now);
		publication.setPublishedBy(actor);
		publicationRepository.save(publication);
		pkg.setStatus(DisclosureStatus.PUBLISHED);
		pkg.setPublishedAt(now);
		pkg.setPublishedBy(actor);
		List<UUID> recipients = studentGuardianRepository.findAllGuardianUserIdsWithPortalAccess()
				.stream().distinct().toList();
		if (!recipients.isEmpty()) {
			List<NotificationService.NotifyCommand> notices = recipients.stream()
					.map(userId -> NotificationService.NotifyCommand.builder()
							.recipient(userId)
							.template("DISCLOSURE_PUBLISHED")
							.category(Notification.Category.ANNOUNCEMENT)
							.channel(Notification.Channel.BOTH)
							.payload(Map.of("tenantName", schoolName(), "academicYear", pkg.getAcademicYear().getName(),
									"version", versionNumber, "familyUrl", "/family/disclosures"))
							.build())
					.toList();
			notificationService.notifyAll(notices);
		}
		auditLogger.log(AuditAction.CUSTOM, RESOURCE, pkg.getPublicUuid(), "Expediente anual publicado a las familias",
				Map.of("academicYear", pkg.getAcademicYear().getName(), "version", versionNumber,
						"notifiedFamilies", recipients.size(), "contentSha256", publication.getContentSha256()), "disclosures");
		return mapper.toResponse(pkg, versionNumber);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DisclosureVersionSummary> listVersions(UUID academicYearPublicUuid) {
		findYear(academicYearPublicUuid);
		AnnualDisclosurePackage pkg = packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid).orElse(null);
		if (pkg == null) return List.of();
		return publicationRepository.findAllByPackageIdOrderByVersionNumberDesc(pkg.getId())
				.stream().map(mapper::toVersionSummary).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public DisclosurePublicationResponse getPublication(UUID academicYearPublicUuid, Integer versionNumber) {
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		AnnualDisclosurePublication publication = versionNumber == null
				? publicationRepository.findFirstByPackageIdOrderByVersionNumberDesc(pkg.getId()).orElseThrow(
						() -> new NotFoundException("DISCLOSURE_PUBLICATION_NOT_FOUND", "Aún no hay una versión publicada."))
				: publicationRepository.findAllByPackageIdOrderByVersionNumberDesc(pkg.getId()).stream()
						.filter(item -> item.getVersionNumber() == versionNumber).findFirst()
						.orElseThrow(() -> new NotFoundException("DISCLOSURE_PUBLICATION_NOT_FOUND", "No se encontró la versión solicitada."));
		return mapper.toPublicationResponse(publication, pkg.getAcademicYear().getPublicUuid(), pkg.getAcademicYear().getName());
	}

	@Override
	@Transactional(readOnly = true)
	public List<DisclosurePublicationResponse> listFamilyPublications(UUID parentUserUuid) {
		requireLinkedParent(parentUserUuid);
		Map<UUID, AnnualDisclosurePublication> latest = new LinkedHashMap<>();
		for (AnnualDisclosurePublication publication : publicationRepository.findAllByOrderByPublishedAtDesc()) {
			latest.putIfAbsent(publication.getPackageId(), publication);
		}
		List<DisclosurePublicationResponse> result = new ArrayList<>();
		for (AnnualDisclosurePublication publication : latest.values()) {
			packageRepository.findById(publication.getPackageId()).ifPresent(pkg -> result.add(
					mapper.toFamilyPublicationResponse(publication, pkg.getAcademicYear().getPublicUuid(), pkg.getAcademicYear().getName())));
		}
		auditLogger.log(AuditAction.READ, "AnnualDisclosurePortal", null,
				"Consulta de expedientes anuales publicados", Map.of("publications", result.size()), "disclosures");
		return List.copyOf(result);
	}

	@Override
	@Transactional(readOnly = true)
	public DisclosurePublicationResponse getFamilyPublication(UUID academicYearPublicUuid, UUID parentUserUuid) {
		requireLinkedParent(parentUserUuid);
		AnnualDisclosurePackage pkg = requirePackage(academicYearPublicUuid);
		AnnualDisclosurePublication publication = publicationRepository.findFirstByPackageIdOrderByVersionNumberDesc(pkg.getId())
				.orElseThrow(() -> new NotFoundException("DISCLOSURE_PUBLICATION_NOT_FOUND", "Aún no hay una versión publicada."));
		DisclosurePublicationResponse response = mapper.toFamilyPublicationResponse(publication,
				pkg.getAcademicYear().getPublicUuid(), pkg.getAcademicYear().getName());
		auditLogger.log(AuditAction.READ, "AnnualDisclosurePublication", response.publicUuid(),
				"Consulta familiar de información anual publicada",
				Map.of("academicYear", response.academicYearName(), "version", response.versionNumber()), "disclosures");
		return response;
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] renderWorkspacePdf(UUID academicYearPublicUuid) {
		DisclosurePackageResponse workspace = getWorkspace(academicYearPublicUuid);
		byte[] bytes = pdfGenerator.generate(schoolName(), workspace.academicYearName(), workspace.latestPublishedVersion(),
				workspace.content(), workspace.status() != DisclosureStatus.PUBLISHED);
		auditLogger.log(AuditAction.EXPORT, RESOURCE, workspace.publicUuid(), "Descarga de copia de revisión del expediente",
				Map.of("academicYear", workspace.academicYearName(), "revision", workspace.revisionNumber()), "disclosures");
		return bytes;
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] renderFamilyPdf(UUID academicYearPublicUuid, UUID parentUserUuid) {
		DisclosurePublicationResponse publication = getFamilyPublication(academicYearPublicUuid, parentUserUuid);
		byte[] bytes = pdfGenerator.generate(schoolName(), publication.academicYearName(), publication.versionNumber(),
				publication.content(), false);
		auditLogger.log(AuditAction.EXPORT, "AnnualDisclosurePublication", publication.publicUuid(),
				"Descarga familiar de copia publicada",
				Map.of("academicYear", publication.academicYearName(), "version", publication.versionNumber()), "disclosures");
		return bytes;
	}

	private void validateContent(DisclosureContent content) {
		if (content == null || content.fields() == null) {
			throw new BadRequestException("DISCLOSURE_CONTENT_REQUIRED", "El expediente requiere un mapa de campos.");
		}
		for (Map.Entry<String, DisclosureField> entry : content.fields().entrySet()) {
			DisclosureFieldDefinition definition = DisclosureCatalog.byKey().get(entry.getKey());
			if (definition == null) {
				throw new BadRequestException("DISCLOSURE_FIELD_UNKNOWN", "El campo '" + entry.getKey() + "' no pertenece al catálogo vigente.");
			}
			DisclosureField field = entry.getValue();
			if (field == null || field.state() == null) {
				throw new BadRequestException("DISCLOSURE_FIELD_STATE_REQUIRED", "Indica si cada dato está completado, no aplica o está pendiente.");
			}
			if (field.state() == DisclosureFieldState.COMPLETED && isBlank(field.value())
					&& (!field.documentOnly() || field.sources().isEmpty())) {
				throw new BadRequestException("DISCLOSURE_FIELD_VALUE_REQUIRED", "Completa el valor de '" + definition.label() + "' o cambia su estado.");
			}
			if (field.documentOnly() && field.state() != DisclosureFieldState.COMPLETED) {
				throw new BadRequestException("DISCLOSURE_DOCUMENT_ANSWER_REQUIRES_COMPLETED", "Una respuesta basada en documento debe estar marcada como completada.");
			}
			if (field.state() == DisclosureFieldState.NOT_APPLICABLE && isBlank(field.note())) {
				throw new BadRequestException("DISCLOSURE_NOT_APPLICABLE_REASON_REQUIRED", "Explica por qué no aplica '" + definition.label() + "'.");
			}
		}
		if (content.attachments() != null) {
			var seen = new java.util.HashSet<UUID>();
			for (DisclosureAttachment attachment : content.attachments()) {
				if (!seen.add(attachment.filePublicUuid())) {
					throw new BadRequestException("DISCLOSURE_ATTACHMENT_DUPLICATE", "No repitas un mismo archivo en los anexos.");
				}
				var file = fileObjectService.findByPublicUuid(attachment.filePublicUuid())
						.orElseThrow(() -> new NotFoundException("DISCLOSURE_ATTACHMENT_NOT_FOUND", "No se encontró uno de los anexos del expediente."));
				if (file.getStatus() != FileUploadStatus.READY || file.getRemoteKey() == null
						|| !file.getRemoteKey().contains("/disclosures/")) {
					throw new BadRequestException("DISCLOSURE_ATTACHMENT_INVALID", "El anexo debe estar cargado y disponible en la carpeta de expedientes del colegio.");
				}
			}
		}
		var attachmentIds = content.attachments() == null ? java.util.Set.<UUID>of()
				: content.attachments().stream().map(DisclosureAttachment::filePublicUuid).collect(java.util.stream.Collectors.toSet());
		var familyVisibleAttachmentIds = content.attachments() == null ? java.util.Set.<UUID>of()
				: content.attachments().stream().filter(attachment -> Boolean.TRUE.equals(attachment.visibleToFamilies()))
					.map(DisclosureAttachment::filePublicUuid).collect(java.util.stream.Collectors.toSet());
		for (DisclosureField field : content.fields().values()) {
			if (field.sources() == null) continue;
			var sourceIds = new java.util.HashSet<UUID>();
			for (var source : field.sources()) {
				if (!sourceIds.add(source.filePublicUuid())) {
					throw new BadRequestException("DISCLOSURE_SOURCE_DUPLICATE", "No repitas el mismo documento como fuente de un dato.");
				}
				if (!attachmentIds.contains(source.filePublicUuid())) {
					throw new BadRequestException("DISCLOSURE_SOURCE_NOT_ATTACHED", "Cada fuente debe pertenecer a los documentos de este expediente.");
				}
			}
			if (field.documentOnly() && field.sources().stream().noneMatch(source -> familyVisibleAttachmentIds.contains(source.filePublicUuid()))) {
				throw new BadRequestException("DISCLOSURE_DOCUMENT_ANSWER_NOT_SHARED", "Comparte con las familias al menos una fuente usada como respuesta.");
			}
		}
	}

	private Map<String, Object> copyContent(Map<String, Object> content) {
		return objectMapper.convertValue(content, new com.fasterxml.jackson.core.type.TypeReference<>() { });
	}

	private String sha256(Map<String, Object> content) {
		try {
			byte[] bytes = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
					.writeValueAsString(content).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception exception) {
			throw new IllegalStateException("No se pudo calcular la huella del expediente publicado", exception);
		}
	}

	private AcademicYear findYear(UUID publicUuid) {
		return academicYearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new NotFoundException("ACADEMIC_YEAR_NOT_FOUND", "No se encontró el año académico."));
	}

	private AnnualDisclosurePackage requirePackage(UUID academicYearPublicUuid) {
		return packageRepository.findByAcademicYear_PublicUuid(academicYearPublicUuid)
				.orElseThrow(() -> new NotFoundException("DISCLOSURE_PACKAGE_NOT_FOUND", "Aún no existe un expediente para este año."));
	}

	private void requireStatus(AnnualDisclosurePackage pkg, DisclosureStatus expected, String code) {
		if (pkg.getStatus() != expected) {
			throw new ConflictException(code, "La acción no está disponible en el estado " + pkg.getStatus() + ".");
		}
	}

	private int latestVersion(UUID packageId) {
		return publicationRepository.findFirstByPackageIdOrderByVersionNumberDesc(packageId)
				.map(AnnualDisclosurePublication::getVersionNumber).orElse(0);
	}

	private UUID actorId() {
		return currentUserProvider.currentUserId()
				.orElseThrow(() -> new ForbiddenException("DISCLOSURE_ACTOR_REQUIRED", "Se requiere un usuario autenticado."));
	}

	private void requireLinkedParent(UUID parentUserUuid) {
		if (studentGuardianRepository.findActiveByGuardianUserId(parentUserUuid).isEmpty()) {
			throw new ForbiddenException("DISCLOSURE_FAMILY_LINK_REQUIRED", "El usuario no tiene un vínculo familiar activo en este colegio.");
		}
	}

	private String schoolName() {
		UUID tenantId = currentUserProvider.currentTenantId()
				.orElseThrow(() -> new ForbiddenException("TENANT_REQUIRED", "Se requiere un colegio activo."));
		return tenantRepository.findById(tenantId).map(Tenant::getName).orElse("Institución educativa");
	}

	private DisclosurePackageResponse withSystemData(DisclosurePackageResponse response, AcademicYear year) {
		DisclosureContent content = applySystemData(year, response.content());
		return new DisclosurePackageResponse(response.publicUuid(), response.academicYearPublicUuid(),
				response.academicYearName(), response.status(), response.revisionNumber(), response.latestPublishedVersion(),
				content, mapper.completeness(content), response.reviewNote(), response.submittedAt(), response.submittedBy(),
				response.approvedAt(), response.approvedBy(), response.publishedAt(), response.publishedBy());
	}

	/** Recalculates EduShift-owned values on the server so the browser never asks for them. */
	private DisclosureContent applySystemData(AcademicYear year, DisclosureContent content) {
		if (content == null) content = DisclosureContent.empty();
		Map<String, DisclosureField> fields = new LinkedHashMap<>();
		if (content.fields() != null) {
			content.fields().forEach((key, field) -> {
				if (field != null && !field.systemManaged()) fields.put(key, new DisclosureField(field.state(), field.value(), field.note(),
						field.sources(), field.documentOnly(), false, field.systemSource()));
			});
		}
		Map<String, SystemFieldValue> systemValues = new LinkedHashMap<>();
		List<PaymentConcept> concepts = paymentConceptRepository.findByActiveTrueAndDeletedFalseOrderBySortOrderAscNameAsc();
		List<BillingPlanRule> billingRules = billingPlanRuleRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
				.filter(rule -> rule.getDeletedAt() == null)
				.toList();
		Map<UUID, String> levelNames = academicLevelRepository.findAllById(billingRules.stream()
				.map(BillingPlanRule::getLevelId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()))
				.stream().collect(java.util.stream.Collectors.toMap(AcademicLevel::getId, AcademicLevel::getName));
		Map<UUID, String> gradeNames = gradeRepository.findAllById(billingRules.stream()
				.map(BillingPlanRule::getGradeId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()))
				.stream().collect(java.util.stream.Collectors.toMap(Grade::getId, Grade::getName));
		String tuition = formatTuitionPrices(concepts, billingRules, levelNames, gradeNames);
		systemValues.put("monthly_tuition", paymentConceptField(tuition, "pensiones"));
		String enrollment = formatConcepts(concepts, PaymentConcept.Category.ENROLLMENT);
		systemValues.put("enrollment_fee", paymentConceptField(enrollment, "matrícula"));
		String paymentDates = formatTuitionPaymentDates(concepts, billingRules, levelNames, gradeNames);
		systemValues.put("tuition_payment_dates", paymentConceptField(paymentDates, "fechas de pago de pensiones"));
		String tuitionHistory = buildTuitionHistory(year, content.fields(), tuition, enrollment);
		systemValues.put("tuition_history", new SystemFieldValue(tuitionHistory,
				"Historial acumulado de expedientes publicados y montos actuales de Pagos"));

		List<GradeVacancyResponse> vacancies = admissionService.listVacancies(year.getPublicUuid());
		String vacancyValue = vacancies.stream().filter(GradeVacancyResponse::capacityConfigured)
				.map(item -> item.gradeName() + ": " + item.availableSeats() + " vacante(s)")
				.collect(java.util.stream.Collectors.joining("; "));
		if (!vacancyValue.isBlank()) systemValues.put("vacancies", new SystemFieldValue(vacancyValue, "Vacantes calculadas por Admisiones"));

		List<Section> sections = sectionRepository.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(year);
		String capacities = sections.stream().filter(section -> section.getCapacity() != null)
				.map(section -> section.getGrade().getName() + " " + section.getName() + ": " + section.getCapacity() + " estudiantes")
				.collect(java.util.stream.Collectors.joining("; "));
		if (!capacities.isBlank()) systemValues.put("max_students_per_class", new SystemFieldValue(capacities, "Aforos configurados en las secciones del año"));

		StringBuilder calendar = new StringBuilder("Inicio: ").append(year.getStartDate())
				.append("; fin: ").append(year.getEndDate());
		List<AcademicPeriod> periods = academicPeriodRepository.findAllByYear(year);
		if (!periods.isEmpty()) {
			calendar.append(". Periodos: ").append(periods.stream()
					.map(period -> period.getName() + " (" + period.getStartDate() + " al " + period.getEndDate() + ")")
					.collect(java.util.stream.Collectors.joining("; ")));
		}
		systemValues.put("school_calendar", new SystemFieldValue(calendar.toString(), "Año y periodos académicos de EduShift"));
		String schoolSchedule = formatSchoolSchedule(year);
		systemValues.put("school_schedule", new SystemFieldValue(schoolSchedule,
				"Jornadas configuradas por nivel, grado y turno en EduShift",
				isBlank(schoolSchedule) ? DisclosureFieldState.PENDING : DisclosureFieldState.COMPLETED));

		systemValues.forEach((key, value) -> fields.put(key,
				new DisclosureField(value.state(), isBlank(value.value()) ? null : value.value(), null,
						List.of(), false, true, value.source())));
		return new DisclosureContent(fields, content.attachments() == null ? List.of() : content.attachments(),
				content.enrollmentDisclosureDeadline(), content.yearCloseDisclosureDeadline(), content.additionalInformation());
	}

	private String formatSchoolSchedule(AcademicYear year) {
		return dayScheduleTemplateRepository.findAllByAcademicYear(year).stream()
				.filter(template -> !template.isDeleted())
				.filter(template -> template.getDayStart() != null && template.getDayEnd() != null)
				.sorted(java.util.Comparator
						.comparing((DayScheduleTemplate template) -> template.getAcademicLevel().getName())
						.thenComparing(template -> template.getGrade() == null ? "" : template.getGrade().getName())
						.thenComparing(template -> template.getShift() == null ? "" : template.getShift()))
				.map(this::formatDayTemplate)
				.collect(java.util.stream.Collectors.joining("; "));
	}

	private String formatDayTemplate(DayScheduleTemplate template) {
		StringBuilder value = new StringBuilder(template.getAcademicLevel().getName());
		if (template.getGrade() != null) value.append(" · ").append(template.getGrade().getName());
		if (template.getShift() != null && !template.getShift().isBlank()) {
			value.append(" · Turno ").append(template.getShift().equalsIgnoreCase("MORNING") ? "mañana"
					: template.getShift().equalsIgnoreCase("AFTERNOON") ? "tarde" : template.getShift());
		}
		value.append(": ").append(formatTime(template.getDayStart())).append("–")
				.append(formatTime(template.getDayEnd()));
		List<String> breaks = dayScheduleBlockRepository.findByTemplateOrdered(template).stream()
				.filter(block -> block.getBlockType() == DayBlockType.RECESS || block.getBlockType() == DayBlockType.LUNCH)
				.map(block -> block.getLabel() + " " + formatTime(block.getStartTime()) + "–"
						+ formatTime(block.getEndTime()))
				.toList();
		if (!breaks.isEmpty()) value.append(" (").append(String.join(", ", breaks)).append(")");
		return value.toString();
	}

	private String formatTime(LocalTime time) {
		return time.format(SCHEDULE_TIME_FORMAT);
	}

	private String formatConcepts(List<PaymentConcept> concepts, PaymentConcept.Category category) {
		String value = concepts.stream().filter(concept -> concept.getCategory() == category && concept.getDefaultAmountCents() > 0)
				.map(concept -> concept.getName() + ": " + concept.getCurrency() + " "
						+ BigDecimal.valueOf(concept.getDefaultAmountCents(), 2).toPlainString()
						+ (concept.isRecurring() ? " (recurrente)" : ""))
				.collect(java.util.stream.Collectors.joining("; "));
		return value.isBlank() ? null : value;
	}

	private String formatTuitionPrices(List<PaymentConcept> concepts, List<BillingPlanRule> rules,
			Map<UUID, String> levelNames, Map<UUID, String> gradeNames) {
		List<PaymentConcept> tuitionConcepts = concepts.stream()
				.filter(concept -> concept.getCategory() == PaymentConcept.Category.TUITION)
				.toList();
		if (tuitionConcepts.isEmpty()) return null;

		List<String> prices = new ArrayList<>();
		for (PaymentConcept concept : tuitionConcepts) {
			List<BillingPlanRule> conceptRules = rules.stream()
					.filter(rule -> rule.getConceptId().equals(concept.getId()))
					.toList();
			if (conceptRules.isEmpty()) {
				if (concept.getDefaultAmountCents() > 0) prices.add(formatAmount(concept.getName(),
						concept.getCurrency(), concept.getDefaultAmountCents()));
				continue;
			}
			for (BillingPlanRule rule : conceptRules) {
				String scope = billingScopeName(rule, levelNames, gradeNames);
				prices.add(formatAmount(scope + " · " + concept.getName(), rule.getCurrency(), rule.getAmountCents())
						+ billingPeriodLabel(rule));
			}
		}
		return prices.isEmpty() ? null : String.join("; ", prices);
	}

	private String formatTuitionPaymentDates(List<PaymentConcept> concepts, List<BillingPlanRule> rules,
			Map<UUID, String> levelNames, Map<UUID, String> gradeNames) {
		java.util.Set<UUID> tuitionConceptIds = concepts.stream()
				.filter(concept -> concept.getCategory() == PaymentConcept.Category.TUITION)
				.map(PaymentConcept::getId)
				.collect(java.util.stream.Collectors.toSet());
		String value = rules.stream()
				.filter(rule -> tuitionConceptIds.contains(rule.getConceptId()))
				.map(rule -> billingScopeName(rule, levelNames, gradeNames) + ": " + billingDueDateLabel(rule))
				.distinct()
				.collect(java.util.stream.Collectors.joining("; "));
		return value.isBlank() ? null : value;
	}

	private String formatAmount(String label, String currency, long amountCents) {
		return label + ": " + currency + " " + BigDecimal.valueOf(amountCents, 2).toPlainString();
	}

	private String billingScopeName(BillingPlanRule rule, Map<UUID, String> levelNames, Map<UUID, String> gradeNames) {
		if (rule.getGradeId() != null) {
			return gradeNames.getOrDefault(rule.getGradeId(), "Grado configurado");
		}
		if (rule.getLevelId() != null) {
			return levelNames.getOrDefault(rule.getLevelId(), "Nivel configurado");
		}
		return "Todos los niveles";
	}

	private String billingPeriodLabel(BillingPlanRule rule) {
		return rule.getBillingPeriod() == Subscription.BillingPeriod.ANNUAL ? " (anual)" : " (mensual)";
	}

	private String billingDueDateLabel(BillingPlanRule rule) {
		String day = "día " + rule.getBillingDayOfMonth();
		if (rule.getBillingPeriod() == Subscription.BillingPeriod.ANNUAL && rule.getBillingMonth() != null) {
			return day + " de " + monthName(rule.getBillingMonth());
		}
		return day + " de cada mes";
	}

	private String monthName(int month) {
		return switch (month) {
			case 1 -> "enero";
			case 2 -> "febrero";
			case 3 -> "marzo";
			case 4 -> "abril";
			case 5 -> "mayo";
			case 6 -> "junio";
			case 7 -> "julio";
			case 8 -> "agosto";
			case 9 -> "septiembre";
			case 10 -> "octubre";
			case 11 -> "noviembre";
			case 12 -> "diciembre";
			default -> "mes " + month;
		};
	}

	private String buildTuitionHistory(AcademicYear currentYear, Map<String, DisclosureField> currentFields,
			String currentTuition, String currentEnrollment) {
		List<HistoricalYearValue> history = new ArrayList<>();
		String currentAdmissionFee = fieldValue(currentFields, "admission_fee");
		String currentRow = historyRow(currentEnrollment, currentTuition, currentAdmissionFee);
		if (!currentRow.isBlank()) history.add(new HistoricalYearValue(currentYear.getName(), currentYear.getStartDate(), currentRow));

		List<AnnualDisclosurePublication> publications = publicationRepository.findAllByOrderByPublishedAtDesc();
		Map<UUID, AnnualDisclosurePackage> packagesById = packageRepository.findAllByIdIn(publications.stream()
				.map(AnnualDisclosurePublication::getPackageId).distinct().toList()).stream()
				.collect(java.util.stream.Collectors.toMap(AnnualDisclosurePackage::getId, pkg -> pkg));
		java.util.Set<UUID> includedYearIds = new java.util.HashSet<>();
		includedYearIds.add(currentYear.getId());
		for (AnnualDisclosurePublication publication : publications) {
			AnnualDisclosurePackage pkg = packagesById.get(publication.getPackageId());
			if (pkg == null || !includedYearIds.add(pkg.getAcademicYear().getId())) continue;
			DisclosureContent publishedContent = mapper.toContent(publication.getContent());
			Map<String, DisclosureField> fields = publishedContent.fields() == null ? Map.of() : publishedContent.fields();
			String row = historyRow(fieldValue(fields, "enrollment_fee"), fieldValue(fields, "monthly_tuition"),
					fieldValue(fields, "admission_fee"));
			if (!row.isBlank()) history.add(new HistoricalYearValue(pkg.getAcademicYear().getName(),
					pkg.getAcademicYear().getStartDate(), row));
		}

		String accumulated = history.stream()
				.sorted(java.util.Comparator.comparing(HistoricalYearValue::startDate).reversed())
				.limit(5)
				.map(item -> item.yearName() + ": " + item.value())
				.collect(java.util.stream.Collectors.joining("; "));
		if (!accumulated.isBlank()) return accumulated;
		return "Se acumulará automáticamente con cada expediente anual publicado.";
	}

	private String fieldValue(Map<String, DisclosureField> fields, String key) {
		DisclosureField field = fields == null ? null : fields.get(key);
		return field == null || isBlank(field.value()) ? null : field.value().trim();
	}

	private String historyRow(String enrollment, String tuition, String admissionFee) {
		List<String> values = new ArrayList<>();
		if (!isBlank(enrollment)) values.add("Matrícula — " + enrollment);
		if (!isBlank(tuition)) values.add("Pensiones — " + tuition);
		if (!isBlank(admissionFee)) values.add("Cuota de ingreso — " + admissionFee);
		return String.join("; ", values);
	}

	private SystemFieldValue paymentConceptField(String value, String label) {
		if (value != null) {
			return new SystemFieldValue(value, "Conceptos activos de " + label + " en Pagos", DisclosureFieldState.COMPLETED);
		}
		return new SystemFieldValue(null, "Sin conceptos activos con monto en Pagos · agrega un concepto de " + label,
				DisclosureFieldState.PENDING);
	}

	private DisclosureContent copyForNewYear(DisclosureContent source, String sourceYearName) {
		Map<String, DisclosureField> fields = new LinkedHashMap<>();
		var familyVisibleFiles = source.attachments() == null ? java.util.Set.<UUID>of()
				: source.attachments().stream().filter(attachment -> Boolean.TRUE.equals(attachment.visibleToFamilies()))
					.map(DisclosureAttachment::filePublicUuid).collect(java.util.stream.Collectors.toSet());
		if (source.fields() != null) {
			source.fields().forEach((key, field) -> {
				if (field == null) return;
				boolean documentOnly = field.documentOnly() && field.sources().stream()
						.anyMatch(reference -> familyVisibleFiles.contains(reference.filePublicUuid()));
				DisclosureFieldState state = field.documentOnly() && !documentOnly && isBlank(field.value())
						? DisclosureFieldState.PENDING : field.state();
				fields.put(key, new DisclosureField(state, field.value(), field.note(), field.sources(),
						documentOnly, false, "Copiado del año " + sourceYearName));
			});
		}
		return new DisclosureContent(fields, source.attachments() == null ? List.of() : source.attachments(),
				null, null, source.additionalInformation());
	}

	private boolean isEmptyContent(DisclosureContent content) {
		return (content.fields() == null || content.fields().values().stream().allMatch(field -> field == null
				|| field.systemManaged() || (field.state() == DisclosureFieldState.PENDING && isBlank(field.value())
					&& field.sources().isEmpty())))
				&& (content.attachments() == null || content.attachments().isEmpty())
				&& isBlank(content.additionalInformation())
				&& content.enrollmentDisclosureDeadline() == null && content.yearCloseDisclosureDeadline() == null;
	}

	private record SystemFieldValue(String value, String source, DisclosureFieldState state) {
		private SystemFieldValue(String value, String source) {
			this(value, source, DisclosureFieldState.COMPLETED);
		}
	}

	private record HistoricalYearValue(String yearName, LocalDate startDate, String value) { }

	private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
