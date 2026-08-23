package com.edushift.modules.admin.dashboard;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDashboardController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class AdminDashboardControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AdminDashboardService dashboardService;

    @Test
    void getKpis_returnsOk_whenSuperAdmin() throws Exception {
        given(dashboardService.getKpis())
                .willReturn(Mockito.mock(AdminDashboardService.DashboardKpis.class));

        mockMvc.perform(get("/v1/admin/dashboard/kpis").with(auth(superAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void getKpis_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/admin/dashboard/kpis"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getKpis_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/admin/dashboard/kpis").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
