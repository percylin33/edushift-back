package com.edushift.modules.family.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.family.service.FamilyService;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FamilyController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class FamilyControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FamilyService familyService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void children_returnsOk_whenParent() throws Exception {
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(familyService.listChildren(ANY_USER)).willReturn(List.of());
        mockMvc.perform(get("/v1/family/children").with(auth(parent())))
                .andExpect(status().isOk());
    }

    @Test
    void children_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/family/children"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void activities_returnsOk_whenParent() throws Exception {
        UUID child = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(familyService.getChildActivities(child, ANY_USER)).willReturn(List.of());
        mockMvc.perform(get("/v1/family/children/{uuid}/activities", child).with(auth(parent())))
                .andExpect(status().isOk());
    }

    @Test
    void payments_returnsOk_whenParent() throws Exception {
        UUID child = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(familyService.getChildPayments(child, ANY_USER)).willReturn(List.of());
        mockMvc.perform(get("/v1/family/children/{uuid}/payments", child).with(auth(parent())))
                .andExpect(status().isOk());
    }

    @Test
    void schedule_returnsOk_whenParent() throws Exception {
        UUID child = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(familyService.getChildSchedule(child, ANY_USER, null))
                .willReturn(ScheduleWeekView.of(List.of(), List.of()));
        mockMvc.perform(get("/v1/family/children/{uuid}/schedule", child).with(auth(parent())))
                .andExpect(status().isOk());
    }
}
