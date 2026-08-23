package com.edushift.modules.academic.classrooms.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.academic.classrooms.service.ClassroomService;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClassroomController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class ClassroomControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ClassroomService service;

    @Test
    void list_returnsOk_whenTenantAdmin() throws Exception {
        given(service.list(any())).willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/v1/academic/classrooms").with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/academic/classrooms"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void list_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/academic/classrooms").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
