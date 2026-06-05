package com.edushift.modules.tenants.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tenants.dto.BrandingDto;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantMapper}. No Spring context — the mapper is a
 * plain bean with no dependencies, so we instantiate it directly and pin
 * the conversions field-by-field.
 *
 * <p>Three flavours of behavior live here:
 * <ul>
 *   <li>{@code Entity → DTO}: {@link #toSummary} and {@link #toResponse}
 *       projections, including the JSON-bag {@code branding} extraction.</li>
 *   <li>{@code DTO → Entity}: {@link TenantMapper#applyUpdate} merge
 *       semantics — null-as-noop on scalars, field-level merge on
 *       {@code branding}, wholesale replacement on opaque maps.</li>
 *   <li>Forward-compat resilience: an unknown branding key in the
 *       persisted column survives a roundtrip-through-merge so future
 *       schema additions don't get silently truncated.</li>
 * </ul>
 */
class TenantMapperTest {

	private final TenantMapper mapper = new TenantMapper();

	// ===========================================================================
	// toSummary
	// ===========================================================================

	@Nested
	@DisplayName("toSummary — public projection")
	class ToSummary {

		@Test
		@DisplayName("returns publicUuid + identity + status + branding from a populated tenant")
		void mapsAllSummaryFields() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setBranding(brandingMap("#0F62FE", "https://cdn.test/logo.png", null, null));

			TenantSummary summary = mapper.toSummary(tenant);

			assertThat(summary.publicUuid()).isEqualTo(tenant.getPublicUuid());
			assertThat(summary.name()).isEqualTo("Demo Institution");
			assertThat(summary.slug()).isEqualTo("acme");
			assertThat(summary.status()).isEqualTo(TenantStatus.ACTIVE);
			assertThat(summary.branding().primaryColor()).isEqualTo("#0F62FE");
			assertThat(summary.branding().logoUrl()).isEqualTo("https://cdn.test/logo.png");
			assertThat(summary.branding().faviconUrl()).isNull();
			assertThat(summary.branding().loginBgUrl()).isNull();
		}

		@Test
		@DisplayName("yields an all-null BrandingDto when the entity has no branding column data")
		void emptyBrandingMapsToNullFields() {
			Tenant tenant = newTenant("blank", TenantStatus.ACTIVE);
			tenant.setBranding(null);

			TenantSummary summary = mapper.toSummary(tenant);

			assertThat(summary.branding()).isNotNull();
			assertThat(summary.branding().primaryColor()).isNull();
			assertThat(summary.branding().logoUrl()).isNull();
			assertThat(summary.branding().faviconUrl()).isNull();
			assertThat(summary.branding().loginBgUrl()).isNull();
		}

		@Test
		@DisplayName("ignores unknown branding keys (forward-compat: future fields don't leak through summary)")
		void unknownBrandingKeysAreSkipped() {
			Tenant tenant = newTenant("future", TenantStatus.ACTIVE);
			Map<String, Object> branding = brandingMap("#1e90ff", null, null, null);
			branding.put("unknownFutureKey", "ignored-value");
			tenant.setBranding(branding);

			TenantSummary summary = mapper.toSummary(tenant);

			assertThat(summary.branding().primaryColor()).isEqualTo("#1e90ff");
			// The DTO is the contract: anything not in BrandingDto is invisible to consumers.
		}

		@Test
		@DisplayName("non-string branding values are coerced to null (defensive against bad jsonb data)")
		void nonStringValuesBecomeNull() {
			Tenant tenant = newTenant("messy", TenantStatus.ACTIVE);
			Map<String, Object> branding = new HashMap<>();
			branding.put("primaryColor", 12345); // not a string
			tenant.setBranding(branding);

			TenantSummary summary = mapper.toSummary(tenant);

			assertThat(summary.branding().primaryColor()).isNull();
		}
	}

	// ===========================================================================
	// toResponse
	// ===========================================================================

	@Nested
	@DisplayName("toResponse — authenticated /me projection")
	class ToResponse {

		@Test
		@DisplayName("maps every operational field including settings + featureFlags + capacity caps")
		void mapsAllFields() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setCustomDomain("acme.example.com");
			tenant.setPlan(TenantPlan.PRO);
			Instant trialEnd = Instant.parse("2030-01-01T00:00:00Z");
			tenant.setTrialEndsAt(trialEnd);
			tenant.setBranding(brandingMap("#000000", null, null, null));
			tenant.setSettings(Map.of("locale", "es-PE"));
			tenant.setFeatureFlags(Map.of("ai.copilot", Boolean.TRUE));
			tenant.setMaxStudents(500);
			tenant.setMaxTeachers(50);

			TenantResponse response = mapper.toResponse(tenant);

			assertThat(response.publicUuid()).isEqualTo(tenant.getPublicUuid());
			assertThat(response.customDomain()).isEqualTo("acme.example.com");
			assertThat(response.plan()).isEqualTo(TenantPlan.PRO);
			assertThat(response.trialEndsAt()).isEqualTo(trialEnd);
			assertThat(response.settings()).containsEntry("locale", "es-PE");
			assertThat(response.featureFlags()).containsEntry("ai.copilot", Boolean.TRUE);
			assertThat(response.maxStudents()).isEqualTo(500);
			assertThat(response.maxTeachers()).isEqualTo(50);
			assertThat(response.branding().primaryColor()).isEqualTo("#000000");
		}

		@Test
		@DisplayName("returns defensive copies of settings / featureFlags so callers can't mutate entity state")
		void returnsDefensiveCopiesOfMaps() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			Map<String, Object> entitySettings = new HashMap<>();
			entitySettings.put("locale", "es-PE");
			tenant.setSettings(entitySettings);

			TenantResponse response = mapper.toResponse(tenant);
			response.settings().put("locale", "MUTATED");

			// Mutation on the response payload must not leak into the entity.
			assertThat(tenant.getSettings()).containsEntry("locale", "es-PE");
		}

		@Test
		@DisplayName("nulls in settings / featureFlags become empty maps (never null) to simplify front-end consumption")
		void nullMapsBecomeEmpty() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setSettings(null);
			tenant.setFeatureFlags(null);

			TenantResponse response = mapper.toResponse(tenant);

			assertThat(response.settings()).isEmpty();
			assertThat(response.featureFlags()).isEmpty();
		}
	}

	// ===========================================================================
	// applyUpdate
	// ===========================================================================

	@Nested
	@DisplayName("applyUpdate — partial merge semantics")
	class ApplyUpdate {

		@Test
		@DisplayName("an all-null patch is a no-op (every field stays as-is)")
		void allNullPatchIsNoOp() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setName("Original Name");
			tenant.setCustomDomain("orig.example.com");
			tenant.setMaxStudents(100);
			Map<String, Object> originalBranding = brandingMap("#000000", null, null, null);
			tenant.setBranding(originalBranding);

			UpdateTenantRequest patch = new UpdateTenantRequest(null, null, null, null, null, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getName()).isEqualTo("Original Name");
			assertThat(tenant.getCustomDomain()).isEqualTo("orig.example.com");
			assertThat(tenant.getMaxStudents()).isEqualTo(100);
			assertThat(tenant.getBranding()).isEqualTo(originalBranding);
		}

		@Test
		@DisplayName("scalar fields are overwritten when the patch carries non-null values")
		void scalarsAreOverwritten() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setName("Old Name");
			tenant.setMaxStudents(100);
			tenant.setMaxTeachers(10);

			UpdateTenantRequest patch = new UpdateTenantRequest(
					"New Name", null, null, null, null, 250, 25);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getName()).isEqualTo("New Name");
			assertThat(tenant.getMaxStudents()).isEqualTo(250);
			assertThat(tenant.getMaxTeachers()).isEqualTo(25);
		}

		@Test
		@DisplayName("customDomain is normalized (trim + lowercase) to match the partial unique index")
		void customDomainIsNormalized() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);

			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, "  ACME.Example.COM  ", null, null, null, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getCustomDomain()).isEqualTo("acme.example.com");
		}

		@Test
		@DisplayName("branding merge keeps untouched keys (sending only primaryColor preserves logoUrl)")
		void brandingMergeIsFieldLevel() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setBranding(brandingMap("#000000", "https://cdn.test/old.png", null, null));

			BrandingDto patchBranding = new BrandingDto("#FF6900", null, null, null);
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, patchBranding, null, null, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getBranding())
					.containsEntry("primaryColor", "#FF6900")
					.containsEntry("logoUrl", "https://cdn.test/old.png");
		}

		@Test
		@DisplayName("branding merge survives forward-compat keys already in the column")
		void brandingMergePreservesUnknownForwardCompatKeys() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			Map<String, Object> existing = new HashMap<>(brandingMap("#000000", null, null, null));
			existing.put("upcomingKey", "do-not-drop");
			tenant.setBranding(existing);

			BrandingDto patchBranding = new BrandingDto("#FFFFFF", null, null, null);
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, patchBranding, null, null, null, null);
			mapper.applyUpdate(patch, tenant);

			// The mapper is contract-driven: it should never silently delete keys
			// it doesn't know about. A future schema add should be backwards compatible.
			assertThat(tenant.getBranding())
					.containsEntry("primaryColor", "#FFFFFF")
					.containsEntry("upcomingKey", "do-not-drop");
		}

		@Test
		@DisplayName("branding merge bootstraps an empty map when the entity had no branding yet")
		void brandingMergeFromNullEntityBranding() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setBranding(null);

			BrandingDto patchBranding = new BrandingDto("#0F62FE", null, null, null);
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, patchBranding, null, null, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getBranding()).containsEntry("primaryColor", "#0F62FE");
		}

		@Test
		@DisplayName("settings are replaced wholesale — keys absent in the patch are dropped")
		void settingsAreReplacedWholesale() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setSettings(new LinkedHashMap<>(Map.of("locale", "es-PE", "currency", "PEN")));

			Map<String, Object> patchSettings = new LinkedHashMap<>(Map.of("locale", "en-US"));
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, null, patchSettings, null, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getSettings())
					.containsEntry("locale", "en-US")
					.doesNotContainKey("currency");
		}

		@Test
		@DisplayName("featureFlags are replaced wholesale, same as settings")
		void featureFlagsAreReplacedWholesale() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			tenant.setFeatureFlags(new LinkedHashMap<>(Map.of("ai.copilot", Boolean.TRUE,
					"reports.beta", Boolean.TRUE)));

			Map<String, Object> patchFlags = new LinkedHashMap<>(Map.of("ai.copilot", Boolean.FALSE));
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, null, null, patchFlags, null, null);
			mapper.applyUpdate(patch, tenant);

			assertThat(tenant.getFeatureFlags())
					.containsEntry("ai.copilot", Boolean.FALSE)
					.doesNotContainKey("reports.beta");
		}

		@Test
		@DisplayName("settings replacement uses a defensive copy (caller can't mutate entity state via the patch reference)")
		void settingsReplacementIsDefensive() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			Map<String, Object> patchSettings = new HashMap<>();
			patchSettings.put("locale", "es-PE");

			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null, null, patchSettings, null, null, null);
			mapper.applyUpdate(patch, tenant);

			patchSettings.put("locale", "MUTATED-AFTER-MERGE");

			// The entity must hold its own copy of the map state.
			assertThat(tenant.getSettings()).containsEntry("locale", "es-PE");
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static Tenant newTenant(String slug, TenantStatus status) {
		Tenant t = new Tenant();
		setIdViaReflection(t, UUID.randomUUID());
		t.setName("Demo Institution");
		t.setSlug(slug);
		t.setStatus(status);
		t.setPublicUuid(UUID.randomUUID());
		return t;
	}

	private static Map<String, Object> brandingMap(String primaryColor, String logoUrl,
			String faviconUrl, String loginBgUrl) {
		Map<String, Object> m = new HashMap<>();
		if (primaryColor != null) m.put("primaryColor", primaryColor);
		if (logoUrl != null) m.put("logoUrl", logoUrl);
		if (faviconUrl != null) m.put("faviconUrl", faviconUrl);
		if (loginBgUrl != null) m.put("loginBgUrl", loginBgUrl);
		return m;
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field found in " + entity.getClass());
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
