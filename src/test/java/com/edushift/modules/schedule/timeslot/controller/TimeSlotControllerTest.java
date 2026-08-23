package com.edushift.modules.schedule.timeslot.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.test.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimeSlotController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class TimeSlotControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TimeSlotService service;

    @Test
    void listTimeSlots_returnsOk_whenTenantAdmin() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();
        given(service.listSlotsOfAssignment(assignmentUuid)).willReturn(java.util.List.of());

        mockMvc.perform(get("/v1/teacher-assignments/{a}/time-slots", assignmentUuid)
                        .with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void listTimeSlots_returns4xx_whenUnauthenticated() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();
        mockMvc.perform(get("/v1/teacher-assignments/{a}/time-slots", assignmentUuid))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void teacherSchedule_returns4xx_whenStudent() throws Exception {
        UUID teacherUuid = UUID.randomUUID();
        mockMvc.perform(get("/v1/teachers/{t}/schedule", teacherUuid)
                        .with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
