package com.edushift.modules.attendance.audit;

import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.QrRevokedReason;
import com.edushift.modules.attendance.entity.StudentAttendanceQr;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.shared.constants.ModuleNames;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Typed facade over {@link AuditLogger} for the attendance module
 * (Sprint 6 / BE-6.4).
 *
 * <p>Centralises the {@code (AuditAction, resourceType, resourceId,
 * summary, metadata)} tuple for the seven attendance events listed in
 * {@link AttendanceAuditEventTypes}. Callers don't have to remember
 * how to map their domain event to the CRUD-flavoured shared enum;
 * they call e.g. {@link #logSessionOpened(AttendanceSession)} and the
 * helper takes care of the consistent shape (action, resource, the
 * canonical name in {@code metadata.attendance.event}, etc.).
 *
 * <h3>Failure handling</h3>
 * Any exception raised by {@link AuditLogger} is caught and logged at
 * WARN — auditing must never fail the originating business operation.
 * The shared listener already persists asynchronously after commit, so
 * a transient persistence error there cannot rollback the action; this
 * helper layer adds the same defensive boundary against pathological
 * cases (e.g. the DomainEventPublisher throwing during construction).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceAuditLogger {

	private final AuditLogger delegate;

	// =====================================================================
	// Sessions
	// =====================================================================

	/** Audit a successful {@code openSession}. */
	public void logSessionOpened(AttendanceSession session, boolean wasIdempotent) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.SESSION_OPENED);
		meta.put("sectionPublicUuid", uuid(session.getSection() != null
				? session.getSection().getPublicUuid() : null));
		meta.put("occurredOn", session.getOccurredOn() != null
				? session.getOccurredOn().toString() : null);
		meta.put("slot", session.getSlot() != null
				? session.getSlot().name() : null);
		meta.put("status", session.getStatus() != null
				? session.getStatus().name() : null);
		meta.put("wasIdempotent", wasIdempotent);
		emit(AuditAction.CREATE,
				AttendanceAuditEventTypes.RESOURCE_SESSION,
				session.getPublicUuid(),
				"Attendance session opened" + (wasIdempotent ? " (idempotent)" : ""),
				meta);
	}

	/** Audit a successful {@code closeSession}. */
	public void logSessionClosed(AttendanceSession session, int absentMaterialized) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.SESSION_CLOSED);
		meta.put("absentMaterialized", absentMaterialized);
		meta.put("closedAt", session.getClosedAt() != null
				? session.getClosedAt().toString() : null);
		emit(AuditAction.UPDATE,
				AttendanceAuditEventTypes.RESOURCE_SESSION,
				session.getPublicUuid(),
				"Attendance session closed",
				meta);
	}

	// =====================================================================
	// Check-in / records
	// =====================================================================

	/** Audit a successful {@code checkIn} (idempotent or fresh). */
	public void logCheckedIn(AttendanceRecord record, boolean wasIdempotent) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.CHECKED_IN);
		meta.put("sessionPublicUuid", uuid(record.getSession() != null
				? record.getSession().getPublicUuid() : null));
		meta.put("studentPublicUuid", uuid(record.getStudent() != null
				? record.getStudent().getPublicUuid() : null));
		meta.put("status", record.getStatus() != null
				? record.getStatus().name() : null);
		meta.put("occurredAt", record.getOccurredAt() != null
				? record.getOccurredAt().toString() : null);
		meta.put("wasIdempotent", wasIdempotent);
		emit(AuditAction.CREATE,
				AttendanceAuditEventTypes.RESOURCE_RECORD,
				record.getPublicUuid(),
				"Attendance check-in" + (wasIdempotent ? " (idempotent)" : ""),
				meta);
	}

	/**
	 * Audit a successful manual check-in (idempotent or fresh).
	 *
	 * <p>Stamped distinctly from {@link #logCheckedIn(AttendanceRecord, boolean)}
	 * so the security team can dashboard "auxiliary marking by name" vs
	 * "QR-driven scans" — useful when investigating discrepancies like
	 * "a kid was marked present without their QR".
	 *
	 * @param record           the persisted record (or pre-existing one if idempotent).
	 * @param wasIdempotent    {@code true} when the record already existed.
	 * @param sessionWasOpened {@code true} when the manual flow auto-opened a
	 *                         brand-new session (the section had no ACTIVE
	 *                         session for that day+slot). Surfaces the side
	 *                         effect explicitly on the timeline.
	 */
	public void logManualCheckedIn(
			AttendanceRecord record, boolean wasIdempotent, boolean sessionWasOpened) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.MANUAL_CHECKED_IN);
		meta.put("sessionPublicUuid", uuid(record.getSession() != null
				? record.getSession().getPublicUuid() : null));
		meta.put("studentPublicUuid", uuid(record.getStudent() != null
				? record.getStudent().getPublicUuid() : null));
		meta.put("status", record.getStatus() != null
				? record.getStatus().name() : null);
		meta.put("occurredAt", record.getOccurredAt() != null
				? record.getOccurredAt().toString() : null);
		meta.put("wasIdempotent", wasIdempotent);
		meta.put("sessionAutoOpened", sessionWasOpened);
		meta.put("manual", true);
		emit(AuditAction.CREATE,
				AttendanceAuditEventTypes.RESOURCE_RECORD,
				record.getPublicUuid(),
				"Attendance manual check-in"
						+ (wasIdempotent ? " (idempotent)" : "")
						+ (sessionWasOpened ? " [auto-opened session]" : ""),
				meta);
	}

	/**
	 * Audit a check-in attempt that was rejected before any DB row was
	 * mutated. Always {@code ACCESS_DENIED} — the whole point of this
	 * event is to feed alerting on suspicious scans (cross-tenant
	 * attempts, expired QRs, forged tokens, etc.).
	 *
	 * @param reason          one of the {@code AttendanceErrorCodes}
	 *                        constants (e.g. {@code QR_TENANT_MISMATCH}).
	 *                        Stamped both as the audit metadata field
	 *                        and as the summary suffix for fast scanning.
	 * @param attemptedStudentPublicUuid student publicUuid extracted
	 *                        from the QR claims. Nullable when the
	 *                        token failed to parse before the {@code sub}
	 *                        could be read.
	 * @param sessionPublicUuid optional session the scan was targeting.
	 */
	public void logQrRejected(
			String reason,
			UUID attemptedStudentPublicUuid,
			UUID sessionPublicUuid) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.QR_REJECTED);
		meta.put("reason", reason);
		meta.put("attemptedStudentPublicUuid", uuid(attemptedStudentPublicUuid));
		meta.put("sessionPublicUuid", uuid(sessionPublicUuid));
		emit(AuditAction.ACCESS_DENIED,
				AttendanceAuditEventTypes.RESOURCE_QR,
				attemptedStudentPublicUuid,
				"QR rejected: " + reason,
				meta);
	}

	/** Audit a successful manual {@code updateRecord}. */
	public void logRecordEdited(
			AttendanceRecord record,
			AttendanceRecordStatus previousStatus,
			String previousNotes) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.RECORD_EDITED);
		meta.put("sessionPublicUuid", uuid(record.getSession() != null
				? record.getSession().getPublicUuid() : null));
		meta.put("studentPublicUuid", uuid(record.getStudent() != null
				? record.getStudent().getPublicUuid() : null));
		meta.put("previousStatus", previousStatus != null
				? previousStatus.name() : null);
		meta.put("newStatus", record.getStatus() != null
				? record.getStatus().name() : null);
		meta.put("previousNotes", truncate(previousNotes));
		meta.put("newNotes", truncate(record.getNotes()));
		emit(AuditAction.UPDATE,
				AttendanceAuditEventTypes.RESOURCE_RECORD,
				record.getPublicUuid(),
				"Attendance record edited",
				meta);
	}

	// =====================================================================
	// QR lifecycle
	// =====================================================================

	/**
	 * Audit a brand-new QR issuance (no previous active row revoked).
	 */
	public void logQrIssued(StudentAttendanceQr qr) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.QR_ISSUED);
		meta.put("studentPublicUuid", uuid(qr.getStudent() != null
				? qr.getStudent().getPublicUuid() : null));
		meta.put("issuedAt", qr.getIssuedAt() != null
				? qr.getIssuedAt().toString() : null);
		emit(AuditAction.CREATE,
				AttendanceAuditEventTypes.RESOURCE_QR,
				resolveQrId(qr),
				"QR credential issued",
				meta);
	}

	/**
	 * Audit a rotation: a previous active row was revoked and a fresh
	 * QR was issued in its place.
	 */
	public void logQrRotated(
			StudentAttendanceQr qr,
			QrRevokedReason previousReason) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.QR_ROTATED);
		meta.put("studentPublicUuid", uuid(qr.getStudent() != null
				? qr.getStudent().getPublicUuid() : null));
		meta.put("issuedAt", qr.getIssuedAt() != null
				? qr.getIssuedAt().toString() : null);
		meta.put("previousRevokedReason", previousReason != null
				? previousReason.name() : null);
		emit(AuditAction.UPDATE,
				AttendanceAuditEventTypes.RESOURCE_QR,
				resolveQrId(qr),
				"QR credential rotated",
				meta);
	}

	// =====================================================================
	// Justifications (BE-18.5)
	// =====================================================================

	/** Audit when a justification is submitted. */
	public void logJustificationSubmitted(AttendanceRecord record) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.JUSTIFICATION_SUBMITTED);
		meta.put("publicUuid", uuid(record.getPublicUuid()));
		emit(AuditAction.UPDATE, AttendanceAuditEventTypes.RESOURCE_RECORD,
				record.getPublicUuid(), "Justification submitted", meta);
	}

	/** Audit when a justification is approved or rejected. */
	public void logJustificationResolved(AttendanceRecord record, boolean approved) {
		Map<String, Object> meta = baseMeta(AttendanceAuditEventTypes.JUSTIFICATION_RESOLVED);
		meta.put("publicUuid", uuid(record.getPublicUuid()));
		meta.put("status", record.getJustificationStatus() != null
				? record.getJustificationStatus().name() : null);
		emit(approved ? AuditAction.UPDATE : AuditAction.UPDATE,
				AttendanceAuditEventTypes.RESOURCE_RECORD,
				record.getPublicUuid(),
				approved ? "Justification approved" : "Justification rejected",
				meta);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private void emit(
			AuditAction action,
			String resourceType,
			UUID resourceId,
			String summary,
			Map<String, Object> metadata) {
		try {
			delegate.log(action, resourceType, resourceId, summary,
					metadata, ModuleNames.ATTENDANCE);
		}
		catch (RuntimeException e) {
			// Never bubble audit failures into the request thread.
			log.warn("[attendance-audit] failed to publish audit event "
							+ "type={} resource={}/{} summary=\"{}\" -- {}",
					action, resourceType, resourceId, summary,
					e.getClass().getSimpleName());
		}
	}

	private static Map<String, Object> baseMeta(String eventName) {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put(AttendanceAuditEventTypes.METADATA_KEY, eventName);
		return meta;
	}

	private static String uuid(UUID v) {
		return v == null ? null : v.toString();
	}

	/**
	 * Truncate notes to 200 chars to bound the audit payload size and
	 * keep the timeline readable. Anything longer is shortened with an
	 * ellipsis suffix; full text is still on the entity itself.
	 */
	private static String truncate(String value) {
		if (value == null) return null;
		if (value.length() <= 200) return value;
		return value.substring(0, 200) + "…";
	}

	/**
	 * The QR row has no public UUID (see
	 * {@link com.edushift.modules.attendance.entity.StudentAttendanceQr}'s
	 * "Identity" javadoc). Fall back to the student's public UUID as
	 * the audit resource id so the timeline link still resolves to a
	 * meaningful FE page.
	 */
	private static UUID resolveQrId(StudentAttendanceQr qr) {
		if (qr.getStudent() != null) {
			return qr.getStudent().getPublicUuid();
		}
		return qr.getId();
	}
}
