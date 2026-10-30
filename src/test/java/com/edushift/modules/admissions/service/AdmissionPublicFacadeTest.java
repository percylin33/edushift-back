package com.edushift.modules.admissions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionPublicFacadeTest {
    @Mock TenantRepository tenantRepository;
    @Mock AdmissionWorkflowService workflow;
    @InjectMocks AdmissionPublicFacade facade;
    @AfterEach void clear() { TenantContext.clear(); }

    private Tenant active(String slug) {
        Tenant tenant = new Tenant(); tenant.setId(UUID.randomUUID()); tenant.setSlug(slug);
        tenant.setName(slug); tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findBySlugIgnoreCase(slug)).thenReturn(Optional.of(tenant));
        return tenant;
    }
    @Test void publicOfferUsesOnlyTenantResolvedFromSlugAndRestoresContext() {
        Tenant a = active("school-a"); Tenant b = active("school-b");
        UUID previous = UUID.randomUUID(); TenantContext.set(previous);
        when(workflow.getPublicOffer(anyString(), anyString())).thenAnswer(call -> {
            assertThat(TenantContext.currentRequired()).isEqualTo(call.getArgument(0).equals("school-a") ? a.getId() : b.getId());
            return null;
        });
        facade.getOffer("school-a", "token-a"); facade.getOffer("school-b", "token-b");
        assertThat(TenantContext.currentRequired()).isEqualTo(previous);
    }
    @Test void failedCrossTenantLookupDoesNotLeakContext() {
        Tenant b = active("school-b");
        when(workflow.respondToOffer("token-a", true)).thenAnswer(call -> {
            assertThat(TenantContext.currentRequired()).isEqualTo(b.getId());
            throw new ResourceNotFoundException("AdmissionOffer", "invalid token");
        });
        assertThatThrownBy(() -> facade.respond("school-b", "token-a", true)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(TenantContext.isSet()).isFalse();
    }
    @Test void inactiveSchoolCannotReceiveInquiriesOrReadOffers() {
        Tenant tenant = active("school"); tenant.setStatus(TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> facade.getOffer("school", "token")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> facade.submit("school", null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(workflow);
    }
}
