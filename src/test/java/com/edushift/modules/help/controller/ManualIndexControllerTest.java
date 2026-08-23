package com.edushift.modules.help.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.help.service.ManualIndexService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManualIndexController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class ManualIndexControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ManualIndexService service;

    @Test
    void index_returnsOk_whenAuthenticated() throws Exception {
        // Production is public; TestSecurityConfig is intentionally omitted here,
        // but default @WebMvcTest security still expects a principal in this slice.
        given(service.getIndex()).willReturn(List.of());

        mockMvc.perform(get("/v1/help/manuals").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void index_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/help/manuals"))
                .andExpect(status().is4xxClientError());
    }
}
