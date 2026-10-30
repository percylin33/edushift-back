package com.edushift.modules.sessions.learning.entity;

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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Printable worksheet for one {@link LearningSession} and one grade.
 */
@Entity
@Table(
		name = "learning_session_application_sheets",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_ls_application_sheets_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_ls_application_sheets_tenant_session",
						columnList = "tenant_id, learning_session_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "kind", "grade", "title"})
@SQLDelete(sql = "UPDATE edushift.learning_session_application_sheets "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class ApplicationSheet extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "learning_session_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_ls_application_sheets_session"))
	private LearningSession learningSession;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 16)
	private ApplicationSheetKind kind;

	@Column(name = "grade", nullable = false, length = 80)
	private String grade;

	@Column(name = "sheet_number", nullable = false)
	private Integer sheetNumber;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", nullable = false, columnDefinition = "jsonb")
	private ApplicationSheetContent content;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
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
