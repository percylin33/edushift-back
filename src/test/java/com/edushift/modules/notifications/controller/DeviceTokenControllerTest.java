package com.edushift.modules.notifications.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.notifications.service.DeviceTokenService;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceTokenController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class DeviceTokenControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DeviceTokenService service;

    @Test
    void register_returnsOk_whenAuthenticated() throws Exception {
        doNothing().when(service).register(any());

        mockMvc.perform(post("/v1/notifications/devices")
                        .with(csrf())
                        .with(auth(student()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abcdefghijklmnop\",\"platform\":\"WEB\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void register_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/v1/notifications/devices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abcdefghijklmnop\",\"platform\":\"WEB\"}"))
                .andExpect(status().is4xxClientError());
    }
}
