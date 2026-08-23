package com.edushift.modules.family.service.impl;

import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.family.dto.FamilyActivityItemDto;
import com.edushift.modules.family.dto.FamilyAttendanceRecordDto;
import com.edushift.modules.family.dto.FamilyChildSummary;
import com.edushift.modules.family.dto.FamilyGradeItemDto;
import com.edushift.modules.family.dto.FamilyPaymentItemDto;
import com.edushift.modules.family.service.FamilyService;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.entity.TaskStatus;
import com.edushift.modules.tasks.repository.TaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentGuardianService studentGuardianService;
	private final AttendanceRecordRepository attendanceRecordRepository;
	private final GradeRecordRepository gradeRecordRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final TaskRepository taskRepository;
	private final QuizRepository quizRepository;
	private final InvoiceRepository invoiceRepository;
	private final TimeSlotService timeSlotService;

	@Override
	@Transactional(readOnly = true)
	public List<FamilyChildSummary> listChildren(UUID parentUserPublicUuid) {
		if (parentUserPublicUuid == null) return List.of();
		return studentGuardianRepository.findActiveByGuardianUserId(parentUserPublicUuid).stream()
				.map(StudentGuardian::getStudent)
				.map(student -> new FamilyChildSummary(
						student.getPublicUuid(), student.getFirstName(), student.getLastName(),
						student.fullName(), student.getEnrollmentStatus()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<FamilyAttendanceRecordDto> getChildAttendance(
			UUID studentPublicUuid, UUID parentUserPublicUuid, Pageable pageable) {
		Student student = loadLinkedStudent(studentPublicUuid, parentUserPublicUuid);
		List<FamilyAttendanceRecordDto> items = attendanceRecordRepository
				.findByStudentInRange(student, null, null)
				.stream()
				.map(this::toFamilyAttendanceRecord)
				.toList();
		return slice(items, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FamilyGradeItemDto> getChildGrades(UUID studentPublicUuid, UUID parentUserPublicUuid) {
		loadLinkedStudent(studentPublicUuid, parentUserPublicUuid);
		return gradeRecordRepository.findAllByStudentPublicUuid(studentPublicUuid).stream()
				.map(this::toFamilyGradeItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<FamilyActivityItemDto> getChildActivities(
			UUID studentPublicUuid, UUID parentUserPublicUuid) {
		Student student = loadLinkedStudent(studentPublicUuid, parentUserPublicUuid);
		List<StudentEnrollment> enrollments = enrollmentRepository.findActiveByStudentFetchSection(student);
		List<FamilyActivityItemDto> items = new ArrayList<>();
		Pageable top = PageRequest.of(0, 1000);
		Instant now = Instant.now();
		for (StudentEnrollment enrollment : enrollments) {
			var section = enrollment.getSection();
			if (section == null) {
				continue;
			}
			String sectionName = section.getName();
			for (Task task : taskRepository.findAllBySectionOrderByDueAtDesc(section, top).getContent()) {
				if (task.getStatus() == TaskStatus.DRAFT) {
					continue;
				}
				boolean overdue = task.getDueAt() != null && task.getDueAt().isBefore(now);
				items.add(new FamilyActivityItemDto(
						"TASK",
						task.getPublicUuid(),
						task.getTitle(),
						sectionName,
						task.getDueAt(),
						overdue,
						task.getStatus() != null ? task.getStatus().name() : null));
			}
			for (Quiz quiz : quizRepository.findAllBySectionOrderByDueAtDesc(section, top).getContent()) {
				if (quiz.getStatus() == QuizStatus.DRAFT) {
					continue;
				}
				boolean overdue = quiz.getDueAt() != null && quiz.getDueAt().isBefore(now);
				items.add(new FamilyActivityItemDto(
						"QUIZ",
						quiz.getPublicUuid(),
						quiz.getTitle(),
						sectionName,
						quiz.getDueAt(),
						overdue,
						quiz.getStatus() != null ? quiz.getStatus().name() : null));
			}
		}
		items.sort(Comparator.comparing(
				FamilyActivityItemDto::dueAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return items;
	}

	@Override
	@Transactional(readOnly = true)
	public List<FamilyPaymentItemDto> getChildPayments(
			UUID studentPublicUuid, UUID parentUserPublicUuid) {
		Student student = loadLinkedStudent(studentPublicUuid, parentUserPublicUuid);
		return invoiceRepository
				.findByStudentIdOrderByIssuedAtDesc(student.getPublicUuid(), Pageable.unpaged())
				.getContent()
				.stream()
				.map(this::toFamilyPayment)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ScheduleWeekView getChildSchedule(
			UUID studentPublicUuid, UUID parentUserPublicUuid, UUID periodUuid) {
		Student student = loadLinkedStudent(studentPublicUuid, parentUserPublicUuid);
		return timeSlotService.getScheduleWeekForStudent(student, periodUuid);
	}

	private Student loadLinkedStudent(UUID studentPublicUuid, UUID parentUserPublicUuid) {
		studentGuardianService.assertParentLinkedToStudent(parentUserPublicUuid, studentPublicUuid);
		return studentRepository.findByPublicUuid(studentPublicUuid)
				.orElseThrow(() -> {
					log.warn("[family] linked child vanished after guardian-check -- studentPublicUuid={}",
							studentPublicUuid);
					return new IllegalStateException("Linked student must exist");
				});
	}

	private FamilyAttendanceRecordDto toFamilyAttendanceRecord(AttendanceRecord record) {
		return new FamilyAttendanceRecordDto(
				record.getPublicUuid(),
				record.getSession() != null ? record.getSession().getPublicUuid() : null,
				record.getStudent() != null ? record.getStudent().getPublicUuid() : null,
				record.getStudent() != null ? record.getStudent().fullName() : null,
				record.getStatus(),
				record.getOccurredAt(),
				record.getNotes(),
				record.getJustificationStatus(),
				record.getJustificationText(),
				record.getApprovedAt());
	}

	private FamilyGradeItemDto toFamilyGradeItem(GradeRecord record) {
		return new FamilyGradeItemDto(
				record.getPublicUuid(),
				record.getEvaluation() != null ? record.getEvaluation().getPublicUuid() : null,
				record.getEvaluation() != null ? record.getEvaluation().getName() : null,
				sectionName(record),
				courseName(record),
				record.getScore(),
				maxScore(record),
				record.getLiteral(),
				record.getComments(),
				scheduledDate(record),
				record.getRecordedAt());
	}

	private FamilyPaymentItemDto toFamilyPayment(Invoice invoice) {
		return new FamilyPaymentItemDto(
				invoice.getPublicUuid(),
				invoice.getPeriodLabel(),
				invoice.getCurrency(),
				invoice.getTotalCents(),
				invoice.getStatus(),
				invoice.getIssuedAt(),
				invoice.getDueAt(),
				invoice.getPaidAt());
	}

	private Page<FamilyAttendanceRecordDto> slice(
			List<FamilyAttendanceRecordDto> items, Pageable pageable) {
		if (pageable.isUnpaged()) {
			return new PageImpl<>(items);
		}
		int start = (int) pageable.getOffset();
		if (start >= items.size()) {
			return new PageImpl<>(List.of(), pageable, items.size());
		}
		int end = Math.min(start + pageable.getPageSize(), items.size());
		return new PageImpl<>(items.subList(start, end), pageable, items.size());
	}

	private static Instant scheduledDate(GradeRecord record) {
		if (record.getEvaluation() == null || record.getEvaluation().getScheduledDate() == null) {
			return record.getRecordedAt();
		}
		return record.getEvaluation().getScheduledDate()
				.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
	}

	private static BigDecimal maxScore(GradeRecord record) {
		if (record.getEvaluation() == null) return null;
		return switch (record.getEvaluation().getScale()) {
			case SCORE_0_20 -> BigDecimal.valueOf(20);
			case LITERAL_AD, LITERAL_NA, LITERAL_A_B_C_D -> null;
		};
	}

	private static String sectionName(GradeRecord record) {
		if (record.getEvaluation() == null
				|| record.getEvaluation().getTeacherAssignment() == null
				|| record.getEvaluation().getTeacherAssignment().getSection() == null) {
			return null;
		}
		return record.getEvaluation().getTeacherAssignment().getSection().getName();
	}

	private static String courseName(GradeRecord record) {
		if (record.getEvaluation() == null
				|| record.getEvaluation().getTeacherAssignment() == null
				|| record.getEvaluation().getTeacherAssignment().getCourse() == null) {
			return null;
		}
		return record.getEvaluation().getTeacherAssignment().getCourse().getName();
	}
}
