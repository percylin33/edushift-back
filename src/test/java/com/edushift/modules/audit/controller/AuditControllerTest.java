package com.edushift.modules.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.audit.repository.AuditLogRepository;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class AuditControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditLogRepository auditLogRepository;

    @Test
    void search_returnsOk_whenSuperAdmin() throws Exception {
        given(auditLogRepository.search(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/v1/admin/audit").with(auth(superAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void search_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/admin/audit"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void search_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/admin/audit").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
