package com.edushift.modules.students.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.entity.BulkImportStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkImportJobMapperTest {

	private final BulkImportJobMapper mapper = new BulkImportJobMapper();

	@Test
	@DisplayName("toResponse copies counters, status, and errors verbatim")
	void copiesAllFields() {
		BulkImportJob job = new BulkImportJob();
		job.setPublicUuid(UUID.randomUUID());
		job.setJobType(BulkImportJobType.STUDENTS);
		job.setStatus(BulkImportStatus.COMPLETED);
		job.setFileName("upload.xlsx");
		job.setFileSizeBytes(2048);
		job.setTotalRows(5);
		job.setProcessedRows(5);
		job.recordRowError(3, "ROW_INVALID", "missing firstName");

		BulkImportJobResponse response = mapper.toResponse(job);

		assertThat(response.publicUuid()).isEqualTo(job.getPublicUuid());
		assertThat(response.jobType()).isEqualTo(BulkImportJobType.STUDENTS);
		assertThat(response.status()).isEqualTo(BulkImportStatus.COMPLETED);
		assertThat(response.fileName()).isEqualTo("upload.xlsx");
		assertThat(response.fileSizeBytes()).isEqualTo(2048);
		assertThat(response.totalRows()).isEqualTo(5);
		assertThat(response.processedRows()).isEqualTo(5);
		assertThat(response.errorRows()).isEqualTo(1);
		assertThat(response.errors()).hasSize(1);
		assertThat(response.errors().get(0).row()).isEqualTo(3);
		assertThat(response.errors().get(0).code()).isEqualTo("ROW_INVALID");
	}

	@Test
	@DisplayName("errors list is a defensive copy — mutating the entity post-mapping doesn't change the DTO")
	void errorsListIsDefensiveCopy() {
		BulkImportJob job = new BulkImportJob();
		job.setPublicUuid(UUID.randomUUID());
		job.setJobType(BulkImportJobType.STUDENTS);
		job.setFileName("u.xlsx");
		job.setFileSizeBytes(1);

		BulkImportJobResponse before = mapper.toResponse(job);
		assertThat(before.errors()).isEmpty();

		job.recordRowError(7, "ROW_INVALID", "added later");

		assertThat(before.errors()).isEmpty(); // snapshot at mapping time
	}
}
