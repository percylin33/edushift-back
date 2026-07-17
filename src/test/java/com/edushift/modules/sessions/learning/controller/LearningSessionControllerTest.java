package com.edushift.modules.sessions.learning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.config.SecurityConfig;
import com.edushift.config.WebConfiguration;
import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem.AssignmentSummary;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem.UnitSummary;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.AssignmentRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CapacityRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CompetencyRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.CourseRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.PeriodRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.SectionRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.TeacherRef;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse.UnitRef;
import com.edushift.modules.sessions.learning.dto.LifecycleRequest;
import com.edushift.modules.sessions.learning.dto.SessionContentDto;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.sessions.learning.service.LearningSessionService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = LearningSessionController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
@DisplayName("LearningSessionController — REST adapter")
class LearningSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LearningSessionService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private LmsRoleAuthorityMapper roleAuthorityMapper;

    private UUID userId;
    private UUID tenantId;
    private UUID sessionUuid;
    private UUID assignmentUuid;
    private UUID unitUuid;
    private LearningSessionResponse stubResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        sessionUuid = UUID.randomUUID();
        assignmentUuid = UUID.randomUUID();
        unitUuid = UUID.randomUUID();

        stubResponse = new LearningSessionResponse(
            sessionUuid, 0L,
            new AssignmentRef(assignmentUuid,
                new TeacherRef(UUID.randomUUID(), "John", "Doe"),
                new CourseRef(UUID.randomUUID(), "MATH-101", "Algebra"),
                new SectionRef(UUID.randomUUID(), "A"),
                new PeriodRef(UUID.randomUUID(), "QUARTER", 1, "Q1",
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31))),
            new UnitRef(unitUuid, "Unit 1", 1),
            "Class 1", "Learn X",
            LocalDate.of(2026, 4, 1), 45, SessionStatus.PLANNED,
            new SessionContentDto("Ctx", List.of("Act"), List.of("Mat"), "Obs"),
            List.of(new CompetencyRef(UUID.randomUUID(), "C1", "Comp 1")),
            List.of(new CapacityRef(UUID.randomUUID(), "CAP1", "Capacity 1", UUID.randomUUID())),
            null, null, null,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private JwtAuthenticationToken authenticatedAdmin() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId, tenantId, "acme", "admin@acme.test");
        return new JwtAuthenticationToken(
            principal, "fake.token",
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Nested
    @DisplayName("GET /learning-sessions")
    class ListSessions {

        @Test
        @DisplayName("200 — lista sessions con filtros")
        void list() throws Exception {
            var listItem = new LearningSessionListItem(
                sessionUuid, 0L, "Class 1",
                LocalDate.of(2026, 4, 1), 45, SessionStatus.PLANNED,
                null, null, null,
                new AssignmentSummary(assignmentUuid, "John Doe", "MATH-101", "A"),
                new UnitSummary(unitUuid, "Unit 1", 1),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            given(service.list(any())).willReturn(List.of(listItem));

            mockMvc.perform(get("/v1/learning-sessions")
                    .with(authentication(authenticatedAdmin()))
                    .param("status", "PLANNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("POST /learning-sessions")
    class CreateSession {

        @Test
        @DisplayName("201 — crea y retorna response")
        void create() throws Exception {
            given(service.create(any())).willReturn(stubResponse);

            var req = new CreateLearningSessionRequest(
                assignmentUuid, unitUuid, "Class 1", "Learn X",
                LocalDate.of(2026, 4, 1), 45,
                new SessionContentDto("Ctx", List.of("Act"), List.of("Mat"), "Obs"),
                List.of(), List.of()
            );

            mockMvc.perform(post("/v1/learning-sessions")
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("GET /learning-sessions/{publicUuid}")
    class GetSession {

        @Test
        @DisplayName("200 — retorna la session")
        void returnsSession() throws Exception {
            given(service.get(sessionUuid)).willReturn(stubResponse);

            mockMvc.perform(get("/v1/learning-sessions/{publicUuid}", sessionUuid)
                    .with(authentication(authenticatedAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("PUT /learning-sessions/{publicUuid}")
    class UpdateSession {

        @Test
        @DisplayName("200 — actualiza y retorna response")
        void update() throws Exception {
            given(service.update(eq(sessionUuid), any())).willReturn(stubResponse);

            var patch = new UpdateLearningSessionRequest(null, "Updated", null, null, null, null, null, null);

            mockMvc.perform(put("/v1/learning-sessions/{publicUuid}", sessionUuid)
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("DELETE /learning-sessions/{publicUuid}")
    class DeleteSession {

        @Test
        @DisplayName("204 — borra la session")
        void removesSession() throws Exception {
            mockMvc.perform(delete("/v1/learning-sessions/{publicUuid}", sessionUuid)
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf()))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /learning-sessions/{publicUuid}/start")
    class StartSession {

        @Test
        @DisplayName("200 — transiciona a IN_PROGRESS")
        void start() throws Exception {
            given(service.start(eq(sessionUuid), any())).willReturn(stubResponse);
            mockMvc.perform(post("/v1/learning-sessions/{publicUuid}/start", sessionUuid)
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new LifecycleRequest(0L, null))))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /learning-sessions/{publicUuid}/complete")
    class CompleteSession {

        @Test
        @DisplayName("200 — transiciona a COMPLETED")
        void complete() throws Exception {
            given(service.complete(eq(sessionUuid), any())).willReturn(stubResponse);
            mockMvc.perform(post("/v1/learning-sessions/{publicUuid}/complete", sessionUuid)
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new LifecycleRequest(0L, null))))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /learning-sessions/{publicUuid}/cancel")
    class CancelSession {

        @Test
        @DisplayName("200 — cancela la session")
        void cancel() throws Exception {
            given(service.cancel(eq(sessionUuid), any())).willReturn(stubResponse);
            mockMvc.perform(post("/v1/learning-sessions/{publicUuid}/cancel", sessionUuid)
                    .with(authentication(authenticatedAdmin()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new LifecycleRequest(0L, "reason"))))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /teacher-assignments/{a}/sessions")
    class ListByAssignment {

        @Test
        @DisplayName("200 — lista sessions por assignment")
        void listByAssignment() throws Exception {
            var listItem = new LearningSessionListItem(
                sessionUuid, 0L, "Class 1",
                LocalDate.of(2026, 4, 1), 45, SessionStatus.PLANNED,
                null, null, null,
                new AssignmentSummary(assignmentUuid, "John Doe", "MATH-101", "A"),
                new UnitSummary(unitUuid, "Unit 1", 1),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            given(service.listByAssignment(assignmentUuid)).willReturn(List.of(listItem));
            mockMvc.perform(get("/v1/teacher-assignments/{a}/sessions", assignmentUuid)
                    .with(authentication(authenticatedAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("GET /academic/units/{u}/sessions")
    class ListByUnit {

        @Test
        @DisplayName("200 — lista sessions por unit")
        void listByUnit() throws Exception {
            var listItem = new LearningSessionListItem(
                sessionUuid, 0L, "Class 1",
                LocalDate.of(2026, 4, 1), 45, SessionStatus.PLANNED,
                null, null, null,
                new AssignmentSummary(assignmentUuid, "John Doe", "MATH-101", "A"),
                new UnitSummary(unitUuid, "Unit 1", 1),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            given(service.listByUnit(unitUuid)).willReturn(List.of(listItem));
            mockMvc.perform(get("/v1/academic/units/{u}/sessions", unitUuid)
                    .with(authentication(authenticatedAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Class 1"));
        }
    }

    @Nested
    @DisplayName("401 — sin autenticación")
    class Unauthenticated {

        @Test
        @DisplayName("GET /learning-sessions retorna 401")
        void list() throws Exception {
            mockMvc.perform(get("/v1/learning-sessions"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /learning-sessions retorna 401")
        void create() throws Exception {
            mockMvc.perform(post("/v1/learning-sessions")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
