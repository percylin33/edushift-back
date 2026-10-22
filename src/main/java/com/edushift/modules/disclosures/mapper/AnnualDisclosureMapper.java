package com.edushift.modules.disclosures.mapper;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.disclosures.dto.DisclosureAttachment;
import com.edushift.modules.disclosures.dto.DisclosureCompleteness;
import com.edushift.modules.disclosures.dto.DisclosureContent;
import com.edushift.modules.disclosures.dto.DisclosureField;
import com.edushift.modules.disclosures.dto.DisclosureFieldDefinition;
import com.edushift.modules.disclosures.dto.DisclosureFieldSource;
import com.edushift.modules.disclosures.dto.DisclosureFieldState;
import com.edushift.modules.disclosures.dto.DisclosurePackageResponse;
import com.edushift.modules.disclosures.dto.DisclosurePublicationResponse;
import com.edushift.modules.disclosures.dto.DisclosureVersionSummary;
import com.edushift.modules.disclosures.entity.AnnualDisclosurePackage;
import com.edushift.modules.disclosures.entity.AnnualDisclosurePublication;
import com.edushift.modules.disclosures.entity.DisclosureStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AnnualDisclosureMapper {
	private final ObjectMapper objectMapper;

	public AnnualDisclosureMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

	public Map<String, Object> toMap(DisclosureContent content) {
		return objectMapper.convertValue(content, new TypeReference<>() { });
	}

	public DisclosureContent toContent(Map<String, Object> value) {
		if (value == null || value.isEmpty()) return DisclosureContent.empty();
		return objectMapper.convertValue(value, DisclosureContent.class);
	}

	public DisclosurePackageResponse toResponse(AnnualDisclosurePackage entity, Integer latestPublishedVersion) {
		DisclosureContent content = toContent(entity.getContent());
		DisclosureCompleteness completeness = completeness(content);
		return new DisclosurePackageResponse(entity.getPublicUuid(), entity.getAcademicYear().getPublicUuid(),
				entity.getAcademicYear().getName(), entity.getStatus(), entity.getRevisionNumber(),
				latestPublishedVersion, content, completeness, entity.getReviewNote(), entity.getSubmittedAt(),
				entity.getSubmittedBy(), entity.getApprovedAt(), entity.getApprovedBy(), entity.getPublishedAt(),
				entity.getPublishedBy());
	}

	public DisclosurePackageResponse emptyResponse(AcademicYear year) {
		DisclosureContent content = DisclosureContent.empty();
		return new DisclosurePackageResponse(null, year.getPublicUuid(), year.getName(), DisclosureStatus.DRAFT,
				1, null, content, completeness(content), null, null, null, null, null, null, null);
	}

	public DisclosurePublicationResponse toPublicationResponse(
			AnnualDisclosurePublication publication, UUID academicYearPublicUuid, String yearName) {
		return new DisclosurePublicationResponse(publication.getPublicUuid(), academicYearPublicUuid, yearName,
				publication.getVersionNumber(), toContent(publication.getContent()), publication.getContentSha256(),
				publication.getApprovedAt(), publication.getPublishedAt());
	}

	/** Family responses contain only documents explicitly approved for sharing. */
	public DisclosurePublicationResponse toFamilyPublicationResponse(
			AnnualDisclosurePublication publication, UUID academicYearPublicUuid, String yearName) {
		DisclosureContent content = toContent(publication.getContent());
		List<DisclosureAttachment> attachments = content.attachments() == null ? List.of() : content.attachments();
		Map<String, DisclosureField> fields = content.fields() == null ? Map.of() : content.fields();
		Set<UUID> visibleFiles = attachments.stream()
				.filter(attachment -> Boolean.TRUE.equals(attachment.visibleToFamilies()))
				.map(DisclosureAttachment::filePublicUuid).collect(Collectors.toUnmodifiableSet());
		List<DisclosureAttachment> visibleAttachments = attachments.stream()
				.filter(attachment -> visibleFiles.contains(attachment.filePublicUuid())).toList();
		Map<String, DisclosureField> visibleFields = new LinkedHashMap<>();
		fields.forEach((key, field) -> {
			if ("school_schedule".equals(key) && field.state() == DisclosureFieldState.PENDING) return;
			List<DisclosureFieldSource> visibleSources = field.sources().stream()
					.filter(source -> visibleFiles.contains(source.filePublicUuid())).toList();
			visibleFields.put(key, new DisclosureField(field.state(), field.value(), field.note(), visibleSources,
					field.documentOnly(), false, null));
		});
		DisclosureContent familyContent = new DisclosureContent(visibleFields, visibleAttachments,
				content.enrollmentDisclosureDeadline(), content.yearCloseDisclosureDeadline(), content.additionalInformation());
		return new DisclosurePublicationResponse(publication.getPublicUuid(), academicYearPublicUuid, yearName,
				publication.getVersionNumber(), familyContent, publication.getContentSha256(),
				publication.getApprovedAt(), publication.getPublishedAt());
	}

	public DisclosureVersionSummary toVersionSummary(AnnualDisclosurePublication publication) {
		return new DisclosureVersionSummary(publication.getPublicUuid(), publication.getVersionNumber(),
				publication.getContentSha256(), publication.getPublishedAt());
	}

	public DisclosureCompleteness completeness(DisclosureContent content) {
		Map<String, DisclosureField> fields = content.fields() == null ? Map.of() : content.fields();
		List<DisclosureFieldDefinition> pending = new ArrayList<>();
		int completed = 0;
		for (DisclosureFieldDefinition definition : com.edushift.modules.disclosures.dto.DisclosureCatalog.fields()) {
			if ("school_schedule".equals(definition.key())) continue;
			DisclosureField field = fields.get(definition.key());
			if (field != null && (field.state() == DisclosureFieldState.COMPLETED
					|| field.state() == DisclosureFieldState.NOT_APPLICABLE)) {
				completed++;
			} else {
				pending.add(definition);
			}
		}
		return new DisclosureCompleteness(completed,
				com.edushift.modules.disclosures.dto.DisclosureCatalog.fields().size() - 1,
				List.copyOf(pending));
	}

	public boolean referencesFile(Map<String, Object> rawContent, UUID fileUuid) {
		DisclosureContent content = toContent(rawContent);
		if (content.attachments() == null) return false;
		return content.attachments().stream().map(DisclosureAttachment::filePublicUuid).anyMatch(fileUuid::equals);
	}

	public boolean referencesFamilyVisibleFile(Map<String, Object> rawContent, UUID fileUuid) {
		DisclosureContent content = toContent(rawContent);
		if (content.attachments() == null) return false;
		return content.attachments().stream().anyMatch(attachment -> fileUuid.equals(attachment.filePublicUuid())
				&& Boolean.TRUE.equals(attachment.visibleToFamilies()));
	}
}
