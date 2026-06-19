package com.edushift.modules.tasks.error;

/**
 * Stable error codes for the LMS tasks + submissions modules
 * (Sprint 7a / BE-7a.2). See {@code docs/modules/lms-tasks.md} §7.
 */
public final class TasksErrorCodes {

    // task-level
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String SECTION_NOT_FOUND = "SECTION_NOT_FOUND";
    public static final String DUE_AT_IN_PAST = "DUE_AT_IN_PAST";
    public static final String RECORD_EMPTY_PATCH = "RECORD_EMPTY_PATCH";

    // submission-level
    public static final String SUBMISSION_NOT_FOUND = "SUBMISSION_NOT_FOUND";
    public static final String ASSIGNMENT_PAST_DUE = "ASSIGNMENT_PAST_DUE";
    public static final String RESUBMISSION_NOT_ALLOWED = "RESUBMISSION_NOT_ALLOWED";
    public static final String GRADE_OUT_OF_RANGE = "GRADE_OUT_OF_RANGE";
    public static final String NOT_GUARDIAN_OF_STUDENT = "NOT_GUARDIAN_OF_STUDENT";
    public static final String STUDENT_NOT_ENROLLED_IN_SECTION = "STUDENT_NOT_ENROLLED_IN_SECTION";
    public static final String INCONSISTENT_PAYLOAD = "INCONSISTENT_PAYLOAD";

    private TasksErrorCodes() {
        // utility class
    }
}
