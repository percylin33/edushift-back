package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleSettingsDto;
import com.edushift.modules.schedule.daytemplate.dto.UpdateScheduleSettingsRequest;
import com.edushift.modules.schedule.daytemplate.entity.RecessPolicy;
import com.edushift.modules.schedule.daytemplate.service.ScheduleSettingsService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSettingsServiceImpl implements ScheduleSettingsService {

	private static final String SETTINGS_SCHEDULE_KEY = "schedule";
	private static final String RECESS_POLICY_KEY = "recessPolicy";
	private static final String SHARE_GROUP_LEVEL_CODES_KEY = "shareGroupLevelCodes";

	private final TenantRepository tenantRepository;

	@Override
	@Transactional(readOnly = true)
	public ScheduleSettingsDto getSettings() {
		Tenant tenant = loadCurrentTenant();
		return extract(tenant.getSettings());
	}

	@Override
	@Transactional
	@SuppressWarnings("unchecked")
	public ScheduleSettingsDto updateSettings(UpdateScheduleSettingsRequest request) {
		Tenant tenant = loadCurrentTenant();
		Map<String, Object> settings = tenant.getSettings();
		if (settings == null) {
			settings = new HashMap<>();
			tenant.setSettings(settings);
		}

		Object scheduleNode = settings.get(SETTINGS_SCHEDULE_KEY);
		Map<String, Object> schedule;
		if (scheduleNode instanceof Map<?, ?> map) {
			schedule = new HashMap<>();
			map.forEach((k, v) -> schedule.put(String.valueOf(k), v));
		} else {
			schedule = new HashMap<>();
		}

		if (request.recessPolicy() != null) {
			schedule.put(RECESS_POLICY_KEY, request.recessPolicy().name());
		}
		if (request.shareGroupLevelCodes() != null) {
			schedule.put(SHARE_GROUP_LEVEL_CODES_KEY, new ArrayList<>(request.shareGroupLevelCodes()));
		}

		settings.put(SETTINGS_SCHEDULE_KEY, schedule);
		tenant.setSettings(settings);
		tenantRepository.saveAndFlush(tenant);
		log.info("[schedule.settings] updated -- tenantId={} recessPolicy={}",
				tenant.getId(), schedule.get(RECESS_POLICY_KEY));
		return extract(settings);
	}

	private Tenant loadCurrentTenant() {
		UUID tenantId = TenantContext.currentRequired();
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
	}

	@SuppressWarnings("unchecked")
	private static ScheduleSettingsDto extract(Map<String, Object> settings) {
		RecessPolicy policy = RecessPolicy.STAGGERED;
		List<String> codes = List.of();
		if (settings == null) {
			return new ScheduleSettingsDto(policy, codes);
		}
		Object scheduleNode = settings.get(SETTINGS_SCHEDULE_KEY);
		if (!(scheduleNode instanceof Map<?, ?> scheduleMap)) {
			return new ScheduleSettingsDto(policy, codes);
		}
		Object policyRaw = scheduleMap.get(RECESS_POLICY_KEY);
		if (policyRaw != null) {
			try {
				policy = RecessPolicy.valueOf(policyRaw.toString().trim().toUpperCase());
			} catch (IllegalArgumentException ignored) {
				policy = RecessPolicy.STAGGERED;
			}
		}
		Object codesRaw = scheduleMap.get(SHARE_GROUP_LEVEL_CODES_KEY);
		if (codesRaw instanceof List<?> list) {
			List<String> parsed = new ArrayList<>();
			for (Object item : list) {
				if (item != null) {
					parsed.add(item.toString());
				}
			}
			codes = List.copyOf(parsed);
		}
		return new ScheduleSettingsDto(policy, codes);
	}
}
