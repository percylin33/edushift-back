package com.edushift.modules.sessions.template.service;

import com.edushift.modules.sessions.template.config.SessionTemplateSeedDefaults;
import com.edushift.modules.sessions.template.config.SessionTemplateSeedDefaults.DefaultTemplate;
import com.edushift.modules.sessions.template.dto.AdoptSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.CreateSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplateGroupDto;
import com.edushift.modules.sessions.template.dto.SessionTemplateListItem;
import com.edushift.modules.sessions.template.dto.SessionTemplatePhasePreview;
import com.edushift.modules.sessions.template.dto.SessionTemplateResponse;
import com.edushift.modules.sessions.template.dto.SessionTemplatesOrganizedResponse;
import com.edushift.modules.sessions.template.dto.UpdateSessionTemplateRequest;
import com.edushift.modules.sessions.template.entity.SessionTemplate;
import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import com.edushift.modules.sessions.template.repository.SessionTemplateRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTemplateService {

	private static final String ERROR_KEY_TAKEN = "TEMPLATE_KEY_TAKEN";
	private static final String ERROR_SYSTEM_READONLY = "TEMPLATE_SYSTEM_READONLY";
	private static final String ERROR_NOT_CATALOG = "SESSION_TEMPLATE_NOT_CATALOG";

	private final SessionTemplateRepository repository;

	@Transactional
	public List<SessionTemplateListItem> listCatalog() {
		materializeCatalog();
		return repository.findByIsSystemTrueOrderByFamilyAscNameAsc().stream()
				.map(t -> toListItem(t, /*canAdopt*/ true, /*canEdit*/ false))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<SessionTemplateResponse> listMine() {
		return repository.findByIsSystemFalseOrderByIsDefaultDescFamilyAscNameAsc().stream()
				.map(this::toResponse)
				.toList();
	}

	/** Backward-compatible list: tenant customs only (system catalog via /catalog). */
	@Transactional(readOnly = true)
	public List<SessionTemplateResponse> listAll() {
		return listMine();
	}

	@Transactional
	public SessionTemplatesOrganizedResponse listOrganized() {
		materializeCatalog();
		List<SessionTemplate> system = repository.findByIsSystemTrueOrderByFamilyAscNameAsc();
		List<SessionTemplate> mine = repository.findByIsSystemFalseOrderByIsDefaultDescFamilyAscNameAsc();

		Map<SessionTemplateFamily, List<SessionTemplateListItem>> buckets =
				new EnumMap<>(SessionTemplateFamily.class);
		for (SessionTemplateFamily f : SessionTemplateFamily.values()) {
			buckets.put(f, new ArrayList<>());
		}
		for (SessionTemplate t : system) {
			buckets.get(t.getFamily()).add(toListItem(t, true, false));
		}
		for (SessionTemplate t : mine) {
			buckets.get(t.getFamily()).add(toListItem(t, false, true));
		}

		List<SessionTemplateGroupDto> groups = new ArrayList<>();
		for (SessionTemplateFamily f : SessionTemplateFamily.values()) {
			List<SessionTemplateListItem> items = buckets.get(f);
			if (items == null || items.isEmpty()) {
				continue;
			}
			groups.add(new SessionTemplateGroupDto(f, f.labelEs(), List.copyOf(items)));
		}
		return new SessionTemplatesOrganizedResponse(groups);
	}

	@Transactional(readOnly = true)
	public SessionTemplateResponse get(UUID publicUuid) {
		return toResponse(require(publicUuid));
	}

	@Transactional
	public SessionTemplateResponse create(CreateSessionTemplateRequest request) {
		if (repository.findByTemplateKey(request.templateKey()).isPresent()) {
			throw new ConflictException(ERROR_KEY_TAKEN,
					"Template key '" + request.templateKey() + "' already exists");
		}
		SessionTemplate entity = new SessionTemplate();
		entity.setTemplateKey(request.templateKey());
		entity.setName(request.name());
		entity.setDescription(request.description());
		entity.setSchema(request.schema() != null ? new LinkedHashMap<>(request.schema()) : new LinkedHashMap<>());
		entity.setIsSystem(false);
		entity.setFamily(SessionTemplateFamily.CUSTOM);
		entity.setIsDefault(false);
		SessionTemplate saved = repository.save(entity);
		return toResponse(saved);
	}

	@Transactional
	public SessionTemplateResponse update(UUID publicUuid, UpdateSessionTemplateRequest request) {
		SessionTemplate entity = require(publicUuid);
		if (Boolean.TRUE.equals(entity.getIsSystem())) {
			throw new ConflictException(ERROR_SYSTEM_READONLY, "System templates cannot be modified");
		}
		if (request == null || request.isEmpty()) {
			return toResponse(entity);
		}
		if (request.name() != null) {
			entity.setName(request.name());
		}
		if (request.description() != null) {
			entity.setDescription(request.description());
		}
		if (request.schema() != null) {
			entity.setSchema(new LinkedHashMap<>(request.schema()));
		}
		if (request.isDefault() != null) {
			applyDefaultFlag(entity, request.isDefault());
		}
		return toResponse(repository.save(entity));
	}

	@Transactional
	public void delete(UUID publicUuid) {
		SessionTemplate entity = require(publicUuid);
		if (Boolean.TRUE.equals(entity.getIsSystem())) {
			throw new ConflictException(ERROR_SYSTEM_READONLY, "System templates cannot be deleted");
		}
		repository.delete(entity);
	}

	@Transactional
	public SessionTemplateResponse adopt(UUID catalogPublicUuid, AdoptSessionTemplateRequest request) {
		materializeCatalog();
		SessionTemplate source = require(catalogPublicUuid);
		if (!Boolean.TRUE.equals(source.getIsSystem())) {
			throw new BadRequestException(ERROR_NOT_CATALOG,
					"Only system catalog templates can be adopted");
		}

		String baseKey = source.getTemplateKey() + "-tenant";
		String key = uniqueKey(baseKey);

		SessionTemplate copy = new SessionTemplate();
		copy.setTemplateKey(key);
		copy.setName(source.getName());
		copy.setDescription(source.getDescription());
		copy.setSchema(deepCopySchema(source.getSchema()));
		copy.setIsSystem(false);
		copy.setFamily(source.getFamily() != null ? source.getFamily() : SessionTemplateFamily.CUSTOM);
		copy.setSourceTemplateUuid(source.getPublicUuid());
		copy.setIsDefault(false);

		boolean setDefault = request != null && Boolean.TRUE.equals(request.setAsDefault());
		SessionTemplate saved = repository.save(copy);
		if (setDefault) {
			applyDefaultFlag(saved, true);
			saved = repository.save(saved);
		}
		log.info("[session-template] adopted catalog={} -> tenantKey={}",
				source.getTemplateKey(), saved.getTemplateKey());
		return toResponse(saved);
	}

	@Transactional
	public SessionTemplateResponse setDefault(UUID publicUuid) {
		SessionTemplate entity = require(publicUuid);
		if (Boolean.TRUE.equals(entity.getIsSystem())) {
			throw new ConflictException(ERROR_SYSTEM_READONLY,
					"System templates cannot be set as default; adopt first");
		}
		applyDefaultFlag(entity, true);
		return toResponse(repository.save(entity));
	}

	@Transactional(readOnly = true)
	public Optional<SessionTemplate> findDefaultForTenant() {
		return repository.findByIsDefaultTrueAndIsSystemFalse();
	}

	@Transactional(readOnly = true)
	public SessionTemplate requireTenantOrSystem(UUID publicUuid) {
		return require(publicUuid);
	}

	/**
	 * Seeds the standard MINEDU template into the current tenant and retires
	 * other system archetypes (ABC, matemática) so the catalog stays
	 * standard + tenant-owned templates.
	 */
	@Transactional
	public void materializeCatalog() {
		Set<String> allowed = new HashSet<>();
		for (DefaultTemplate def : SessionTemplateSeedDefaults.all()) {
			allowed.add(def.templateKey());
			Optional<SessionTemplate> existing =
					repository.findByTemplateKeyAndIsSystemTrue(def.templateKey());
			if (existing.isPresent()) {
				continue;
			}
			SessionTemplate t = new SessionTemplate();
			t.setTemplateKey(def.templateKey());
			t.setName(def.name());
			t.setDescription(def.description());
			t.setSchema(deepCopySchema(def.schema()));
			t.setIsSystem(true);
			t.setFamily(def.family());
			t.setIsDefault(false);
			repository.save(t);
			log.info("[session-template] seeded system key={}", def.templateKey());
		}
		for (SessionTemplate existing : repository.findByIsSystemTrueOrderByFamilyAscNameAsc()) {
			if (existing.getTemplateKey() != null && !allowed.contains(existing.getTemplateKey())) {
				repository.delete(existing);
				log.info("[session-template] retired system key={}", existing.getTemplateKey());
			}
		}
	}

	/**
	 * Builds flat activity/material seed lines from a template schema for LearningSession content.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> seedActivitiesFromSchema(Map<String, Object> schema) {
		List<String> lines = new ArrayList<>();
		if (schema == null) {
			return lines;
		}
		Object phasesObj = schema.get("phases");
		if (!(phasesObj instanceof List<?> phases)) {
			return lines;
		}
		for (Object p : phases) {
			if (!(p instanceof Map<?, ?> m)) {
				continue;
			}
			Object name = m.get("name");
			Object desc = m.get("description");
			Object dur = m.get("duration_min");
			StringBuilder sb = new StringBuilder();
			sb.append('[').append(name != null ? name : "Fase").append(']');
			if (dur != null) {
				sb.append(' ').append(dur).append(" min");
			}
			if (desc != null && !desc.toString().isBlank()) {
				sb.append(" — ").append(desc);
			}
			lines.add(sb.toString());
		}
		return lines;
	}

	@SuppressWarnings("unchecked")
	public static List<String> seedMaterialsFromSchema(Map<String, Object> schema) {
		if (schema == null) {
			return List.of();
		}
		Object mats = schema.get("materials_suggested");
		if (!(mats instanceof List<?> list)) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Object o : list) {
			if (o != null && !o.toString().isBlank()) {
				out.add(o.toString());
			}
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	public static List<String> phaseNamesFromSchema(Map<String, Object> schema) {
		List<String> names = new ArrayList<>();
		if (schema == null) {
			return names;
		}
		Object phasesObj = schema.get("phases");
		if (!(phasesObj instanceof List<?> phases)) {
			return names;
		}
		for (Object p : phases) {
			if (p instanceof Map<?, ?> m && m.get("name") != null) {
				names.add(m.get("name").toString());
			}
		}
		return names;
	}

	private void applyDefaultFlag(SessionTemplate entity, boolean makeDefault) {
		if (makeDefault) {
			repository.clearOtherDefaults(entity.getPublicUuid());
			entity.setIsDefault(true);
		} else {
			entity.setIsDefault(false);
		}
	}

	private String uniqueKey(String base) {
		if (repository.findByTemplateKey(base).isEmpty()) {
			return base;
		}
		for (int i = 2; i < 100; i++) {
			String candidate = base + "-" + i;
			if (repository.findByTemplateKey(candidate).isEmpty()) {
				return candidate;
			}
		}
		return base + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private SessionTemplate require(UUID publicUuid) {
		return repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("SessionTemplate", publicUuid));
	}

	private SessionTemplateResponse toResponse(SessionTemplate t) {
		return new SessionTemplateResponse(
				t.getPublicUuid(),
				t.getTemplateKey(),
				t.getName(),
				t.getDescription(),
				t.getSchema(),
				Boolean.TRUE.equals(t.getIsSystem()),
				t.getFamily() != null ? t.getFamily() : SessionTemplateFamily.CUSTOM,
				t.getSourceTemplateUuid(),
				Boolean.TRUE.equals(t.getIsDefault()),
				t.getCreatedAt()
		);
	}

	private SessionTemplateListItem toListItem(SessionTemplate t, boolean canAdopt, boolean canEdit) {
		SessionTemplateFamily family = t.getFamily() != null ? t.getFamily() : SessionTemplateFamily.CUSTOM;
		boolean system = Boolean.TRUE.equals(t.getIsSystem());
		return new SessionTemplateListItem(
				t.getPublicUuid(),
				t.getTemplateKey(),
				t.getName(),
				t.getDescription(),
				family,
				family.labelEs(),
				system ? "SYSTEM" : "TENANT",
				system,
				Boolean.TRUE.equals(t.getIsDefault()),
				t.getSourceTemplateUuid() != null,
				canAdopt && system,
				canEdit && !system,
				extractPhases(t.getSchema())
		);
	}

	@SuppressWarnings("unchecked")
	private static List<SessionTemplatePhasePreview> extractPhases(Map<String, Object> schema) {
		if (schema == null) {
			return List.of();
		}
		Object phasesObj = schema.get("phases");
		if (!(phasesObj instanceof List<?> phases)) {
			return List.of();
		}
		List<SessionTemplatePhasePreview> out = new ArrayList<>();
		for (Object p : phases) {
			if (!(p instanceof Map<?, ?> m)) {
				continue;
			}
			String name = m.get("name") != null ? m.get("name").toString() : null;
			Integer dur = null;
			Object d = m.get("duration_min");
			if (d instanceof Number n) {
				dur = n.intValue();
			}
			String desc = m.get("description") != null ? m.get("description").toString() : null;
			out.add(new SessionTemplatePhasePreview(name, dur, desc));
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> deepCopySchema(Map<String, Object> schema) {
		if (schema == null) {
			return new LinkedHashMap<>();
		}
		return new LinkedHashMap<>(schema);
	}
}
