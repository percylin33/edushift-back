package com.edushift.modules.teachers.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.test.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherBulkImportController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class TeacherBulkImportControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BulkImportService service;

    @Test
    void getJob_returnsOk_whenTenantAdmin() throws Exception {
        given(service.getJob(any())).willReturn(Mockito.mock(BulkImportJobResponse.class));

        mockMvc.perform(get("/v1/teachers/bulk-import/{id}", UUID.randomUUID())
                        .with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void getJob_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/teachers/bulk-import/{id}", UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getJob_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/teachers/bulk-import/{id}", UUID.randomUUID())
                        .with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
