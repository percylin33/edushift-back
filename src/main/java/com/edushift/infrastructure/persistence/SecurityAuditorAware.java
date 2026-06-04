package com.edushift.infrastructure.persistence;

import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Bridge between Spring Data JPA Auditing and {@link CurrentUserProvider}.
 * <p>
 * Populates {@code @CreatedBy} / {@code @LastModifiedBy} with the current user id.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditorAware implements AuditorAware<UUID> {

	private final CurrentUserProvider currentUserProvider;

	@Override
	public Optional<UUID> getCurrentAuditor() {
		return currentUserProvider.currentUserId();
	}

}
