package com.edushift.modules.admin.plans;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_ai_policies", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class PlanAiPolicy extends AuditableEntity {

    @Column(name = "plan_id", nullable = false, unique = true)
    private UUID planId;

    @Column(name = "daily_request_limit", nullable = false)
    private int dailyRequestLimit;

    @Column(name = "monthly_token_limit", nullable = false)
    private long monthlyTokenLimit;

    @Column(name = "tutor_messages_per_student_day", nullable = false)
    private int tutorMessagesPerStudentDay;

    @Column(name = "institutional_document_limit", nullable = false)
    private int institutionalDocumentLimit;

    @Column(name = "institutional_text_bytes_limit", nullable = false)
    private long institutionalTextBytesLimit;
}
