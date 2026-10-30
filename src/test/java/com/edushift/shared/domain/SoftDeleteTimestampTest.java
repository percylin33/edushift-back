package com.edushift.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.classrooms.entity.Classroom;
import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod;
import com.edushift.modules.reports.entity.ReportTemplate;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SoftDeleteTimestampTest {

	@Test
	void classroomDeleteAndRestoreKeepDeletedFlagAndTimestampInSync() {
		assertLifecycle(new Classroom());
	}

	@Test
	void reportTemplateDeleteAndRestoreKeepDeletedFlagAndTimestampInSync() {
		assertLifecycle(new ReportTemplate());
	}

	@Test
	void paymentMethodDeleteAndRestoreKeepDeletedFlagAndTimestampInSync() {
		assertLifecycle(new TenantPaymentMethod());
	}

	private static void assertLifecycle(BaseEntity entity) {
		entity.markDeleted();
		assertThat(entity.isDeleted()).isTrue();
		Instant deletedAt = deletedAt(entity);
		assertThat(deletedAt).isNotNull().isBeforeOrEqualTo(Instant.now());

		entity.restore();
		assertThat(entity.isDeleted()).isFalse();
		assertThat(deletedAt(entity)).isNull();
	}

	private static Instant deletedAt(BaseEntity entity) {
		if (entity instanceof Classroom classroom) return classroom.getDeletedAt();
		if (entity instanceof ReportTemplate reportTemplate) return reportTemplate.getDeletedAt();
		if (entity instanceof TenantPaymentMethod paymentMethod) return paymentMethod.getDeletedAt();
		throw new AssertionError("Unexpected entity type: " + entity.getClass());
	}
}
