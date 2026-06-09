package com.edushift.modules.sessions.learning.mapper;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem.AssignmentSummary;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem.UnitSummary;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.AssignmentRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CapacityRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CompetencyRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CourseRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.PeriodRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.SectionRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.TeacherRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.UnitRef;
import com.edushift.modules.sessions.learning.dto.SessionContentDto;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionContent;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link LearningSession}. No MapStruct, same
 * convention as the rest of the codebase.
 */
@Component
public class LearningSessionMapper {

	// =========================================================================
	// Entity -> DTO
	// =========================================================================

	public LearningSessionResponse toResponse(LearningSession session) {
		return new LearningSessionResponse(
				session.getPublicUuid(),
				session.getVersion(),
				toAssignmentRef(session.getTeacherAssignment()),
				toUnitRef(session.getUnit()),
				session.getTitle(),
				session.getObjective(),
				session.getScheduledDate(),
				session.getDurationMinutes(),
				session.getStatus(),
				toContentDto(session.getContent()),
				toCompetencyRefs(session.getCompetencies()),
				toCapacityRefs(session.getCapacities()),
				session.getStartedAt(),
				session.getEndedAt(),
				session.getCancelledAt(),
				session.getCreatedAt(),
				session.getUpdatedAt()
		);
	}

	public LearningSessionListItem toListItem(LearningSession session) {
		TeacherAssignment a = session.getTeacherAssignment();
		Unit u = session.getUnit();
		return new LearningSessionListItem(
				session.getPublicUuid(),
				session.getVersion(),
				session.getTitle(),
				session.getScheduledDate(),
				session.getDurationMinutes(),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndedAt(),
				session.getCancelledAt(),
				toAssignmentSummary(a),
				toUnitSummary(u),
				session.getCreatedAt(),
				session.getUpdatedAt()
		);
	}

	// =========================================================================
	// DTO -> Entity (create / update merges)
	// =========================================================================

	public LearningSession fromCreate(CreateLearningSessionRequest request,
			TeacherAssignment assignment, Unit unit) {
		LearningSession session = new LearningSession();
		session.setTeacherAssignment(assignment);
		session.setUnit(unit);
		session.setTitle(request.title());
		session.setObjective(blankToNull(request.objective()));
		session.setScheduledDate(request.scheduledDate());
		session.setDurationMinutes(request.durationMinutes());
		session.setContent(toEntityContent(request.content()));
		return session;
	}

	/**
	 * Applies the partial-merge update. Whitelisted fields only - the
	 * lifecycle ({@code status}, timestamps), {@code teacherAssignment}
	 * and the M:N collections are NEVER mutated here. Lifecycle goes
	 * through {@code start}/{@code complete}/{@code cancel}; the M:N
	 * collections are replaced explicitly by the service.
	 */
	public void applyUpdate(com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest patch,
			LearningSession session) {
		if (patch.title() != null) {
			session.setTitle(patch.title());
		}
		if (patch.objective() != null) {
			session.setObjective(blankToNull(patch.objective()));
		}
		if (patch.scheduledDate() != null) {
			session.setScheduledDate(patch.scheduledDate());
		}
		if (patch.durationMinutes() != null) {
			session.setDurationMinutes(patch.durationMinutes());
		}
		if (patch.content() != null) {
			session.setContent(toEntityContent(patch.content()));
		}
	}

	// =========================================================================
	// Content (jsonb) <-> Dto
	// =========================================================================

	public SessionContent toEntityContent(SessionContentDto dto) {
		if (dto == null) return null;
		SessionContent c = new SessionContent();
		c.setObjective(blankToNull(dto.objective()));
		c.setActivities(safeList(dto.activities()));
		c.setMaterials(safeList(dto.materials()));
		c.setObservations(blankToNull(dto.observations()));
		return c;
	}

	private static SessionContentDto toContentDto(SessionContent content) {
		if (content == null) return null;
		return new SessionContentDto(
				content.getObjective(),
				safeList(content.getActivities()),
				safeList(content.getMaterials()),
				content.getObservations()
		);
	}

	// =========================================================================
	// Refs
	// =========================================================================

	private static AssignmentRef toAssignmentRef(TeacherAssignment a) {
		if (a == null) return null;
		return new AssignmentRef(
				a.getPublicUuid(),
				toTeacherRef(a.getTeacher()),
				toCourseRef(a.getCourse()),
				toSectionRef(a.getSection()),
				toPeriodRef(a.getAcademicPeriod())
		);
	}

	private static TeacherRef toTeacherRef(Teacher t) {
		if (t == null) return null;
		return new TeacherRef(t.getPublicUuid(), t.getFirstName(), t.getLastName());
	}

	private static CourseRef toCourseRef(Course c) {
		if (c == null) return null;
		return new CourseRef(c.getPublicUuid(), c.getCode(), c.getName());
	}

	private static SectionRef toSectionRef(Section s) {
		if (s == null) return null;
		return new SectionRef(s.getPublicUuid(), s.getName());
	}

	private static PeriodRef toPeriodRef(AcademicPeriod p) {
		if (p == null) return null;
		return new PeriodRef(
				p.getPublicUuid(),
				p.getPeriodType() == null ? null : p.getPeriodType().name(),
				p.getOrdinal(),
				p.getName(),
				p.getStartDate(),
				p.getEndDate()
		);
	}

	private static UnitRef toUnitRef(Unit u) {
		if (u == null) return null;
		return new UnitRef(u.getPublicUuid(), u.getName(), u.getDisplayOrder());
	}

	private static AssignmentSummary toAssignmentSummary(TeacherAssignment a) {
		if (a == null) return null;
		Teacher t = a.getTeacher();
		Course c = a.getCourse();
		Section s = a.getSection();
		String teacherName = (t == null)
				? null
				: ((nullSafe(t.getFirstName()) + " " + nullSafe(t.getLastName())).trim());
		return new AssignmentSummary(
				a.getPublicUuid(),
				blankToNull(teacherName),
				c == null ? null : c.getCode(),
				s == null ? null : s.getName()
		);
	}

	private static UnitSummary toUnitSummary(Unit u) {
		if (u == null) return null;
		return new UnitSummary(u.getPublicUuid(), u.getName(), u.getDisplayOrder());
	}

	// =========================================================================
	// Collections
	// =========================================================================

	private static List<CompetencyRef> toCompetencyRefs(java.util.Collection<Competency> source) {
		if (source == null || source.isEmpty()) return List.of();
		return source.stream()
				.sorted(Comparator
						.comparing(Competency::getDisplayOrder,
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Competency::getCode,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.map(c -> new CompetencyRef(c.getPublicUuid(), c.getCode(), c.getName()))
				.toList();
	}

	private static List<CapacityRef> toCapacityRefs(java.util.Collection<Capacity> source) {
		if (source == null || source.isEmpty()) return List.of();
		return source.stream()
				.sorted(Comparator
						.comparing((Capacity c) ->
										c.getCompetency() == null
												? null
												: c.getCompetency().getDisplayOrder(),
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Capacity::getDisplayOrder,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.map(c -> new CapacityRef(
						c.getPublicUuid(),
						c.getCode(),
						c.getName(),
						c.getCompetency() == null ? null : c.getCompetency().getPublicUuid()))
				.toList();
	}

	// =========================================================================
	// Utils
	// =========================================================================

	private static <T> List<T> safeList(List<T> source) {
		return source == null ? new ArrayList<>() : new ArrayList<>(source);
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
