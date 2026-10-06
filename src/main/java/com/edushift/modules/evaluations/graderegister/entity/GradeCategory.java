package com.edushift.modules.evaluations.graderegister.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Column group inside a {@link GradeRegisterTemplate} (F1 + planilla slots).
 */
@Entity
@Table(
		name = "grade_categories",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_grade_categories_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_grade_categories_template_order",
						columnList = "tenant_id, template_id, sort_order")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "sortOrder", "weight", "columnMode"})
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE edushift.grade_categories "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class GradeCategory extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "template_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_grade_categories_template"))
	private GradeRegisterTemplate template;

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	@Column(name = "color_token", nullable = false, length = 32)
	private String colorToken = "neutral";

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 1;

	@Column(name = "weight", nullable = false, precision = 6, scale = 2)
	private BigDecimal weight = BigDecimal.ZERO;

	@Column(name = "contributes_to_average", nullable = false)
	private Boolean contributesToAverage = Boolean.TRUE;

	@Column(name = "allows_teacher_columns", nullable = false)
	private Boolean allowsTeacherColumns = Boolean.TRUE;

	@Enumerated(EnumType.STRING)
	@Column(name = "column_mode", nullable = false, length = 16)
	private GradeCategoryColumnMode columnMode = GradeCategoryColumnMode.OPEN;

	@Column(name = "slot_count")
	private Integer slotCount;

	@Column(name = "slot_label_prefix", length = 16)
	private String slotLabelPrefix;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "slot_labels", columnDefinition = "jsonb")
	private List<String> slotLabels;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Resolved evaluation column names for materialisation / preview.
	 * Empty for {@link GradeCategoryColumnMode#OPEN}.
	 */
	public List<String> resolvedSlotLabels() {
		GradeCategoryColumnMode mode = columnMode != null
				? columnMode
				: GradeCategoryColumnMode.OPEN;
		return switch (mode) {
			case OPEN -> List.of();
			case SINGLE -> {
				if (slotLabels != null && !slotLabels.isEmpty()
						&& slotLabels.getFirst() != null
						&& !slotLabels.getFirst().isBlank()) {
					yield List.of(slotLabels.getFirst().trim());
				}
				yield List.of(name != null ? name.trim() : "Nota");
			}
			case SERIES -> {
				if (slotLabels != null && !slotLabels.isEmpty()) {
					List<String> cleaned = new ArrayList<>();
					for (String label : slotLabels) {
						if (label != null && !label.isBlank()) {
							cleaned.add(label.trim());
						}
					}
					if (!cleaned.isEmpty()) {
						yield List.copyOf(cleaned);
					}
				}
				int n = slotCount != null ? Math.max(0, Math.min(slotCount, 40)) : 0;
				String prefix = (slotLabelPrefix != null && !slotLabelPrefix.isBlank())
						? slotLabelPrefix.trim()
						: "S";
				List<String> generated = new ArrayList<>(n);
				for (int i = 1; i <= n; i++) {
					generated.add(prefix + i);
				}
				yield List.copyOf(generated);
			}
		};
	}

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (colorToken == null || colorToken.isBlank()) {
			colorToken = "neutral";
		}
		if (sortOrder == null) {
			sortOrder = 1;
		}
		if (weight == null) {
			weight = BigDecimal.ZERO;
		}
		if (contributesToAverage == null) {
			contributesToAverage = Boolean.TRUE;
		}
		if (allowsTeacherColumns == null) {
			allowsTeacherColumns = Boolean.TRUE;
		}
		if (columnMode == null) {
			columnMode = GradeCategoryColumnMode.OPEN;
		}
		if (slotLabelPrefix == null || slotLabelPrefix.isBlank()) {
			slotLabelPrefix = "S";
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
