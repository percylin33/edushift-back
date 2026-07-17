package com.edushift.modules.attendance.teacher.entity;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(
    name = "teacher_attendance_records",
    schema = "edushift",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_attendance_public_uuid",
                columnNames = "public_uuid"),
        @UniqueConstraint(name = "uk_teacher_attendance_assignment_date",
                columnNames = {"tenant_id", "teacher_assignment_id", "scheduled_date"})
    },
    indexes = {
        @Index(name = "idx_teacher_attendance_tenant_period",
                columnList = "tenant_id, academic_period_id"),
        @Index(name = "idx_teacher_attendance_tenant_assignment",
                columnList = "tenant_id, teacher_assignment_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "scheduledDate", "status"})
@SQLDelete(sql = "UPDATE edushift.teacher_attendance_records "
        + "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
        + "WHERE id = ?")
public class TeacherAttendanceRecord extends TenantAwareEntity {

    public enum Status { PRESENT, ABSENT, JUSTIFIED, LATE }

    @Column(name = "public_uuid", nullable = false, updatable = false,
            unique = true, columnDefinition = "uuid")
    private UUID publicUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_assignment_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_teacher_attendance_assignment"))
    private TeacherAssignment teacherAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_period_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_teacher_attendance_period"))
    private AcademicPeriod academicPeriod;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by_user_id", columnDefinition = "uuid")
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    private void onPrePersist() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
