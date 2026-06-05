package com.edushift.modules.students.mapper;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper from {@link BulkImportJob} to
 * {@link BulkImportJobResponse}.
 *
 * <p>The errors list is exposed as a defensive copy so consumers cannot
 * accidentally mutate the entity's mutable jsonb-backed list and have
 * Hibernate flush stale state on the next transaction.
 */
@Component
public class BulkImportJobMapper {

	public BulkImportJobResponse toResponse(BulkImportJob entity) {
		return new BulkImportJobResponse(
				entity.getPublicUuid(),
				entity.getJobType(),
				entity.getStatus(),
				entity.getFileName(),
				entity.getFileSizeBytes(),
				entity.getTotalRows(),
				entity.getProcessedRows(),
				entity.getErrorRows(),
				java.util.List.copyOf(entity.getErrorsView()),
				entity.getFailReason(),
				entity.getStartedAt(),
				entity.getFinishedAt(),
				entity.getCreatedAt()
		);
	}
}
