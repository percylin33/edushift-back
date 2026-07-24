package com.edushift.modules.academic.classrooms.service;

import com.edushift.modules.academic.classrooms.dto.ClassroomRequest;
import com.edushift.modules.academic.classrooms.dto.ClassroomResponse;
import com.edushift.modules.academic.classrooms.entity.Classroom;
import com.edushift.modules.academic.classrooms.exception.ClassroomNotFoundException;
import com.edushift.modules.academic.classrooms.repository.ClassroomRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped CRUD for {@link Classroom} (Sprint cierre-C / B4).
 *
 * <p>Multi-tenant safety: all reads/writes go through the tenant-scoped
 * repository. Cross-tenant lookups by {@code publicUuid} return
 * {@link ClassroomNotFoundException} (404, anti-enumeration). The
 * partial UNIQUE index {@code uk_classrooms_tenant_code} (V86) keeps
 * the (tenant_id, code) uniqueness contract.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

	private final ClassroomRepository repository;

	@Transactional(readOnly = true)
	public Page<ClassroomResponse> list(Pageable pageable) {
		TenantContext.currentRequired();
		return repository
				.findByDeletedFalseOrderByCodeAsc(pageable)
				.map(ClassroomResponse::from);
	}

	@Transactional(readOnly = true)
	public ClassroomResponse get(UUID publicUuid) {
		return ClassroomResponse.from(mustFind(publicUuid));
	}

	@Transactional
	public ClassroomResponse create(ClassroomRequest req) {
		TenantContext.currentRequired();
		if (repository.findByCodeAndDeletedFalse(req.code()).isPresent()) {
			throw new BusinessException(
					"CLASSROOM_CODE_EXISTS",
					"A classroom with code '" + req.code() + "' already exists in this tenant");
		}
		Classroom c = new Classroom();
		applyRequest(c, req);
		try {
			c = repository.saveAndFlush(c);
		}
		catch (DataIntegrityViolationException dup) {
			log.warn("Concurrent insert caught for classroom code {}", req.code());
			throw new BusinessException(
					"CLASSROOM_CODE_EXISTS",
					"A classroom with code '" + req.code() + "' already exists in this tenant");
		}
		log.info("[classroom] created publicUuid={} code={}", c.getPublicUuid(), c.getCode());
		return ClassroomResponse.from(c);
	}

	@Transactional
	public ClassroomResponse update(UUID publicUuid, ClassroomRequest req) {
		Classroom c = mustFind(publicUuid);
		// Code uniqueness check (allow same code on same row).
		repository.findByCodeAndDeletedFalse(req.code())
				.filter(other -> !other.getPublicUuid().equals(publicUuid))
				.ifPresent(other -> {
					throw new BusinessException(
							"CLASSROOM_CODE_EXISTS",
							"A classroom with code '" + req.code() + "' already exists in this tenant");
				});
		applyRequest(c, req);
		c = repository.save(c);
		log.info("[classroom] updated publicUuid={}", c.getPublicUuid());
		return ClassroomResponse.from(c);
	}

	@Transactional
	public void softDelete(UUID publicUuid) {
		Classroom c = mustFind(publicUuid);
		c.markDeleted();
		repository.save(c);
		log.info("[classroom] soft-deleted publicUuid={}", c.getPublicUuid());
	}

	/**
	 * Resolve a classroom by publicUuid, including soft-deleted rows.
	 * Useful for {@link com.edushift.modules.schedule.timeslot.service.TimeSlotService}
	 * which must accept classrooms that were created pre-deletion in
	 * historical schedule snapshots.
	 */
	@Transactional(readOnly = true)
	public Classroom resolveRequired(UUID publicUuid) {
		return repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ClassroomNotFoundException(
						"Classroom not found: " + publicUuid));
	}

	private Classroom mustFind(UUID publicUuid) {
		return repository.findByPublicUuidAndDeletedFalse(publicUuid)
				.orElseThrow(() -> new ClassroomNotFoundException(
						"Classroom not found: " + publicUuid));
	}

	private static void applyRequest(Classroom c, ClassroomRequest r) {
		c.setCode(r.code());
		c.setName(r.name());
		c.setType(r.type());
		c.setCapacity(r.capacity());
		c.setLocation(r.location());
		c.setDescription(r.description());
	}
}