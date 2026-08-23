package com.edushift.modules.me.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.me.service.MeAcademicService;
import com.edushift.modules.me.service.MeSelfService;
import com.edushift.modules.me.service.MeService;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class MeControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean MeService meService;
    @MockitoBean MeAcademicService meAcademicService;
    @MockitoBean MeSelfService meSelfService;

    @Test
    void profile_returnsOk_whenAuthenticated() throws Exception {
        given(meService.getProfile()).willReturn(null);
        mockMvc.perform(get("/v1/me/profile").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void sections_returnsOk_whenAuthenticated() throws Exception {
        given(meAcademicService.listMySections()).willReturn(List.of());
        mockMvc.perform(get("/v1/me/sections").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void section_returnsOk_whenAuthenticated() throws Exception {
        given(meAcademicService.getMySection(org.mockito.ArgumentMatchers.any(UUID.class)))
                .willReturn(null);
        mockMvc.perform(get("/v1/me/sections/{uuid}", UUID.randomUUID()).with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void grades_returnsOk_whenAuthenticated() throws Exception {
        given(meAcademicService.listMyGrades()).willReturn(List.of());
        mockMvc.perform(get("/v1/me/grades").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void attendance_returnsOk_whenAuthenticated() throws Exception {
        given(meSelfService.listMyAttendance()).willReturn(List.of());
        mockMvc.perform(get("/v1/me/attendance").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void qr_returnsOk_whenAuthenticated() throws Exception {
        given(meSelfService.getMyQr()).willReturn(null);
        mockMvc.perform(get("/v1/me/qr").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void payments_returnsOk_whenAuthenticated() throws Exception {
        given(meSelfService.listMyPayments()).willReturn(List.of());
        mockMvc.perform(get("/v1/me/payments").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void schedule_returnsOk_whenAuthenticated() throws Exception {
        given(meSelfService.listMySchedule(null)).willReturn(ScheduleWeekView.of(List.of(), List.of()));
        mockMvc.perform(get("/v1/me/schedule").with(auth(student())))
                .andExpect(status().isOk());
    }
}
