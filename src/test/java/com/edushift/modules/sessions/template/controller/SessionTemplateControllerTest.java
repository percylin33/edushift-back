package com.edushift.modules.sessions.template.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.sessions.template.dto.AdoptSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplateResponse;
import com.edushift.modules.sessions.template.dto.SessionTemplatesOrganizedResponse;
import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import com.edushift.modules.sessions.template.service.SessionTemplateService;
import com.edushift.test.AbstractControllerTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionTemplateController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class SessionTemplateControllerTest extends AbstractControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@MockitoBean SessionTemplateService service;

	@Test
	void list_returnsOk_whenTeacher() throws Exception {
		given(service.listMine()).willReturn(List.of());

		mockMvc.perform(get("/v1/session-templates").with(auth(teacher())))
				.andExpect(status().isOk());
	}

	@Test
	void organized_returnsOk_whenAdmin() throws Exception {
		given(service.listOrganized()).willReturn(new SessionTemplatesOrganizedResponse(List.of()));

		mockMvc.perform(get("/v1/session-templates/organized").with(auth(tenantAdmin())))
				.andExpect(status().isOk());
	}

	@Test
	void catalog_returnsOk_whenTeacher() throws Exception {
		given(service.listCatalog()).willReturn(List.of());

		mockMvc.perform(get("/v1/session-templates/catalog").with(auth(teacher())))
				.andExpect(status().isOk());
	}

	@Test
	void adopt_returnsCreated_whenAdmin() throws Exception {
		UUID id = UUID.randomUUID();
		given(service.adopt(eq(id), any())).willReturn(sample(id));

		mockMvc.perform(post("/v1/session-templates/" + id + "/adopt")
						.with(auth(tenantAdmin()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new AdoptSessionTemplateRequest(true))))
				.andExpect(status().isCreated());
		verify(service).adopt(eq(id), any());
	}

	@Test
	void adopt_returns4xx_whenTeacher() throws Exception {
		mockMvc.perform(post("/v1/session-templates/" + UUID.randomUUID() + "/adopt")
						.with(auth(teacher()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void setDefault_returnsOk_whenAdmin() throws Exception {
		UUID id = UUID.randomUUID();
		given(service.setDefault(id)).willReturn(sample(id));

		mockMvc.perform(put("/v1/session-templates/" + id + "/default").with(auth(tenantAdmin())))
				.andExpect(status().isOk());
	}

	@Test
	void list_returns4xx_whenUnauthenticated() throws Exception {
		mockMvc.perform(get("/v1/session-templates"))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void list_returns4xx_whenStudent() throws Exception {
		mockMvc.perform(get("/v1/session-templates").with(auth(student())))
				.andExpect(status().is4xxClientError());
	}

	private static SessionTemplateResponse sample(UUID id) {
		return new SessionTemplateResponse(
				id, "minedu-standard-tenant", "Estándar", "desc",
				Map.of(), false, SessionTemplateFamily.STANDARD, UUID.randomUUID(), true, Instant.now());
	}
}
