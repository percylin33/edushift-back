package com.edushift.modules.files.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StoredObject.buildKey")
class StoredObjectTest {

	@Test
	@DisplayName("layout includes env, slug-tenantId, module, year, month, uuid")
	void layout() {
		UUID tenant = UUID.fromString("01a012e7-f05b-7903-b59d-a1be8ea3ad73");
		UUID file = UUID.fromString("731804d5-e129-41fc-8315-ddcaa5aaba04");
		Instant at = Instant.parse("2026-09-02T14:00:00Z");

		String key = StoredObject.buildKey(
				"dev", tenant, "tecnosur", "submissions", at, file, "pdf");

		assertThat(key).isEqualTo(
				"envs/dev/tenants/tecnosur-01a012e7-f05b-7903-b59d-a1be8ea3ad73/submissions/2026/09/731804d5-e129-41fc-8315-ddcaa5aaba04.pdf");
	}

	@Test
	@DisplayName("blank slug falls back to tenantId-only folder")
	void blankSlugFallsBackToUuid() {
		UUID tenant = UUID.fromString("01a012e7-f05b-7903-b59d-a1be8ea3ad73");
		UUID file = UUID.fromString("731804d5-e129-41fc-8315-ddcaa5aaba04");
		Instant at = Instant.parse("2026-09-02T14:00:00Z");

		String key = StoredObject.buildKey("dev", tenant, null, "materials", at, file, "png");

		assertThat(key).isEqualTo(
				"envs/dev/tenants/01a012e7-f05b-7903-b59d-a1be8ea3ad73/materials/2026/09/731804d5-e129-41fc-8315-ddcaa5aaba04.png");
	}

	@Test
	@DisplayName("sanitizeTenantSlug strips unsafe characters")
	void sanitizeSlug() {
		assertThat(StoredObject.sanitizeTenantSlug("Colegio San José"))
				.isEqualTo("colegio-san-jos");
		assertThat(StoredObject.tenantFolder("tecnosur", UUID.fromString("01a012e7-f05b-7903-b59d-a1be8ea3ad73")))
				.isEqualTo("tecnosur-01a012e7-f05b-7903-b59d-a1be8ea3ad73");
	}

	@Test
	@DisplayName("rejects unknown module")
	void rejectsUnknownModule() {
		assertThatThrownBy(() -> StoredObject.buildKey(
				"dev", UUID.randomUUID(), "demo", "evil", Instant.now(), UUID.randomUUID(), "bin"))
				.isInstanceOf(com.edushift.shared.exception.BadRequestException.class);
	}

	@Test
	@DisplayName("sanitizeEnv maps production → prod")
	void sanitizeEnv() {
		assertThat(StoredObject.sanitizeEnv("production")).isEqualTo("prod");
		assertThat(StoredObject.sanitizeEnv("local")).isEqualTo("dev");
		assertThat(StoredObject.sanitizeEnv("staging")).isEqualTo("staging");
	}
}
