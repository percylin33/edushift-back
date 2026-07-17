package com.edushift.modules.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.repository.AnnouncementRecipientRepository;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test for {@link AnnouncementAudienceResolver} (V75 fix).
 *
 * <p>Regression test for the FK mismatch bug surfaced by V48
 * (DEBT-FK-BUGS-2 follow-up). Before V75 + this resolver fix, the
 * resolver returned {@code users.id} (internal UUIDv7 PK) values, but
 * the FK on {@code announcement_recipients.user_id} (re-targeted by
 * V48) points at {@code users.public_uuid} (UUIDv4). The publish flow
 * therefore failed with {@code DataIntegrityViolationException} on
 * every audience type that goes through the resolver (SCHOOL / ROLE /
 * GRADE / SECTION / COURSE).</p>
 *
 * <h3>What this test asserts</h3>
 * <ul>
 *   <li>{@link AnnouncementAudienceResolver#resolve} returns
 *       {@code users.public_uuid} values, NOT {@code users.id}.</li>
 *   <li>The end-to-end publish flow inserts recipient rows that
 *       satisfy the FK constraint
 *       {@code fk_announcement_recipients_user → users.public_uuid}.</li>
 *   <li>Every recipient row's {@code user_id} matches a real
 *       {@code users.public_uuid} in the database (no orphan rows).</li>
 * </ul>
 *
 * <h3>Why a separate IT from {@code AnnouncementTenantIsolationIT}</h3>
 * The isolation IT only covers read/write cross-tenant access — it
 * never calls {@code publish()} with a non-empty audience. Before V75
 * every {@code publish()} in production would 500 on the FK violation;
 * this test asserts the success path end-to-end.
 */
@DisplayName("AnnouncementAudienceResolver returns public_uuid (V75 fix)")
class AnnouncementAudienceResolverIT extends IntegrationTest {

    @Autowired private AnnouncementAudienceResolver resolver;
    @Autowired private AnnouncementRepository announcementRepo;
    @Autowired private AnnouncementRecipientRepository recipientRepo;
    @Autowired private TenantRepository tenantRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private StudentRepository studentRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        if (tx == null) tx = new TransactionTemplate(txManager);
    }

    @Test
    @DisplayName("SCHOOL audience — resolved UUIDs match users.public_uuid (not users.id)")
    void schoolReturnsPublicUuidNotId() {
        Fixture f = seedTenant("resolve-school-", 3);

        Announcement a = new Announcement();
        a.setTenantId(f.tenant().getId());
        a.setAuthorUserId(f.admin().getPublicUuid());
        a.setTitle("Reunion general");
        a.setBodyHtml("<p>Reunion el lunes</p>");
        a.setAudienceType(Announcement.AudienceType.SCHOOL);
        a.setStatus(Announcement.Status.DRAFT);

        TenantContext.runAs(f.tenant().getId(), () -> {
            List<UUID> resolved = resolver.resolve(a);

            // Critical assertion: every resolved UUID matches a real
            // users.public_uuid, NOT users.id. Before V75 the resolver
            // returned users.id (UUIDv7) which the V48 FK rejected.
            for (UUID resolvedUuid : resolved) {
                boolean matchesPublicUuid = f.allUsers().stream()
                        .anyMatch(u -> u.getPublicUuid().equals(resolvedUuid));
                boolean matchesId = f.allUsers().stream()
                        .anyMatch(u -> u.getId().equals(resolvedUuid));
                assertThat(matchesPublicUuid)
                        .as("resolved UUID %s must match a users.public_uuid (FK target)", resolvedUuid)
                        .isTrue();
                assertThat(matchesId)
                        .as("resolved UUID %s must NOT match users.id (legacy UUIDv7 PK)", resolvedUuid)
                        .isFalse();
            }
            return null;
        });
    }

    @Test
    @DisplayName("USER audience — FE-supplied publicUuids pass through unchanged")
    void userAudiencePassesThrough() {
        Fixture f = seedTenant("resolve-user-", 1);
        User target = f.allUsers().get(0);

        Announcement a = new Announcement();
        a.setTenantId(f.tenant().getId());
        a.setAuthorUserId(f.admin().getPublicUuid());
        a.setTitle("DM");
        a.setBodyHtml("<p>Directo</p>");
        a.setAudienceType(Announcement.AudienceType.USER);
        a.setAudienceIds(List.of(target.getPublicUuid().toString()));
        a.setStatus(Announcement.Status.DRAFT);

        TenantContext.runAs(f.tenant().getId(), () -> {
            List<UUID> resolved = resolver.resolve(a);
            assertThat(resolved).containsExactly(target.getPublicUuid());
            return null;
        });
    }

    @Test
    @DisplayName("End-to-end publish — recipient rows satisfy FK → users.public_uuid")
    void publishInsertsRecipientsWithPublicUuidFk() {
        Fixture f = seedTenant("resolve-e2e-", 2);

        // Create + persist a DRAFT announcement, then publish.
        Announcement draft = tx.execute(s -> TenantContext.runAs(f.tenant().getId(),
                () -> {
                    Announcement a = new Announcement();
                    a.setTenantId(f.tenant().getId());
                    a.setAuthorUserId(f.admin().getPublicUuid());
                    a.setTitle("E2E publish");
                    a.setBodyHtml("<p>Test</p>");
                    a.setAudienceType(Announcement.AudienceType.SCHOOL);
                    a.setStatus(Announcement.Status.DRAFT);
                    return announcementRepo.save(a);
                }));

        // Resolve audience + insert recipient rows. This is the same
        // sequence AnnouncementService.publish() runs (minus the
        // notification fan-out which needs a richer NotificationService
        // stub). Before V75 this call would 500 with
        // DataIntegrityViolationException at recipientRepo.save().
        TenantContext.runAs(f.tenant().getId(), () -> {
            List<UUID> recipients = resolver.resolve(draft);
            for (UUID uid : recipients) {
                var r = new com.edushift.modules.notifications.entity.AnnouncementRecipient();
                r.setTenantId(f.tenant().getId());
                r.setAnnouncementId(draft.getId());
                r.setUserId(uid);
                recipientRepo.save(r);
            }
            return null;
        });

        // Verify every recipient.user_id matches a real users.public_uuid.
        TenantContext.runAs(f.tenant().getId(), () -> {
            List<com.edushift.modules.notifications.entity.AnnouncementRecipient> saved =
                    recipientRepo.findAll().stream()
                            .filter(r -> r.getAnnouncementId().equals(draft.getId()))
                            .toList();
            assertThat(saved).hasSize(f.allUsers().size());

            for (var recipient : saved) {
                User matched = userRepo.findByPublicUuid(recipient.getUserId()).orElse(null);
                assertThat(matched)
                        .as("recipient.user_id=%s must resolve to a real user via public_uuid",
                                recipient.getUserId())
                        .isNotNull();
                assertThat(matched.getId())
                        .as("recipient.user_id must NOT equal users.id (FK target is public_uuid)")
                        .isNotEqualTo(recipient.getUserId());
            }
            return null;
        });
    }

    // ============================================================ grade / section / course audiences

    /**
     * GRADE / SECTION / COURSE audience coverage added in Phase 3 after
     * V76+V77 retargeted {@code students.user_id} and
     * {@code teachers.user_id} to {@code users.public_uuid}. With the
     * retargeting the resolver no longer needs the JOIN through
     * {@code users}, and the SECTION/COURSE cases no longer reference
     * the (non-existent) {@code students.section_id} /
     * {@code teachers.course_id} columns — they go through the
     * {@code student_enrollments} and {@code teacher_assignments}
     * pivot tables instead.
     *
     * <p>This fixture seeds one academic bundle (year + period + grade
     * + section + course) and links a student + a teacher to it. The
     * student gets a {@code student_enrollments} row in the section;
     * the teacher gets a {@code teacher_assignments} row in the
     * section AND for the course. Then three audience cases are
     * exercised: GRADE (student only), SECTION (student + teacher),
     * COURSE (teacher only).</p>
     */
    @Test
    @DisplayName("GRADE/SECTION/COURSE audiences return correct user_ids post-V76+V77")
    void gradeSectionCourseAudiences() {
        Bundle b = seedAcademicBundle();
        UUID studentPublic = b.studentUser.getPublicUuid();
        UUID teacherPublic = b.teacherUser.getPublicUuid();

        // GRADE audience: only the student (enrolled in the section
        // whose grade = "5A"). The teacher doesn't have a student row.
        Announcement byGrade = new Announcement();
        byGrade.setTenantId(b.tenant.getId());
        byGrade.setAuthorUserId(b.admin.getPublicUuid());
        byGrade.setTitle("GRADE 5A");
        byGrade.setBodyHtml("<p>g</p>");
        byGrade.setAudienceType(Announcement.AudienceType.GRADE);
        // The resolver matches against `g.name` (the Grade entity
        // doesn't have a `code` column; the FE composer sends the
        // grade identifier which maps to g.name at write time).
        byGrade.setAudienceIds(List.of(b.grade.getName()));
        byGrade.setStatus(Announcement.Status.DRAFT);

        TenantContext.runAs(b.tenant.getId(), () -> {
            List<UUID> resolved = resolver.resolve(byGrade);
            assertThat(resolved)
                    .as("GRADE 5A audience must include the student but not the teacher")
                    .contains(studentPublic)
                    .doesNotContain(teacherPublic);
            for (UUID u : resolved) {
                assertThat(u)
                        .as("GRADE-audience UUID %s must be a public_uuid (FK target)", u)
                        .isNotEqualTo(b.studentUser.getId())
                        .isNotEqualTo(b.teacherUser.getId());
            }
            return null;
        });

        // SECTION audience: student (via student_enrollments) + teacher
        // (via teacher_assignments). The V75 SECTION query referenced a
        // non-existent s.section_id — V77 fixes that via the pivot
        // tables.
        Announcement bySection = new Announcement();
        bySection.setTenantId(b.tenant.getId());
        bySection.setAuthorUserId(b.admin.getPublicUuid());
        bySection.setTitle("SECTION A");
        bySection.setBodyHtml("<p>s</p>");
        bySection.setAudienceType(Announcement.AudienceType.SECTION);
        bySection.setAudienceIds(List.of(b.section.getPublicUuid().toString()));
        bySection.setStatus(Announcement.Status.DRAFT);

        TenantContext.runAs(b.tenant.getId(), () -> {
            List<UUID> resolved = resolver.resolve(bySection);
            assertThat(resolved)
                    .as("SECTION audience must include both student and teacher")
                    .contains(studentPublic, teacherPublic);
            for (UUID u : resolved) {
                assertThat(u)
                        .as("SECTION-audience UUID must be a public_uuid")
                        .isNotEqualTo(b.studentUser.getId())
                        .isNotEqualTo(b.teacherUser.getId());
            }
            return null;
        });

        // COURSE audience: only the teacher (no direct student→course
        // enrollment; students are enrolled in sections). The V75
        // COURSE query referenced t.course_id (also non-existent) —
        // V77 fixes via teacher_assignments.course_id.
        Announcement byCourse = new Announcement();
        byCourse.setTenantId(b.tenant.getId());
        byCourse.setAuthorUserId(b.admin.getPublicUuid());
        byCourse.setTitle("COURSE MAT");
        byCourse.setBodyHtml("<p>c</p>");
        byCourse.setAudienceType(Announcement.AudienceType.COURSE);
        byCourse.setAudienceIds(List.of(b.course.getPublicUuid().toString()));
        byCourse.setStatus(Announcement.Status.DRAFT);

        TenantContext.runAs(b.tenant.getId(), () -> {
            List<UUID> resolved = resolver.resolve(byCourse);
            assertThat(resolved)
                    .as("COURSE audience must include the teacher but not the student")
                    .contains(teacherPublic)
                    .doesNotContain(studentPublic);
            return null;
        });
    }

    // ============================================================ academic bundle fixture

    private record Bundle(
            Tenant tenant, User admin,
            com.edushift.modules.academic.year.entity.AcademicYear year,
            com.edushift.modules.academic.period.entity.AcademicPeriod period,
            com.edushift.modules.academic.levelgrade.entity.Grade grade,
            com.edushift.modules.academic.section.entity.Section section,
            com.edushift.modules.academic.course.entity.Course course,
            User studentUser, User teacherUser) {}

    @Autowired private com.edushift.modules.academic.year.repository.AcademicYearRepository yearRepo;
    @Autowired private com.edushift.modules.academic.period.repository.AcademicPeriodRepository periodRepo;
    @Autowired private com.edushift.modules.academic.levelgrade.repository.GradeRepository gradeRepo;
    @Autowired private com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository levelRepo;
    @Autowired private com.edushift.modules.academic.section.repository.SectionRepository sectionRepo;
    @Autowired private com.edushift.modules.academic.course.repository.CourseRepository courseRepo;
    @Autowired private com.edushift.modules.academic.course.repository.CourseLevelRepository courseLevelRepo;
    @Autowired private com.edushift.modules.teachers.repository.TeacherRepository teacherRepo;
    @Autowired private com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository teacherAssignmentRepo;
    @Autowired private com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository studentEnrollmentRepo;

    private Bundle seedAcademicBundle() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(s -> {
            Tenant tenant = new Tenant();
            tenant.setPublicUuid(UUID.randomUUID());
            tenant.setSlug("phase3-resolver-" + suffix);
            tenant.setName("Phase3 Resolver " + suffix);
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant = tenantRepo.save(tenant);

            User admin = createUser(tenant, "admin-r-" + suffix,
                    "admin-r-" + suffix + "@resolver.test", UserRole.TENANT_ADMIN);

            User studentUser = createUser(tenant, "student-r-" + suffix,
                    "student-r-" + suffix + "@resolver.test", UserRole.STUDENT);
            User teacherUser = createUser(tenant, "teacher-r-" + suffix,
                    "teacher-r-" + suffix + "@resolver.test", UserRole.TEACHER);

            // Seed defaults for academic catalog (level, grade, etc).
            // We invoke the existing service if available; otherwise
            // create the rows manually. For this IT we go manual.
            com.edushift.modules.academic.levelgrade.entity.AcademicLevel primaria =
                    ensurePrimaria(tenant);
            com.edushift.modules.academic.levelgrade.entity.Grade grade =
                    ensureGrade(tenant, primaria, "Quinto A");

            com.edushift.modules.academic.year.entity.AcademicYear year =
                    new com.edushift.modules.academic.year.entity.AcademicYear();
            year.setPublicUuid(UUID.randomUUID());
            year.setName("2026-Resolver-" + suffix);
            year.setStartDate(java.time.LocalDate.of(2026, 3, 1));
            year.setEndDate(java.time.LocalDate.of(2026, 12, 20));
            year.setStatus(com.edushift.modules.academic.year.entity.AcademicYearStatus.ACTIVE);
            year = yearRepo.save(year);

            com.edushift.modules.academic.period.entity.AcademicPeriod period =
                    new com.edushift.modules.academic.period.entity.AcademicPeriod();
            period.setAcademicYear(year);
            period.setPeriodType(com.edushift.modules.academic.period.entity.PeriodType.BIMESTRE);
            period.setOrdinal(1);
            period.setName("I Bimestre");
            period.setStartDate(java.time.LocalDate.of(2026, 3, 1));
            period.setEndDate(java.time.LocalDate.of(2026, 5, 31));
            period = periodRepo.save(period);

            com.edushift.modules.academic.section.entity.Section section =
                    new com.edushift.modules.academic.section.entity.Section();
            section.setPublicUuid(UUID.randomUUID());
            section.setAcademicYear(year);
            section.setGrade(grade);
            section.setName("5A-Resolver-" + suffix);
            section.setDisplayOrder(1);
            section = sectionRepo.save(section);

            com.edushift.modules.academic.course.entity.Course course =
                    new com.edushift.modules.academic.course.entity.Course();
            course.setPublicUuid(UUID.randomUUID());
            course.setCode("MAT_R_" + suffix);
            course.setName("Matemática Resolver");
            course.setIsActive(true);
            course = courseRepo.save(course);

            com.edushift.modules.academic.course.entity.CourseLevel courseLevel =
                    new com.edushift.modules.academic.course.entity.CourseLevel();
            courseLevel.setCourse(course);
            courseLevel.setLevel(primaria);
            courseLevelRepo.save(courseLevel);

            // Student + Student.user_id = publicUuid (post-V76) — the
            // JOIN in SECTION now reads students.user_id directly.
            com.edushift.modules.students.entity.Student student =
                    new com.edushift.modules.students.entity.Student();
            student.setTenantId(tenant.getId());
            student.setPublicUuid(UUID.randomUUID());
            student.setDocumentType(com.edushift.modules.students.entity.DocumentType.DNI);
            student.setDocumentNumber("DNI-R-" + suffix);
            student.setFirstName("Student");
            student.setLastName("Resolver");
            student.setGender(com.edushift.modules.students.entity.Gender.NOT_SPECIFIED);
            student.setEnrollmentStatus(com.edushift.modules.students.entity.EnrollmentStatus.ENROLLED);
            student.setUserId(studentUser.getPublicUuid());
            studentRepo.save(student);

            com.edushift.modules.students.enrollments.entity.StudentEnrollment enrollment =
                    new com.edushift.modules.students.enrollments.entity.StudentEnrollment();
            enrollment.setTenantId(tenant.getId());
            enrollment.setPublicUuid(UUID.randomUUID());
            enrollment.setStudent(student);
            enrollment.setSection(section);
            enrollment.setAcademicYear(year);
            enrollment.setEnrolledAt(java.time.LocalDate.of(2026, 3, 1));
            enrollment.setStatus(
                    com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus.ACTIVE);
            studentEnrollmentRepo.save(enrollment);

            // Teacher + Teacher.user_id = publicUuid (post-V77).
            com.edushift.modules.teachers.entity.Teacher teacher =
                    new com.edushift.modules.teachers.entity.Teacher();
            teacher.setTenantId(tenant.getId());
            teacher.setPublicUuid(UUID.randomUUID());
            teacher.setDocumentType(com.edushift.modules.students.entity.DocumentType.DNI);
            teacher.setDocumentNumber("DNI-T-" + suffix);
            teacher.setFirstName("Teacher");
            teacher.setLastName("Resolver");
            teacher.setGender(com.edushift.modules.students.entity.Gender.NOT_SPECIFIED);
            teacher.setEmploymentStatus(com.edushift.modules.teachers.entity.EmploymentStatus.ACTIVE);
            teacher.setUserId(teacherUser.getPublicUuid());
            teacherRepo.save(teacher);

            com.edushift.modules.teachers.assignments.entity.TeacherAssignment assignment =
                    new com.edushift.modules.teachers.assignments.entity.TeacherAssignment();
            assignment.setTenantId(tenant.getId());
            assignment.setPublicUuid(UUID.randomUUID());
            assignment.setTeacher(teacher);
            assignment.setSection(section);
            assignment.setCourse(course);
            assignment.setAcademicPeriod(period);
            assignment.setAssignedAt(java.time.Instant.now());
            teacherAssignmentRepo.save(assignment);

            return new Bundle(tenant, admin, year, period, grade, section, course,
                    studentUser, teacherUser);
        });
    }

    private com.edushift.modules.academic.levelgrade.entity.AcademicLevel ensurePrimaria(Tenant t) {
        // Levels are not tenant-scoped in the schema (see V13); they are
        // a global catalog. findByCodeIgnoreCase is enough.
        return levelRepo.findByCodeIgnoreCase("PRIMARIA").orElseGet(() -> {
            var level = new com.edushift.modules.academic.levelgrade.entity.AcademicLevel();
            level.setTenantId(t.getId());
            level.setPublicUuid(UUID.randomUUID());
            level.setCode("PRIMARIA");
            level.setName("Primaria");
            level.setOrdinal(1);
            return levelRepo.save(level);
        });
    }

    private com.edushift.modules.academic.levelgrade.entity.Grade ensureGrade(
            Tenant t, com.edushift.modules.academic.levelgrade.entity.AcademicLevel level,
            String gradeName) {
        var existing = gradeRepo.findAllByLevelOrderByOrdinalAsc(level);
        for (var g : existing) {
            if (gradeName.equalsIgnoreCase(g.getName())) return g;
        }
        var grade = new com.edushift.modules.academic.levelgrade.entity.Grade();
        grade.setTenantId(t.getId());
        grade.setPublicUuid(UUID.randomUUID());
        grade.setLevel(level);
        grade.setName(gradeName);
        grade.setOrdinal(existing.size() + 1);
        return gradeRepo.save(grade);
    }

    // ============================================================ existing fixture

    private record Fixture(Tenant tenant, User admin, List<User> allUsers) {}

    private Fixture seedTenant(String slugPrefix, int extraUsers) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(status -> {
            Tenant t = new Tenant();
            t.setPublicUuid(UUID.randomUUID());
            t.setSlug(slugPrefix + suffix);
            t.setName("ResolverIT " + suffix);
            t.setStatus(TenantStatus.ACTIVE);
            t = tenantRepo.save(t);

            User admin = createUser(t, "admin-" + suffix, "admin-" + suffix + "@resolver.test",
                    UserRole.TENANT_ADMIN);

            java.util.ArrayList<User> users = new java.util.ArrayList<>();
            users.add(admin);
            for (int i = 0; i < extraUsers; i++) {
                users.add(createUser(t, "user-" + i + "-" + suffix,
                        "user-" + i + "-" + suffix + "@resolver.test", UserRole.STUDENT));
            }

            // Link students (whose user_id FKs users.id) to their users so
            // GRADE audience resolution has a path through the JOIN.
            for (User u : users) {
                if (Arrays.asList(u.getRoles()).contains(UserRole.STUDENT.name())) {
                    Student student = new Student();
                    student.setTenantId(t.getId());
                    student.setPublicUuid(UUID.randomUUID());
                    student.setDocumentType(DocumentType.DNI);
                    student.setDocumentNumber("DNI-" + u.getId().toString().substring(0, 8));
                    student.setFirstName(u.getFirstName());
                    student.setLastName(u.getLastName());
                    student.setGender(Gender.NOT_SPECIFIED);
                    student.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
                    student.setUserId(u.getId());
                    studentRepo.save(student);
                }
            }
            return new Fixture(t, admin, users);
        });
    }

    private User createUser(Tenant t, String username, String email, UserRole role) {
        User u = new User();
        u.setPublicUuid(UUID.randomUUID());
        u.setTenantId(t.getId());
        u.setFirstName(username);
        u.setLastName("of-resolver-it");
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("PassResolver-1!"));
        u.setStatus(UserStatus.ACTIVE);
        u.setRoles(new String[] { role.name() });
        return userRepo.save(u);
    }
}