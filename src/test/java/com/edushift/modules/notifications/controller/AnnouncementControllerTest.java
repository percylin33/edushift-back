package com.edushift.modules.notifications.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.service.AnnouncementService;
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

@WebMvcTest(AnnouncementController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class AnnouncementControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AnnouncementService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    private Announcement stub(UUID publicUuid) {
        Announcement a = new Announcement();
        a.setPublicUuid(publicUuid);
        a.setAuthorUserId(ANY_USER);
        a.setTitle("Reunión");
        a.setBodyHtml("<p>Saludos</p>");
        a.setAudienceType(Announcement.AudienceType.SCHOOL);
        a.setStatus(Announcement.Status.PUBLISHED);
        return a;
    }

    @Test
    void listPublished_returnsOk_whenAuthenticated() throws Exception {
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(service.listPublishedForUser(any(), anyInt())).willReturn(List.of());

        mockMvc.perform(get("/v1/announcements").with(auth(student())))
                .andExpect(status().isOk());
    }

    @Test
    void listPublished_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/announcements"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getOne_returnsOk_whenAuthenticated() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.get(id)).willReturn(stub(id));

        mockMvc.perform(get("/v1/announcements/{id}", id).with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void markRead_returnsOk_whenAuthenticated() throws Exception {
        UUID id = UUID.randomUUID();
        given(currentUserProvider.currentUserId()).willReturn(Optional.of(ANY_USER));
        given(service.markRead(id, ANY_USER)).willReturn(true);

        mockMvc.perform(post("/v1/announcements/{id}/read", id).with(auth(parent())).with(csrf()))
                .andExpect(status().isOk());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
