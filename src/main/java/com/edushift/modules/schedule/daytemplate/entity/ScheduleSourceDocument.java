package com.edushift.modules.schedule.daytemplate.entity;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Prior-year schedule file used to bootstrap day templates / slots
 * (V103 / ADR-SCH-10).
 */
@Entity
@Table(
		name = "schedule_source_documents",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_schedule_source_documents_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_schedule_source_documents_year",
						columnList = "tenant_id, academic_year_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "kind", "parseStatus", "originalFilename"})
@SQLDelete(sql = "UPDATE edushift.schedule_source_documents "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class ScheduleSourceDocument extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_schedule_source_documents_year"))
	private AcademicYear academicYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 32)
	private ScheduleSourceKind kind;

	@Enumerated(EnumType.STRING)
	@Column(name = "parse_status", nullable = false, length = 32)
	private ScheduleParseStatus parseStatus = ScheduleParseStatus.UPLOADED;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", length = 120)
	private String contentType;

	@Column(name = "storage_key", nullable = false, length = 500)
	private String storageKey;

	@Column(name = "file_size_bytes")
	private Long fileSizeBytes;

	@Column(name = "parsed_draft_json", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> parsedDraftJson = new HashMap<>();

	@Column(name = "parse_error", length = 500)
	private String parseError;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (parseStatus == null) {
			parseStatus = ScheduleParseStatus.UPLOADED;
		}
		if (parsedDraftJson == null) {
			parsedDraftJson = new HashMap<>();
		}
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}
}
