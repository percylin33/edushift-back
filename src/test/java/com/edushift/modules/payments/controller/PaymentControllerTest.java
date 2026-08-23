package com.edushift.modules.payments.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.payments.service.PaymentService;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class PaymentControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentService paymentService;

    @Test
    void listInvoices_returnsOk_whenAuthenticated() throws Exception {
        given(paymentService.listInvoicesForCaller(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(java.util.List.of()));
        // PaymentController resolves caller via CurrentUserProvider
        mockMvc.perform(get("/v1/payments/invoices").with(auth(parent())))
                .andExpect(status().isOk());
    }

    @Test
    void listInvoices_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/payments/invoices"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void receipt_returnsPdf_whenAuthenticated() throws Exception {
        java.util.UUID invoice = java.util.UUID.randomUUID();
        given(paymentService.getReceiptPdf(invoice)).willReturn(new byte[] {1, 2, 3});
        mockMvc.perform(get("/v1/payments/invoices/{uuid}/receipt", invoice).with(auth(parent())))
                .andExpect(status().isOk());
    }
}
