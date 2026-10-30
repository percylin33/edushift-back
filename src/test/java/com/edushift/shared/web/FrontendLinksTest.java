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
				.isEqualTo("http://colegiodepercy.localhost:4201/invitation/tok-1");
	}

	@Test
	void passwordResetPinsSchoolAndToken() {
		assertThat(links.passwordReset("jwt.token", "tecnosur"))
				.isEqualTo("http://tecnosur.localhost:4201/auth/reset-password?token=jwt.token");
	}

	@Test
	void schoolLoginPinsTheSchool() {
		assertThat(links.schoolLogin("demo"))
				.isEqualTo("http://demo.localhost:4201/auth/login");
	}
}
