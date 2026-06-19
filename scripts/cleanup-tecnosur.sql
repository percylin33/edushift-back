DO $do$
DECLARE tid uuid;
BEGIN
    SELECT id INTO tid FROM edushift.tenants WHERE slug='tecnosur';
    IF tid IS NOT NULL THEN
        DELETE FROM edushift.tenant_ai_usage        WHERE tenant_id = tid;
        DELETE FROM edushift.ai_generations        WHERE tenant_id = tid;
        DELETE FROM edushift.attendance_records    WHERE tenant_id = tid;
        DELETE FROM edushift.attendance_sessions   WHERE tenant_id = tid;
        -- Disable MC trigger on options (it also fires on DELETE).
        ALTER TABLE edushift.lms_quiz_options DISABLE TRIGGER trg_lms_quiz_options_one_correct;
        DELETE FROM edushift.lms_quiz_options      WHERE tenant_id = tid;
        DELETE FROM edushift.lms_quiz_questions    WHERE tenant_id = tid;
        ALTER TABLE edushift.lms_quiz_options ENABLE TRIGGER trg_lms_quiz_options_one_correct;
        DELETE FROM edushift.lms_quizzes           WHERE tenant_id = tid;
        DELETE FROM edushift.student_guardians     WHERE tenant_id = tid;
        DELETE FROM edushift.guardians             WHERE tenant_id = tid;
        DELETE FROM edushift.student_enrollments   WHERE tenant_id = tid;
        DELETE FROM edushift.students              WHERE tenant_id = tid;
        DELETE FROM edushift.teacher_assignments   WHERE tenant_id = tid;
        DELETE FROM edushift.teachers              WHERE tenant_id = tid;
        DELETE FROM edushift.sections              WHERE tenant_id = tid;
        DELETE FROM edushift.course_levels         WHERE tenant_id = tid;
        DELETE FROM edushift.courses               WHERE tenant_id = tid;
        DELETE FROM edushift.grades                WHERE tenant_id = tid;
        DELETE FROM edushift.academic_levels       WHERE tenant_id = tid;
        DELETE FROM edushift.academic_periods      WHERE tenant_id = tid;
        DELETE FROM edushift.academic_years        WHERE tenant_id = tid;
        DELETE FROM edushift.users                 WHERE tenant_id = tid;
        DELETE FROM edushift.tenant_ai_settings    WHERE tenant_id IN (SELECT public_uuid FROM edushift.tenants WHERE slug='tecnosur');
        DELETE FROM edushift.tenants WHERE slug = 'tecnosur';
    END IF;
END
$do$;
SELECT 'cleaned' AS status;
