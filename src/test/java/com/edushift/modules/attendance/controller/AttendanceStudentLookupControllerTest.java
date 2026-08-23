package com.edushift.modules.attendance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.attendance.service.AttendanceStudentLookupService;
import com.edushift.modules.attendance.service.AttendanceStudentLookupService.Filter;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceStudentLookupController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class AttendanceStudentLookupControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceStudentLookupService service;

    @Test
    void lookup_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/attendance/students/lookup"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void lookup_returnsOk_whenTeacher() throws Exception {
        given(service.lookup(any(Filter.class), any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/v1/attendance/students/lookup").with(auth(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    void lookup_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/attendance/students/lookup").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
