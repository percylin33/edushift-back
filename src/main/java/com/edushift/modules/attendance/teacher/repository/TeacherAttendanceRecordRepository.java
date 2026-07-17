package com.edushift.modules.attendance.teacher.repository;

import com.edushift.modules.attendance.teacher.entity.TeacherAttendanceRecord;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherAttendanceRecordRepository
        extends JpaRepository<TeacherAttendanceRecord, UUID> {

    Optional<TeacherAttendanceRecord> findByPublicUuid(UUID publicUuid);

    Optional<TeacherAttendanceRecord> findByTeacherAssignmentAndScheduledDate(
            TeacherAssignment assignment, LocalDate date);

    @Query("""
            select t from TeacherAttendanceRecord t
            where t.teacherAssignment = :assignment
            order by t.scheduledDate desc
            """)
    List<TeacherAttendanceRecord> findAllByAssignment(
            @Param("assignment") TeacherAssignment assignment);

    @Query("""
            select t.status, count(t)
            from TeacherAttendanceRecord t
            where t.teacherAssignment = :assignment
            group by t.status
            """)
    List<Object[]> countByStatus(@Param("assignment") TeacherAssignment assignment);
}
