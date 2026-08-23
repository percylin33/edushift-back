package com.edushift.modules.payments.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.payments.service.PaymentConceptService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminPaymentController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class AdminPaymentControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentConceptService conceptService;
    @MockitoBean AdminPaymentService adminPaymentService;

    private static JwtAuthenticationToken paymentAdmin() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(ANY_USER, ANY_TENANT, "pay", "pay@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_PAYMENT_ADMIN")));
    }

    @Test
    void listConcepts_returnsOk_whenAuthorized() throws Exception {
        given(conceptService.list(any())).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/admin/payments/concepts").with(auth(paymentAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void listConcepts_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/admin/payments/concepts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listConcepts_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/admin/payments/concepts").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
