package com.edushift.modules.students.provision;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves a login identifier without requiring a real contact mailbox. */
@Component
@RequiredArgsConstructor
public class PortalEmailResolver {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;

    public String resolve(String email, String firstName, String lastName, String documentNumber) {
        if (email != null && !email.isBlank()) return email;
        var tenantId = TenantContext.currentRequired();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        return SyntheticEmailFactory.generate(firstName, lastName, documentNumber, tenant.getSlug(),
                candidate -> userRepository.existsByEmail(candidate)
                        || invitationRepository.findActivePendingByEmail(candidate, Instant.now()).isPresent());
    }
}
