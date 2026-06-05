package com.edushift.modules.students.service;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListFilters;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Tenant-scoped CRUD over the {@code students} aggregate.
 *
 * <p>Authorization is enforced at the controller level
 * ({@code @PreAuthorize("hasRole('TENANT_ADMIN')")}) for Sprint 3.
 * Permission-based gates ({@code STUDENTS:CREATE/READ/UPDATE/DELETE})
 * are explicit deferred technical debt; see {@code SPRINT-03} doc.
 *
 * <h3>Uniqueness guardrails</h3>
 * <ul>
 *   <li>{@code (documentType, documentNumber)} is unique per tenant on
 *       non-deleted rows. The service runs a pre-check before
 *       create/update so the API surface returns a clean
 *       {@code 409 STUDENT_DOCUMENT_TAKEN} instead of a generic
 *       constraint violation.</li>
 *   <li>{@code email}, when present, is unique per tenant. Same
 *       pre-check pattern; surfaces as {@code 409 STUDENT_EMAIL_TAKEN}.</li>
 * </ul>
 */
public interface StudentService {

	/** Paginated list with optional filters. */
	Page<StudentListItem> listStudents(StudentListFilters filters, Pageable pageable);

	/** Resolves a single student by public UUID, or {@code 404}. */
	StudentResponse getStudent(UUID publicUuid);

	/** Creates a brand-new student in the current tenant. */
	StudentResponse createStudent(CreateStudentRequest request);

	/** Applies a partial patch and returns the post-merge snapshot. */
	StudentResponse updateStudent(UUID publicUuid, UpdateStudentRequest request);

	/** Soft-deletes a student. Idempotent — already-deleted students surface as 404. */
	void deleteStudent(UUID publicUuid);

}
