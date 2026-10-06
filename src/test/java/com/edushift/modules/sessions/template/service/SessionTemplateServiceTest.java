package com.edushift.modules.sessions.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.sessions.template.config.SessionTemplateSeedDefaults;
import com.edushift.modules.sessions.template.dto.AdoptSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.CreateSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplatesOrganizedResponse;
import com.edushift.modules.sessions.template.dto.UpdateSessionTemplateRequest;
import com.edushift.modules.sessions.template.entity.SessionTemplate;
import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import com.edushift.modules.sessions.template.repository.SessionTemplateRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionTemplateServiceTest {

	@Mock private SessionTemplateRepository repository;
	private SessionTemplateService service;

	@BeforeEach
	void setUp() {
		service = new SessionTemplateService(repository);
	}

	@Test
	@DisplayName("materializeCatalog seeds missing system templates")
	void materializeSeeds() {
		when(repository.findByTemplateKeyAndIsSystemTrue(any())).thenReturn(Optional.empty());
		when(repository.save(any(SessionTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		service.materializeCatalog();

		ArgumentCaptor<SessionTemplate> cap = ArgumentCaptor.forClass(SessionTemplate.class);
		verify(repository, org.mockito.Mockito.times(SessionTemplateSeedDefaults.all().size()))
				.save(cap.capture());
		assertThat(cap.getAllValues()).allMatch(t -> Boolean.TRUE.equals(t.getIsSystem()));
		assertThat(cap.getAllValues().stream().map(SessionTemplate::getTemplateKey))
				.containsExactly("minedu-standard");
	}

	@Test
	@DisplayName("materializeCatalog retires system templates other than MINEDU standard")
	void materializeRetiresExtraSystemTemplates() {
		SessionTemplate standard = system("minedu-standard", SessionTemplateFamily.STANDARD);
		SessionTemplate abc = system("minedu-abc", SessionTemplateFamily.COMMUNICATION);
		when(repository.findByTemplateKeyAndIsSystemTrue(any())).thenReturn(Optional.of(standard));
		when(repository.findByIsSystemTrueOrderByFamilyAscNameAsc()).thenReturn(List.of(standard, abc));

		service.materializeCatalog();

		verify(repository).delete(abc);
		verify(repository, never()).delete(standard);
	}

	@Test
	@DisplayName("organized groups system + tenant by family")
	void organizedGroups() {
		SessionTemplate sys = system("minedu-standard", SessionTemplateFamily.STANDARD);
		SessionTemplate mine = tenant("custom-1", SessionTemplateFamily.CUSTOM);
		when(repository.findByTemplateKeyAndIsSystemTrue(any())).thenReturn(Optional.of(sys));
		when(repository.findByIsSystemTrueOrderByFamilyAscNameAsc()).thenReturn(List.of(sys));
		when(repository.findByIsSystemFalseOrderByIsDefaultDescFamilyAscNameAsc()).thenReturn(List.of(mine));

		SessionTemplatesOrganizedResponse resp = service.listOrganized();

		assertThat(resp.groups()).hasSize(2);
		assertThat(resp.groups().get(0).family()).isEqualTo(SessionTemplateFamily.STANDARD);
		assertThat(resp.groups().get(1).family()).isEqualTo(SessionTemplateFamily.CUSTOM);
	}

	@Test
	@DisplayName("adopt clones system template into tenant")
	void adoptClones() {
		SessionTemplate sys = system("minedu-abc", SessionTemplateFamily.COMMUNICATION);
		sys.setPublicUuid(UUID.randomUUID());
		when(repository.findByTemplateKeyAndIsSystemTrue(any())).thenReturn(Optional.of(sys));
		when(repository.findByPublicUuid(sys.getPublicUuid())).thenReturn(Optional.of(sys));
		when(repository.findByTemplateKey(any())).thenReturn(Optional.empty());
		when(repository.save(any(SessionTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		var resp = service.adopt(sys.getPublicUuid(), new AdoptSessionTemplateRequest(true));

		assertThat(resp.isSystem()).isFalse();
		assertThat(resp.family()).isEqualTo(SessionTemplateFamily.COMMUNICATION);
		assertThat(resp.sourceTemplateUuid()).isEqualTo(sys.getPublicUuid());
		assertThat(resp.isDefault()).isTrue();
		verify(repository).clearOtherDefaults(any());
	}

	@Test
	@DisplayName("adopt rejects non-system template")
	void adoptRejectsTenant() {
		SessionTemplate mine = tenant("custom", SessionTemplateFamily.CUSTOM);
		mine.setPublicUuid(UUID.randomUUID());
		when(repository.findByTemplateKeyAndIsSystemTrue(any())).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(repository.findByPublicUuid(mine.getPublicUuid())).thenReturn(Optional.of(mine));

		assertThatThrownBy(() -> service.adopt(mine.getPublicUuid(), new AdoptSessionTemplateRequest(false)))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	@DisplayName("update/delete blocked for system templates")
	void systemReadonly() {
		SessionTemplate sys = system("minedu-math", SessionTemplateFamily.MATH);
		sys.setPublicUuid(UUID.randomUUID());
		when(repository.findByPublicUuid(sys.getPublicUuid())).thenReturn(Optional.of(sys));

		assertThatThrownBy(() -> service.update(sys.getPublicUuid(),
				new UpdateSessionTemplateRequest("x", null, null, null)))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.delete(sys.getPublicUuid()))
				.isInstanceOf(ConflictException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	@DisplayName("create custom forces CUSTOM family")
	void createCustom() {
		when(repository.findByTemplateKey("mi-plantilla")).thenReturn(Optional.empty());
		when(repository.save(any(SessionTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

		var resp = service.create(new CreateSessionTemplateRequest(
				"mi-plantilla", "Mi plantilla", "desc", null));

		assertThat(resp.family()).isEqualTo(SessionTemplateFamily.CUSTOM);
		assertThat(resp.isSystem()).isFalse();
	}

	private static SessionTemplate system(String key, SessionTemplateFamily family) {
		SessionTemplate t = new SessionTemplate();
		t.setPublicUuid(UUID.randomUUID());
		t.setTemplateKey(key);
		t.setName(key);
		t.setIsSystem(true);
		t.setFamily(family);
		t.setSchema(new java.util.LinkedHashMap<>(SessionTemplateSeedDefaults.all().get(0).schema()));
		t.setIsDefault(false);
		return t;
	}

	private static SessionTemplate tenant(String key, SessionTemplateFamily family) {
		SessionTemplate t = new SessionTemplate();
		t.setPublicUuid(UUID.randomUUID());
		t.setTemplateKey(key);
		t.setName(key);
		t.setIsSystem(false);
		t.setFamily(family);
		t.setSchema(new java.util.LinkedHashMap<>());
		t.setIsDefault(false);
		return t;
	}
}
