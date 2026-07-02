package com.edushift.modules.tasks.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tasks.service.TaskService;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean TaskService taskService;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService;
    @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;

    private static JwtAuthenticationToken teacher() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_TASK_CREATE"),
                        new SimpleGrantedAuthority("LMS_TASK_READ")));
    }

    private static JwtAuthenticationToken reader() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_TASK_READ")));
    }

    @Test
    void createHappyPath() throws Exception {
        mockMvc.perform(post("/sections/{sid}/tasks", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())).content("{}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void listTasksOfSection() throws Exception {
        mockMvc.perform(get("/sections/{sid}/tasks", UUID.randomUUID())
                        .with(authentication(reader())))
                .andExpect(status().isOk());
    }

    @Test
    void getTask() throws Exception {
        mockMvc.perform(get("/tasks/{id}", UUID.randomUUID())
                        .with(authentication(reader())))
                .andExpect(status().isOk());
    }

    @Test
    void patchHappyPath() throws Exception {
        mockMvc.perform(patch("/tasks/{id}", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .content("{\"title\":\"X\"}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTask() throws Exception {
        mockMvc.perform(delete("/tasks/{id}", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())))
                .andExpect(status().isNoContent());
    }

    @Test
    void createMissingRoleForbidden() throws Exception {
        mockMvc.perform(post("/sections/{sid}/tasks", UUID.randomUUID())
                        .with(csrf())
                        .with(authentication(new JwtAuthenticationToken(
                                new JwtAuthenticatedPrincipal(
                                        UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                                "t", List.<SimpleGrantedAuthority>of())))
                        .content("{}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAnonymousUnauthorized() throws Exception {
        mockMvc.perform(post("/sections/{sid}/tasks", UUID.randomUUID())
                        .with(csrf())
                        .content("{}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}