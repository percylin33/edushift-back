package com.edushift.modules.disclosures.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.disclosures.dto.DisclosurePackageResponse;
import com.edushift.modules.disclosures.entity.DisclosureStatus;
import com.edushift.modules.disclosures.service.AnnualDisclosureService;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.test.AbstractControllerTest;
import com.edushift.test.EdushiftWebMvcTestConfig;
import com.edushift.test.TestSecurityConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnnualDisclosureController.class)
@Import({EdushiftWebMvcTestConfig.class, TestSecurityConfig.class})
class AnnualDisclosureControllerSecurityTest extends AbstractControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AnnualDisclosureService service;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void tenantAdminWithReadCanLoadCatalogWithoutManagePermission() throws Exception {
		mockMvc.perform(get(url("/disclosures/catalog"))
						.with(auth(principal(List.of("ROLE_TENANT_ADMIN", "LMS_DISCLOSURE_READ")))))
				.andExpect(status().isOk());
	}

	@Test
	void userWithoutReadCannotLoadCatalog() throws Exception {
		mockMvc.perform(get(url("/disclosures/catalog"))
						.with(auth(principal(List.of("ROLE_TENANT_ADMIN")))))
				.andExpect(status().isForbidden());
	}

	@Test
	void staffWithSubmitCanSendDraftForReview() throws Exception {
		UUID yearUuid = UUID.randomUUID();
		given(service.submitForReview(yearUuid)).willReturn(new DisclosurePackageResponse(
				UUID.randomUUID(), yearUuid, "2027", DisclosureStatus.IN_REVIEW, 1,
				null, null, null, null, null, null, null, null, null, null));

		mockMvc.perform(post(url("/disclosures/years/" + yearUuid + "/submit-review"))
						.with(auth(principal(List.of(
								"ROLE_STAFF",
								"LMS_DISCLOSURE_READ",
								"LMS_DISCLOSURE_MANAGE",
								"LMS_DISCLOSURE_SUBMIT")))))
				.andExpect(status().isOk());

		then(service).should().submitForReview(yearUuid);
	}

	@Test
	void tenantAdminCannotSendDraftForReviewWithoutSubmitPermission() throws Exception {
		UUID yearUuid = UUID.randomUUID();

		mockMvc.perform(post(url("/disclosures/years/" + yearUuid + "/submit-review"))
						.with(auth(principal(List.of(
								"ROLE_TENANT_ADMIN",
								"LMS_DISCLOSURE_READ",
								"LMS_DISCLOSURE_REVIEW",
								"LMS_DISCLOSURE_PUBLISH",
								"LMS_DISCLOSURE_SUBMIT")))))
				.andExpect(status().isForbidden());

		then(service).should(never()).submitForReview(yearUuid);
	}

	@Test
	void tenantAdminCannotSubmitEvenWhenAlsoAssignedStaffRole() throws Exception {
		UUID yearUuid = UUID.randomUUID();

		mockMvc.perform(post(url("/disclosures/years/" + yearUuid + "/submit-review"))
						.with(auth(principal(List.of(
								"ROLE_TENANT_ADMIN",
								"ROLE_STAFF",
								"LMS_DISCLOSURE_READ",
								"LMS_DISCLOSURE_SUBMIT")))))
				.andExpect(status().isForbidden());

		then(service).should(never()).submitForReview(yearUuid);
	}

	@Test
	void tenantAdminCannotEditEvenIfAnOldTokenContainsManagePermission() throws Exception {
		UUID yearUuid = UUID.randomUUID();

		mockMvc.perform(put(url("/disclosures/years/" + yearUuid))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":{\"fields\":{},\"attachments\":[]}}")
						.with(auth(principal(List.of(
								"ROLE_TENANT_ADMIN",
								"LMS_DISCLOSURE_READ",
								"LMS_DISCLOSURE_MANAGE")))))
				.andExpect(status().isForbidden());

		then(service).should(never()).save(any(), any());
	}
}
