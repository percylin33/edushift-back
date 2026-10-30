package com.edushift.modules.academic.unit.service.impl;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.unit.dto.SeedUnitsResponse;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.mapper.UnitMapper;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.academic.unit.service.UnitSeedService;
import com.edushift.modules.tenants.service.TenantSettingsService;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds {@code Unidad 1}…{@code Unidad N} for a course. N comes from the
 * request or {@link TenantSettingsService#getDefaultUnitCount}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitSeedServiceImpl implements UnitSeedService {

	private final CourseRepository courseRepository;
	private final UnitRepository unitRepository;
	private final UnitMapper unitMapper;
	private final TenantSettingsService tenantSettingsService;
	private final CurrentUserProvider currentUserProvider;

	@Override
	@Transactional
	public SeedUnitsResponse seedForCourse(
			UUID courseUuid,
			Integer countOrNull,
			boolean saveAsTenantDefault) {
		Course course = courseRepository.findByPublicUuid(courseUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", courseUuid));

		UUID tenantId = currentUserProvider.currentTenantId().orElse(null);
		int count = countOrNull != null
				? TenantSettingsService.clampUnitCount(countOrNull)
				: tenantSettingsService.getDefaultUnitCount(tenantId);

		if (saveAsTenantDefault && tenantId != null) {
			tenantSettingsService.setDefaultUnitCount(tenantId, count);
		}

		long existing = unitRepository.countByCourse(course);
		if (existing > 0) {
			log.debug("[academic.unit.seed] course={} -- skipping (already has {} units)",
					course.getCode(), existing);
			return new SeedUnitsResponse(false, count, 0, List.of());
		}

		List<UnitListItem> created = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			Unit unit = new Unit();
			unit.setCourse(course);
			unit.setName("Unidad " + i);
			unit.setDisplayOrder(i);
			unit.setIsActive(Boolean.TRUE);
			Unit persisted = unitRepository.save(unit);
			created.add(unitMapper.toListItem(persisted, 0L));
		}
		unitRepository.flush();

		log.info("[academic.unit.seed] course={} -- seeded {} units",
				course.getCode(), count);
		return new SeedUnitsResponse(true, count, count, created);
	}
}
