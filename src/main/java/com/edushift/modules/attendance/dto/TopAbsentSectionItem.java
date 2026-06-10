package com.edushift.modules.attendance.dto;

import java.util.UUID;

/**
 * One row of the "Top secciones con mas inasistencias" widget
 * (Sprint 6 / BE-6.5).
 *
 * <p>Computed across the last 7 days (UTC) per
 * {@code docs/product/sprints/sprint-06-attendance-dashboard.md}
 * §BE-6.5. Ordered desc by {@link #absentCount}.
 *
 * <h3>What "absent" counts</h3>
 * Only records with {@code status = ABSENT} that
 * {@code occurred_at} within the 7-day window are counted.
 * Justifications, inasistencias marcadas por el docente y llegadas
 * tarde (LATE) no entran al ranking. Section
 * {@code deleted=true} queda filtrada por el tenant scope y no
 * aparece.
 *
 * @param sectionPublicUuid public UUID del row {@code sections}.
 * @param sectionName       nombre humano (e.g. "5to A").
 * @param gradeName         nombre del grado (e.g. "Primaria 5"). El
 *                          join no es estricto en caso de migraciones
 *                          (puede ser {@code null} si el grado fue
 *                          borrado despues de la seccion).
 * @param absentCount       total de records ABSENT en la ventana.
 * @param enrolledStudents  tamano de la seccion (solo ACTIVE) — util
 *                          para que el FE muestre "% inasistencia =
 *                          absentCount / enrolled".
 */
public record TopAbsentSectionItem(
		UUID sectionPublicUuid,
		String sectionName,
		String gradeName,
		long absentCount,
		long enrolledStudents
) {
}
