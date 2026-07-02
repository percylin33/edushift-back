package com.edushift.modules.reports.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.reports.dto.CreateReportRequest;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.job.ReportOutputCache;
import com.edushift.modules.reports.service.ReportService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ReportController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
    GlobalExceptionHandler.class,
    com.edushift.config.SecurityConfig.class,
    com.edushift.config.WebConfiguration.class
})
class ReportControllerTest {

    private static final String BASE = "/v1/reports";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ReportService reportService;
    @MockitoBean private CurrentUserProvider currentUserProvider;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private LmsRoleAuthorityMapper roleAuthorityMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(userId));
    }

    @AfterEach
    void tearDown() {
        ReportOutputCache.evict(UUID.randomUUID());
    }

    private static JwtAuthenticationToken userAuth(UUID userId) {
        var principal = new JwtAuthenticatedPrincipal(
            userId, UUID.randomUUID(), "acme", "u@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
            java.util.List.<GrantedAuthority>of());
    }

    private ReportJob stubJob(ReportType type, Format format, Status status, UUID publicUuid) {
        var job = new ReportJob();
        job.setPublicUuid(publicUuid);
        job.setReportType(type);
        job.setFormat(format);
        job.setStatus(status);
        job.setProgressPct(status == Status.DONE ? (short) 100 : (short) 0);
        job.setRequestedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return job;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // =========================================================================
    // POST /v1/reports — create
    // =========================================================================

    @Nested
    @DisplayName("POST /v1/reports")
    class Create {

        @Test
        @DisplayName("authenticated — 200 with publicUuid envelope")
        void happyPath() throws Exception {
            var publicUuid = UUID.randomUUID();
            var saved = stubJob(ReportType.GRADE_BOOK, Format.CSV, Status.PENDING, publicUuid);
            given(reportService.request(eq(userId), any(ReportType.class),
                    any(Format.class), any(), any()))
                .willReturn(saved);

            var body = new CreateReportRequest(
                ReportType.GRADE_BOOK, Format.CSV, "{}", "key-1");

            mockMvc.perform(post(BASE)
                    .with(csrf())
                    .with(authentication(userAuth(userId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUuid").value(publicUuid.toString()))
                .andExpect(jsonPath("$.data.reportType").value("GRADE_BOOK"))
                .andExpect(jsonPath("$.data.format").value("CSV"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("missing reportType — 400")
        void missingReportType() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(csrf())
                    .with(authentication(userAuth(userId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest());

            then(reportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("missing format — 400")
        void missingFormat() throws Exception {
            mockMvc.perform(post(BASE)
                    .with(csrf())
                    .with(authentication(userAuth(userId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reportType\":\"GRADE_BOOK\"}"))
                .andExpect(status().isBadRequest());

            then(reportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anonymous — 401 UnauthorizedException")
        void anonymous() throws Exception {
            given(currentUserProvider.currentUserId())
                .willReturn(Optional.empty());

            mockMvc.perform(post(BASE)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isUnauthorized());

            then(reportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("idem key reuse → service returns existing job (idempotency)")
        void idempotencyPassthrough() throws Exception {
            var publicUuid = UUID.randomUUID();
            var existing = stubJob(ReportType.ATTENDANCE_SUMMARY, Format.PDF,
                Status.DONE, publicUuid);
            existing.setProgressPct((short) 100);
            given(reportService.request(any(), any(), any(), any(), any()))
                .willReturn(existing);

            var body = new CreateReportRequest(
                ReportType.ATTENDANCE_SUMMARY, Format.PDF, "{}", "stable-key");

            mockMvc.perform(post(BASE)
                    .with(csrf())
                    .with(authentication(userAuth(userId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUuid").value(publicUuid.toString()))
                .andExpect(jsonPath("$.data.status").value("DONE"));
        }
    }

    // =========================================================================
    // GET /v1/reports — list
    // =========================================================================

    @Nested
    @DisplayName("GET /v1/reports")
    class List {

        @Test
        @DisplayName("authenticated — 200 with envelope and meta")
        void happyPath() throws Exception {
            var id1 = UUID.randomUUID();
            var id2 = UUID.randomUUID();
            Page<ReportJob> page = new PageImpl<>(
                java.util.List.of(
                    stubJob(ReportType.GRADE_BOOK, Format.CSV, Status.DONE, id1),
                    stubJob(ReportType.ATTENDANCE_SUMMARY, Format.PDF, Status.PENDING, id2)),
                PageRequest.of(0, 20), 2);
            given(reportService.listForUser(eq(userId), any())).willReturn(page);

            mockMvc.perform(get(BASE).with(authentication(userAuth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].publicUuid").value(id1.toString()))
                .andExpect(jsonPath("$.data[1].publicUuid").value(id2.toString()))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(0));
        }

        @Test
        @DisplayName("anonymous — 401")
        void anonymous() throws Exception {
            given(currentUserProvider.currentUserId())
                .willReturn(Optional.empty());

            mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());

            then(reportService).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // GET /v1/reports/{publicUuid}
    // =========================================================================

    @Nested
    @DisplayName("GET /v1/reports/{publicUuid}")
    class Get {

        @Test
        @DisplayName("existing — 200 with envelope")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.STUDENT_TRANSCRIPT, Format.XLSX,
                    Status.RUNNING, id));

            mockMvc.perform(get(BASE + "/{id}", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicUuid").value(id.toString()))
                .andExpect(jsonPath("$.data.format").value("XLSX"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        }

        @Test
        @DisplayName("unknown — 404")
        void notFound() throws Exception {
            var id = UUID.randomUUID();
            given(reportService.get(id))
                .willThrow(new NotFoundException("REPORT_JOB_NOT_FOUND", "missing"));

            mockMvc.perform(get(BASE + "/{id}", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // GET /v1/reports/{publicUuid}/download
    // =========================================================================

    @Nested
    @DisplayName("GET /v1/reports/{publicUuid}/download")
    class Download {

        @Test
        @DisplayName("DONE PDF — 200 application/pdf with attachment")
        void pdfDownload() throws Exception {
            var id = UUID.randomUUID();
            var bytes = "%PDF-1.4 fake".getBytes();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.GRADE_BOOK, Format.PDF, Status.DONE, id));
            ReportOutputCache.put(id, bytes, Format.PDF);

            mockMvc.perform(get(BASE + "/{id}/download", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                    org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                    org.hamcrest.Matchers.containsString(".pdf")));

            ReportOutputCache.evict(id);
        }

        @Test
        @DisplayName("DONE XLSX — 200 spreadsheetml content type")
        void xlsxDownload() throws Exception {
            var id = UUID.randomUUID();
            var bytes = "fake-xlsx".getBytes();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.ATTENDANCE_SUMMARY, Format.XLSX, Status.DONE, id));
            ReportOutputCache.put(id, bytes, Format.XLSX);

            mockMvc.perform(get(BASE + "/{id}/download", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition",
                    org.hamcrest.Matchers.containsString(".xlsx")));

            ReportOutputCache.evict(id);
        }

        @Test
        @DisplayName("DONE CSV — 200 text/csv")
        void csvDownload() throws Exception {
            var id = UUID.randomUUID();
            var bytes = "a,b,c\r\n1,2,3\r\n".getBytes();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.PERIOD_CLOSE, Format.CSV, Status.DONE, id));
            ReportOutputCache.put(id, bytes, Format.CSV);

            mockMvc.perform(get(BASE + "/{id}/download", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition",
                    org.hamcrest.Matchers.containsString(".csv")));

            ReportOutputCache.evict(id);
        }

        @Test
        @DisplayName("not DONE — 422 REPORT_NOT_READY")
        void notReady() throws Exception {
            var id = UUID.randomUUID();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.GRADE_BOOK, Format.CSV, Status.PENDING, id));

            mockMvc.perform(get(BASE + "/{id}/download", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("DONE but cache expired — 404 REPORT_OUTPUT_EXPIRED")
        void cacheExpired() throws Exception {
            var id = UUID.randomUUID();
            given(reportService.get(id))
                .willReturn(stubJob(ReportType.GRADE_BOOK, Format.CSV, Status.DONE, id));
            // Cache is intentionally empty.

            mockMvc.perform(get(BASE + "/{id}/download", id)
                    .with(authentication(userAuth(userId))))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("anonymous — 401")
        void anonymous() throws Exception {
            given(currentUserProvider.currentUserId())
                .willReturn(Optional.empty());

            mockMvc.perform(get(BASE + "/{id}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }
    }
}
