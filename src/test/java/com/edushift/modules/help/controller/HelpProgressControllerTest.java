package com.edushift.modules.help.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.help.service.HelpProgressService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HelpProgressController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class HelpProgressControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean HelpProgressService service;

    @Test
    void getProgress_returnsOk_whenAuthenticated() throws Exception {
        given(service.resolveInternalUserId(any())).willReturn(UUID.randomUUID());
        given(service.getProgress(any(), any(), anyString(), anyString())).willReturn(List.of());

        mockMvc.perform(get("/v1/help/progress/TENANT_ADMIN/03-autoevaluacion.md")
                        .with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void getProgress_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/help/progress/TENANT_ADMIN/03-autoevaluacion.md"))
                .andExpect(status().is4xxClientError());
    }
}
