package com.edushift.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendLinksTest {

	private final FrontendLinks links = new FrontendLinks("http://localhost:4201/");

	@Test
	void schoolSetupHasTokenAndNoTenant() {
		assertThat(links.schoolSetup("abc"))
				.isEqualTo("http://localhost:4201/auth/setup-school?token=abc");
	}

	@Test
	void userInvitationPinsTheSchool() {
		assertThat(links.userInvitation("tok-1", "Colegio-De-Percy"))
				.isEqualTo("http://localhost:4201/invitation/tok-1?tenant=colegio-de-percy");
	}

	@Test
	void passwordResetPinsSchoolAndToken() {
		assertThat(links.passwordReset("jwt.token", "tecnosur"))
				.isEqualTo("http://localhost:4201/auth/reset-password?tenant=tecnosur&token=jwt.token");
	}

	@Test
	void schoolLoginPinsTheSchool() {
		assertThat(links.schoolLogin("demo"))
				.isEqualTo("http://localhost:4201/auth/login?tenant=demo");
	}
}
