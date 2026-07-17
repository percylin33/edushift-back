package com.edushift.modules.admin.plans;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_plans", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class PlatformPlan extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price_per_student_cents", nullable = false)
    private int pricePerStudentCents;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "max_teachers")
    private Integer maxTeachers;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private java.util.List<String> features;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
