package com.edushift.modules.notifications.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tenants.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailLayoutRendererTest {

	@Mock TenantRepository tenantRepository;

	private EmailLayoutRenderer renderer;

	@BeforeEach
	void setUp() {
		EmailBrandingResolver brandingResolver = new EmailBrandingResolver(tenantRepository);
		ReflectionTestUtils.setField(brandingResolver, "defaultLogoUrl", "");
		renderer = new EmailLayoutRenderer(brandingResolver);
	}

	@Test
	void wrapIncludesBrandingAndBody() {
		EmailBrandingContext ctx = new EmailBrandingContext(
				"Colegio Demo", "https://cdn.example.com/logo.png", "#0e7490", "demo");
		String html = renderer.wrap("<p>Hola mundo</p>", "Preview text", ctx);

		assertThat(html).contains("Colegio Demo");
		assertThat(html).contains("https://cdn.example.com/logo.png");
		assertThat(html).contains("#0e7490");
		assertThat(html).contains("Hola mundo");
		assertThat(html).contains("Preview text");
		assertThat(html).contains("max-width:600px");
	}

	@Test
	void wrapWithoutLogoFallsBackToTextBrand() {
		EmailBrandingContext ctx = EmailBrandingContext.edushiftDefault(null);
		String html = renderer.wrap("<p>Body</p>", null, ctx);
		assertThat(html).contains("EduShift");
		assertThat(html).doesNotContain("<img ");
	}

	@Test
	void renderFragmentSubstitutesPlaceholders() {
		String fragment = renderer.renderFragment("invitation-school.html", java.util.Map.of(
				"setupUrl", "https://app.example/setup?t=abc",
				"expiry", "01/01/2030 12:00",
				"primaryColor", "#112233"));
		assertThat(fragment).contains("https://app.example/setup?t=abc");
		assertThat(fragment).contains("#112233");
		assertThat(fragment).contains("Configurar mi colegio");
	}
}
