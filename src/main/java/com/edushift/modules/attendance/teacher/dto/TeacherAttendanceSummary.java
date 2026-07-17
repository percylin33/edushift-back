package com.edushift.modules.attendance.teacher.dto;

import java.util.Map;

public record TeacherAttendanceSummary(
    long totalDays,
    long presentDays,
    long absentDays,
    long justifiedDays,
    long lateDays,
    double attendancePct
) {
    public static TeacherAttendanceSummary fromCounts(Map<Object, Long> counts, long totalDays) {
        long present = counts.getOrDefault("PRESENT", 0L);
        long absent = counts.getOrDefault("ABSENT", 0L);
        long justified = counts.getOrDefault("JUSTIFIED", 0L);
        long late = counts.getOrDefault("LATE", 0L);
        double pct = totalDays > 0
                ? ((double) (present + justified) / totalDays) * 100.0
                : 0.0;
        return new TeacherAttendanceSummary(totalDays, present, absent, justified, late, pct);
    }
}
