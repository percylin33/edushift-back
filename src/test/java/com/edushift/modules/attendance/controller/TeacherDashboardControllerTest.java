package com.edushift.modules.attendance.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.attendance.dto.TeacherDashboardResponse;
import com.edushift.modules.attendance.service.TeacherDashboardService;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherDashboardController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class TeacherDashboardControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TeacherDashboardService teacherDashboardService;

    @Test
    void get_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/dashboard/teacher"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void get_returnsOk_whenTeacher() throws Exception {
        given(teacherDashboardService.getForCurrentTeacher()).willReturn(TeacherDashboardResponse.empty());

        mockMvc.perform(get("/v1/dashboard/teacher").with(auth(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    void get_returnsOk_whenTenantAdmin() throws Exception {
        given(teacherDashboardService.getForCurrentTeacher()).willReturn(TeacherDashboardResponse.empty());

        mockMvc.perform(get("/v1/dashboard/teacher").with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }
}
