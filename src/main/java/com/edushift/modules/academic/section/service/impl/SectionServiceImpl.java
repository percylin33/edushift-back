package com.edushift.modules.academic.section.service.impl;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.mapper.SectionMapper;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of
 * {@link com.edushift.modules.academic.section.service.SectionService}.
 *
 * <h3>Defense in depth on tenant scoping</h3>
 * Hibernate's {@code @TenantId} discriminator already filters every
 * read by tenant. We still run an explicit triple-check
 * ({@link #assertSameTenantContext}) on the parent year and grade
 * because:
 * <ul>
 *   <li>misconfigured native queries could bypass the discriminator;</li>
 *   <li>cross-tenant FKs are theoretically allowed by the DB schema;</li>
 *   <li>a cheap log line on any cross-tenant attempt is worth the
 *       diagnostic value.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements
		com.edushift.modules.academic.section.service.SectionService {

	private final SectionRepository sectionRepository;
	private final AcademicYearRepository yearRepository;
	private final GradeRepository gradeRepository;
	private final AcademicLevelRepository levelRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final SectionMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<SectionListItem> listSections(
			UUID academicYearPublicUuid,
			UUID gradePublicUuid,
			UUID levelPublicUuid) {

		// Step 1 — resolve the year (explicit > active > empty).
		Optional<AcademicYear> yearOpt;
		if (academicYearPublicUuid != null) {
			yearOpt = yearRepository.findByPublicUuid(academicYearPublicUuid);
			if (yearOpt.isEmpty()) {
				throw new ResourceNotFoundException("AcademicYear", academicYearPublicUuid);
			}
		}
		else {
			yearOpt = yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE);
		}

		if (yearOpt.isEmpty()) {
			// No explicit year + no ACTIVE year. Brand-new tenant before
			// BE-4.1 activate; respond with empty list rather than 404.
			return List.of();
		}

		AcademicYear year = yearOpt.get();

		// Step 2 — narrow further by grade > level if asked.
		List<Section> sections;
		if (gradePublicUuid != null) {
			Grade grade = gradeRepository.findByPublicUuid(gradePublicUuid)
					.orElseThrow(() -> new ResourceNotFoundException("Grade", gradePublicUuid));
			sections = sectionRepository
					.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade);
		}
		else if (levelPublicUuid != null) {
			AcademicLevel level = levelRepository.findByPublicUuid(levelPublicUuid)
					.orElseThrow(() -> new ResourceNotFoundException("AcademicLevel", levelPublicUuid));
			sections = sectionRepository.findAllByYearAndLevel(year, level);
		}
		else {
			sections = sectionRepository
					.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(year);
		}

		return sections.stream().map(mapper::toListItem).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public SectionResponse getSection(UUID publicUuid) {
		Section section = loadSection(publicUuid);
		return mapper.toResponse(section);
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public SectionResponse createSection(CreateSectionRequest request) {
		AcademicYear year = yearRepository.findByPublicUuid(request.academicYearPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"AcademicYear", request.academicYearPublicUuid()));
		Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Grade", request.gradePublicUuid()));

		assertSameTenantContext(year, grade);
		assertYearIsEditable(year, "create");
		ensureNameAvailable(year, grade, request.name(), null);

		Section section = mapper.fromCreate(request, year, grade);
		try {
			Section saved = sectionRepository.saveAndFlush(section);
			log.info("[academic.section] created -- publicUuid={} year={} grade={} name={}",
					saved.getPublicUuid(), year.getName(), grade.getName(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Race against uk_sections_year_grade_name_active.
			throw new ConflictException("SECTION_NAME_TAKEN",
					"Another section in (" + year.getName() + ", " + grade.getName()
							+ ") already uses the name '" + request.name() + "'", ex);
		}
	}

	@Override
	@Transactional
	public SectionResponse updateSection(UUID publicUuid, UpdateSectionRequest request) {
		Section section = loadSection(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(section);
		}

		AcademicYear year = section.getAcademicYear();
		Grade grade = section.getGrade();

		assertSameTenantContext(year, grade);
		assertYearIsEditable(year, "update");

		if (request.name() != null && !request.name().trim().equalsIgnoreCase(section.getName())) {
			ensureNameAvailable(year, grade, request.name(), section.getId());
		}

		mapper.applyUpdate(request, section);

		try {
			Section saved = sectionRepository.saveAndFlush(section);
			log.info("[academic.section] updated -- publicUuid={} name={} order={}",
					saved.getPublicUuid(), saved.getName(), saved.getDisplayOrder());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("SECTION_NAME_TAKEN",
					"Another section in (" + year.getName() + ", " + grade.getName()
							+ ") already uses the name '" + request.name() + "'", ex);
		}
	}

	@Override
	@Transactional
	public void deleteSection(UUID publicUuid) {
		Section section = loadSection(publicUuid);

		AcademicYear year = section.getAcademicYear();
		Grade grade = section.getGrade();

		assertSameTenantContext(year, grade);
		assertYearIsEditable(year, "delete");
		// BE-4.8: reject delete when there are ACTIVE enrollments. This
		// preserves the historical roster (transferred / graduated rows
		// remain) while forcing the admin to soft-end the active ones
		// first via POST /v1/enrollments/{uuid}/withdraw.
		if (enrollmentRepository.existsActiveBySection(section)) {
			throw new ConflictException("SECTION_HAS_ENROLLMENTS",
					"Section '" + section.getName() + "' has active student "
							+ "enrollments. Withdraw them first via "
							+ "POST /enrollments/{uuid}/withdraw.");
		}

		sectionRepository.delete(section);
		log.info("[academic.section] deleted -- publicUuid={} year={} grade={} name={}",
				section.getPublicUuid(), year.getName(), grade.getName(), section.getName());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Section loadSection(UUID publicUuid) {
		return sectionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", publicUuid));
	}

	private void ensureNameAvailable(AcademicYear year, Grade grade, String name,
			UUID excludeInternalId) {
		if (name == null) return;
		String normalised = name.trim();
		sectionRepository.findByYearGradeAndNameIgnoreCase(year, grade, normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("SECTION_NAME_TAKEN",
							"Another section in (" + year.getName() + ", " + grade.getName()
									+ ") already uses the name '" + normalised + "'");
				});
	}

	private void assertYearIsEditable(AcademicYear year, String op) {
		if (year.getStatus() == AcademicYearStatus.CLOSED) {
			log.warn("[academic.section] {} refused -- year={} is CLOSED",
					op, year.getPublicUuid());
			throw new ConflictException("ACADEMIC_YEAR_LOCKED",
					"Academic year '" + year.getName()
							+ "' is CLOSED; sections are read-only.");
		}
	}

	private void assertSameTenantContext(AcademicYear year, Grade grade) {
		UUID currentTenant = TenantContext.currentRequired();
		if (!currentTenant.equals(year.getTenantId())) {
			// Should be impossible — Hibernate filters by tenant — but log
			// it loudly if it happens; it means a query bypassed the
			// discriminator (custom JPQL, native query, etc.).
			log.warn("[academic.section] tenant mismatch on year -- current={} year.tenant={}",
					currentTenant, year.getTenantId());
			throw new ResourceNotFoundException("AcademicYear", year.getPublicUuid());
		}
		if (!currentTenant.equals(grade.getTenantId())) {
			log.warn("[academic.section] tenant mismatch on grade -- current={} grade.tenant={}",
					currentTenant, grade.getTenantId());
			throw new ResourceNotFoundException("Grade", grade.getPublicUuid());
		}
	}
}
