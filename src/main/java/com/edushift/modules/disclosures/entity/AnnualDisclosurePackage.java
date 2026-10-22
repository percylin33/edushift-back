package com.edushift.modules.disclosures.entity;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/** Mutable workspace for one tenant and academic year; published content lives in separate snapshots. */
@Entity
@Table(name = "annual_disclosure_packages", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "status", "revisionNumber"})
@SQLDelete(sql = "UPDATE edushift.annual_disclosure_packages "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class AnnualDisclosurePackage extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(name = "fk_disclosure_package_academic_year"))
	private AcademicYear academicYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DisclosureStatus status = DisclosureStatus.DRAFT;

	@Column(name = "revision_number", nullable = false)
	private int revisionNumber = 1;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", nullable = false, columnDefinition = "jsonb")
	private java.util.Map<String, Object> content = new HashMap<>();

	@Column(name = "review_note", length = 2000)
	private String reviewNote;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "submitted_by", columnDefinition = "uuid")
	private UUID submittedBy;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "approved_by", columnDefinition = "uuid")
	private UUID approvedBy;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "published_by", columnDefinition = "uuid")
	private UUID publishedBy;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (status == null) status = DisclosureStatus.DRAFT;
		if (revisionNumber < 1) revisionNumber = 1;
		if (content == null) content = new HashMap<>();
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		deletedAt = null;
	}
}
