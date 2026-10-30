package com.edushift.modules.payments.entity;

import com.edushift.modules.payments.entity.Subscription.BillingPeriod;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tenant-owned rule that assigns a recurring plan to students in an academic scope. */
@Entity
@Table(name = "billing_plan_rules", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE edushift.billing_plan_rules SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class BillingPlanRule extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "concept_id", nullable = false)
	private UUID conceptId;

	@Column(name = "level_id")
	private UUID levelId;

	@Column(name = "grade_id")
	private UUID gradeId;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "PEN";

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_period", nullable = false, length = 10)
	private BillingPeriod billingPeriod;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "billing_day_of_month", nullable = false)
	private int billingDayOfMonth;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "billing_month")
	private Integer billingMonth;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}
