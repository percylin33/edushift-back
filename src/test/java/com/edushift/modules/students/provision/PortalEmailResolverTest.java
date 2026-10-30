package com.edushift.modules.students.provision;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class PortalEmailResolverTest {
 @Test void preservesRealEmailWithoutLookingUpSchool() {
  var tenants = mock(TenantRepository.class);
  var resolver = new PortalEmailResolver(tenants, mock(UserRepository.class), mock(UserInvitationRepository.class));
  assertThat(resolver.resolve("family@example.com", "Alex", "Parent", "12345678")).isEqualTo("family@example.com");
  verifyNoInteractions(tenants);
 }
 @Test void usesFixedSyntheticDomainAndKeepsCurrentTenantScope() {
  var tenants = mock(TenantRepository.class);
  var resolver = new PortalEmailResolver(tenants, mock(UserRepository.class), mock(UserInvitationRepository.class));
  var tenantId = UUID.randomUUID();
  var tenant = new Tenant(); tenant.setSlug("semillitasinicialprueba");
  when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
  String email = TenantContext.runAs(tenantId, () -> resolver.resolve(null, "María", "Pérez", "12345678"));
  assertThat(email).matches("maria.perez.12345678.[a-f0-9]{6}@slud-del-colegio\\.com");
  assertThat(SyntheticEmailFactory.isSyntheticAddress(email)).isTrue();
  verify(tenants).findById(tenantId);
 }
 @Test void syntheticAddressesNeverReachMailer() {
  var provider = mock(org.springframework.beans.factory.ObjectProvider.class);
  var mailer = new com.edushift.modules.users.UserInvitationMailer(provider,
   mock(com.edushift.modules.notifications.email.EmailLayoutRenderer.class),
   mock(com.edushift.modules.notifications.email.EmailBrandingResolver.class));
  assertThat(mailer.send("maria.perez.12345678.abcdef@school.com", "School", "link", null)).isFalse();
  assertThat(mailer.send("old@school.accounts.edushift.local", "School", "link", null)).isFalse();
  verifyNoInteractions(provider);
 }
}
