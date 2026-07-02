package com.edushift.modules.tasks.submission.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tasks.submission.service.SubmissionService;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService;
    @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;

    private static JwtAuthenticationToken submitter() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_TASK_SUBMIT")));
    }

    private static JwtAuthenticationToken grader() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_TASK_GRADE")));
    }

    @Test
    void submitHappyPath() throws Exception {
        mockMvc.perform(post("/tasks/{tid}/submissions", UUID.randomUUID())
                        .with(csrf()).with(authentication(submitter()))
                        .content("{\"studentPublicUuid\":\"" + UUID.randomUUID() + "\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void listByTask() throws Exception {
        mockMvc.perform(get("/tasks/{tid}/submissions", UUID.randomUUID())
                        .with(authentication(grader())))
                .andExpect(status().isOk());
    }

    @Test
    void getMine() throws Exception {
        mockMvc.perform(get("/tasks/{tid}/submissions/me", UUID.randomUUID())
                        .with(authentication(submitter())))
                .andExpect(status().isOk());
    }

    @Test
    void gradeHappyPath() throws Exception {
        mockMvc.perform(patch("/submissions/{id}/grade", UUID.randomUUID())
                        .with(csrf()).with(authentication(grader()))
                        .content("{\"grade\":90,\"feedback\":\"ok\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void submitMissingRoleForbidden() throws Exception {
        mockMvc.perform(post("/tasks/{tid}/submissions", UUID.randomUUID())
                        .with(csrf())
                        .with(authentication(new JwtAuthenticationToken(
                                new JwtAuthenticatedPrincipal(
                                        UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                                "t", List.<SimpleGrantedAuthority>of())))
                        .content("{}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void gradeMissingRoleForbidden() throws Exception {
        mockMvc.perform(patch("/submissions/{id}/grade", UUID.randomUUID())
                        .with(csrf())
                        .with(authentication(new JwtAuthenticationToken(
                                new JwtAuthenticatedPrincipal(
                                        UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                                "t", List.<SimpleGrantedAuthority>of())))
                        .content("{\"grade\":50}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}