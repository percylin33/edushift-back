package com.edushift.modules.attendance.audit;

/**
 * Canonical audit event names for the attendance module
 * (Sprint 6 / BE-6.4 / D-ATT §9.2).
 *
 * <p>The shared {@link com.edushift.modules.audit.events.AuditAction}
 * enum is intentionally CRUD-flavoured ({@code CREATE / UPDATE /
 * DELETE / ACCESS_DENIED}) so the audit timeline is consistent
 * across modules. To carry the richer attendance-specific semantics
 * we also stamp every {@code AuditEvent.metadata} with the key
 * {@value #METADATA_KEY} set to one of the constants below; readers
 * can then dashboard / filter / alert on those names without parsing
 * free-form summaries.
 *
 * <h3>Mapping at a glance</h3>
 * <table>
 *   <caption>Mapping of attendance events to canonical CRUD actions</caption>
 *   <tr><th>Constant</th><th>{@code AuditAction}</th><th>Resource</th></tr>
 *   <tr><td>{@link #SESSION_OPENED}</td>     <td>CREATE</td>         <td>AttendanceSession</td></tr>
 *   <tr><td>{@link #SESSION_CLOSED}</td>     <td>UPDATE</td>         <td>AttendanceSession</td></tr>
 *   <tr><td>{@link #CHECKED_IN}</td>         <td>CREATE</td>         <td>AttendanceRecord</td></tr>
 *   <tr><td>{@link #MANUAL_CHECKED_IN}</td>  <td>CREATE</td>         <td>AttendanceRecord</td></tr>
 *   <tr><td>{@link #RECORD_EDITED}</td>      <td>UPDATE</td>         <td>AttendanceRecord</td></tr>
 *   <tr><td>{@link #QR_ISSUED}</td>          <td>CREATE</td>         <td>StudentAttendanceQr</td></tr>
 *   <tr><td>{@link #QR_ROTATED}</td>         <td>UPDATE</td>         <td>StudentAttendanceQr</td></tr>
 *   <tr><td>{@link #QR_REJECTED}</td>        <td>ACCESS_DENIED</td>  <td>StudentAttendanceQr</td></tr>
 * </table>
 */
public final class AttendanceAuditEventTypes {

	/** Metadata key under which the canonical name is stamped. */
	public static final String METADATA_KEY = "attendance.event";

	public static final String SESSION_OPENED  = "AttendanceSessionOpened";
	public static final String SESSION_CLOSED  = "AttendanceSessionClosed";
	public static final String CHECKED_IN      = "AttendanceCheckedIn";
	public static final String MANUAL_CHECKED_IN = "AttendanceManualCheckedIn";
	public static final String RECORD_EDITED   = "AttendanceRecordEdited";
	public static final String QR_ISSUED       = "AttendanceQrIssued";
	public static final String QR_ROTATED      = "AttendanceQrRotated";
	public static final String QR_REJECTED     = "AttendanceQrRejected";

	/** Resource type strings used by callers. */
	public static final String RESOURCE_SESSION = "AttendanceSession";
	public static final String RESOURCE_RECORD  = "AttendanceRecord";
	public static final String RESOURCE_QR      = "StudentAttendanceQr";

	private AttendanceAuditEventTypes() {
	}
}
