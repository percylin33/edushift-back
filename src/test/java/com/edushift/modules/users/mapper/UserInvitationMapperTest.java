package com.edushift.modules.users.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.entity.UserInvitation;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserInvitationMapper}. Status derivation is the
 * piece worth pinning — the mapper itself is field-by-field plumbing.
 */
class UserInvitationMapperTest {

	private final UserInvitationMapper mapper = new UserInvitationMapper();

	private static final Instant NOW = Instant.parse("2026-06-04T12:00:00Z");

	// ===========================================================================
	// deriveStatus
	// ===========================================================================

	@Nested
	@DisplayName("deriveStatus — lifecycle from timestamps")
	class DeriveStatus {

		@Test
		@DisplayName("not-yet-expired, not accepted, not cancelled → PENDING")
		void pendingPath() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.plusSeconds(60));

			assertThat(mapper.deriveStatus(inv, NOW)).isEqualTo(InvitationStatus.PENDING);
		}

		@Test
		@DisplayName("acceptedAt set → ACCEPTED (precedence over expiry)")
		void acceptedTakesPrecedenceOverExpiry() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.minusSeconds(60));   // already expired
			inv.markAccepted(NOW.minusSeconds(120));

			assertThat(mapper.deriveStatus(inv, NOW)).isEqualTo(InvitationStatus.ACCEPTED);
		}

		@Test
		@DisplayName("cancelledAt set (and not accepted) → CANCELLED")
		void cancelledPath() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.plusSeconds(60));
			inv.markCancelled(NOW.minusSeconds(30));

			assertThat(mapper.deriveStatus(inv, NOW)).isEqualTo(InvitationStatus.CANCELLED);
		}

		@Test
		@DisplayName("past expiresAt, not accepted, not cancelled → EXPIRED")
		void expiredPath() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.minusSeconds(1));

			assertThat(mapper.deriveStatus(inv, NOW)).isEqualTo(InvitationStatus.EXPIRED);
		}

		@Test
		@DisplayName("expiresAt exactly equals now → EXPIRED (boundary is inclusive)")
		void expiredBoundaryIsInclusive() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW);

			assertThat(mapper.deriveStatus(inv, NOW)).isEqualTo(InvitationStatus.EXPIRED);
		}
	}

	// ===========================================================================
	// toResponse / toResponseWithToken
	// ===========================================================================

	@Nested
	@DisplayName("projection variants")
	class Projections {

		@Test
		@DisplayName("withToken includes the token verbatim")
		void withTokenIncludesToken() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.plusSeconds(60));
			inv.setToken("super-secret-token-1234567890abcdef");

			InvitationResponse response = mapper.toResponseWithToken(inv, NOW);

			assertThat(response.token()).isEqualTo("super-secret-token-1234567890abcdef");
			assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
		}

		@Test
		@DisplayName("toResponse strips the token (list endpoints must not re-expose it)")
		void listVariantStripsToken() {
			UserInvitation inv = newInvitation();
			inv.setExpiresAt(NOW.plusSeconds(60));
			inv.setToken("super-secret-token-1234567890abcdef");

			InvitationResponse response = mapper.toResponse(inv, NOW);

			assertThat(response.token()).isNull();
			assertThat(response.email()).isEqualTo("ada@acme.test");
			assertThat(response.firstName()).isEqualTo("Ada");
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static UserInvitation newInvitation() {
		UserInvitation inv = new UserInvitation();
		setIdViaReflection(inv, UUID.randomUUID());
		inv.setPublicUuid(UUID.randomUUID());
		inv.setEmail("ada@acme.test");
		inv.setFirstName("Ada");
		inv.setLastName("Lovelace");
		inv.setRoleNames(java.util.Set.of("TEACHER"));
		return inv;
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
			throw new IllegalStateException("No 'id' field found");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
