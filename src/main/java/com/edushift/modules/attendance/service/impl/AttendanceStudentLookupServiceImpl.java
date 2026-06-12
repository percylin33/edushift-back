package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.attendance.dto.AttendanceStudentLookupItem;
import com.edushift.modules.attendance.service.AttendanceStudentLookupService;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AttendanceStudentLookupService} (BE-6.8).
 *
 * <p>Built on raw JPQL with constructor projection because the
 * returned shape includes joined-and-flattened fields
 * ({@code sectionName}, {@code gradeName}, {@code levelName}) that
 * are awkward to express with Spring Data {@code @Query} return types
 * and would otherwise force us to load the parent entities.
 *
 * <h3>Multi-tenant</h3>
 * Both {@code Student} and {@code StudentEnrollment} extend
 * {@code TenantAwareEntity}, so Hibernate's {@code @TenantId} filter
 * is applied transparently — the JPQL below carries no explicit
 * {@code tenant_id} predicate but is still tenant-scoped at the SQL
 * layer.
 *
 * <h3>Hard size cap</h3>
 * The auxiliary at the entrance shouldn't need more than ~50 hits per
 * page; we clamp {@code pageable.size} to {@link #MAX_PAGE_SIZE} to
 * keep the endpoint cheap and prevent accidental over-fetch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceStudentLookupServiceImpl implements AttendanceStudentLookupService {

	private static final int MAX_PAGE_SIZE = 50;

	private final EntityManager entityManager;

	@Override
	@Transactional(readOnly = true)
	public Page<AttendanceStudentLookupItem> lookup(Filter filter, Pageable pageable) {
		Filter f = filter == null ? Filter.empty() : filter;
		int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
		int page = Math.max(pageable.getPageNumber(), 0);

		String needle = (f.q() != null && !f.q().isBlank())
				? "%" + f.q().trim().toLowerCase() + "%" : null;

		StringBuilder where = new StringBuilder();
		where.append(" where e.status = :active ");
		if (needle != null) {
			where.append(" and (lower(s.firstName) like :q ")
				 .append("      or lower(s.lastName) like :q ")
				 .append("      or lower(s.documentNumber) like :q) ");
		}
		if (f.sectionPublicUuid() != null) {
			where.append(" and sec.publicUuid = :sectionUuid ");
		}
		if (f.gradePublicUuid() != null) {
			where.append(" and g.publicUuid = :gradeUuid ");
		}
		if (f.levelPublicUuid() != null) {
			where.append(" and lv.publicUuid = :levelUuid ");
		}

		// We don't synthesise fullName at the SQL layer because some JPA
		// providers struggle with constructor-expression type inference
		// on concat() in a nested SELECT. We build it from firstName +
		// lastName in Java below — both are non-null in the schema.
		String selectJpql = """
				select new com.edushift.modules.attendance.dto.AttendanceStudentLookupItem(
				    s.publicUuid,
				    s.firstName,
				    s.lastName,
				    s.firstName,
				    s.documentNumber,
				    sec.publicUuid,
				    sec.name,
				    g.name,
				    lv.name)
				from com.edushift.modules.students.enrollments.entity.StudentEnrollment e
				join e.student s
				join e.section sec
				join sec.grade g
				join g.level lv
				""" + where + " order by s.lastName asc, s.firstName asc";

		String countJpql = """
				select count(s.id)
				from com.edushift.modules.students.enrollments.entity.StudentEnrollment e
				join e.student s
				join e.section sec
				join sec.grade g
				join g.level lv
				""" + where;

		TypedQuery<AttendanceStudentLookupItem> dataQuery = entityManager.createQuery(
				selectJpql, AttendanceStudentLookupItem.class);
		TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);

		bindParams(dataQuery, needle, f);
		bindParams(countQuery, needle, f);

		dataQuery.setFirstResult(page * size);
		dataQuery.setMaxResults(size);

		List<AttendanceStudentLookupItem> raw = dataQuery.getResultList();
		long total = countQuery.getSingleResult();

		// Compose fullName in Java so we don't depend on the JPA
		// provider's concat() return-type inference inside a
		// constructor expression.
		List<AttendanceStudentLookupItem> items = new ArrayList<>(raw.size());
		for (AttendanceStudentLookupItem it : raw) {
			String first = it.firstName() == null ? "" : it.firstName();
			String last = it.lastName() == null ? "" : it.lastName();
			String full = (first + " " + last).trim();
			items.add(new AttendanceStudentLookupItem(
					it.studentPublicUuid(),
					it.firstName(),
					it.lastName(),
					full,
					it.documentNumber(),
					it.sectionPublicUuid(),
					it.sectionName(),
					it.gradeName(),
					it.levelName()));
		}

		log.debug("[attendance-lookup] hits={} totalElements={} filters={} page={}/size={}",
				items.size(), total, f, page, size);

		return new PageImpl<>(items, pageable, total);
	}

	private static void bindParams(TypedQuery<?> q, String needle, Filter f) {
		q.setParameter("active", StudentEnrollmentStatus.ACTIVE);
		if (needle != null) {
			q.setParameter("q", needle);
		}
		if (f.sectionPublicUuid() != null) {
			q.setParameter("sectionUuid", f.sectionPublicUuid());
		}
		if (f.gradePublicUuid() != null) {
			q.setParameter("gradeUuid", f.gradePublicUuid());
		}
		if (f.levelPublicUuid() != null) {
			q.setParameter("levelUuid", f.levelPublicUuid());
		}
	}
}
