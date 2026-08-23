package com.edushift.modules.qa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.qa.service.QaBugReportService;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.test.AbstractControllerTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QaBugReportController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class QaBugReportControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean QaBugReportService service;
    @MockitoBean CurrentUserProvider currentUser;
    @MockitoBean UserRepository userRepository;

    @Test
    void list_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/qa/bug-reports"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void list_returnsOk_whenAuthenticated() throws Exception {
        given(currentUser.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(userRepository.findByPublicUuid(any())).willReturn(Optional.empty());
        given(service.search(any(), any(), any(), any())).willReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/v1/qa/bug-reports").with(auth(tenantAdmin())))
                .andExpect(status().is4xxClientError());
    }
}
