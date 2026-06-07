package com.edushift.modules.academic.competency.service.impl;

import com.edushift.modules.academic.competency.config.CompetencyDefaults;
import com.edushift.modules.academic.competency.config.CompetencyDefaults.DefaultCapacity;
import com.edushift.modules.academic.competency.config.CompetencyDefaults.DefaultCompetency;
import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.SeedCompetenciesResponse;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CompetencyMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.competency.service.CompetencySeedService;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CompetencySeedService}.
 *
 * <p>Idempotency contract is enforced here (not at the controller) so
 * that future callers (admin scripts, importers) get the same behaviour
 * as the public endpoint.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencySeedServiceImpl implements CompetencySeedService {

	private final CourseRepository courseRepository;
	private final CompetencyRepository competencyRepository;
	private final CapacityRepository capacityRepository;
	private final CompetencyMapper competencyMapper;

	@Override
	@Transactional
	public SeedCompetenciesResponse seedForCourse(UUID courseUuid) {
		Course course = courseRepository.findByPublicUuid(courseUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", courseUuid));

		long existing = competencyRepository.countByCourse(course);
		if (existing > 0) {
			log.debug("[academic.competency.seed] courseCode={} -- skipping (already has {} competencies)",
					course.getCode(), existing);
			return new SeedCompetenciesResponse(
					false, false, course.getCode(), 0, 0, List.of());
		}

		List<DefaultCompetency> bundle = CompetencyDefaults.bundleFor(course.getCode());
		if (bundle == null) {
			log.info("[academic.competency.seed] courseCode={} -- unsupported "
					+ "(no default bundle); supported codes={}",
					course.getCode(), CompetencyDefaults.supportedCourseCodes());
			return new SeedCompetenciesResponse(
					false, true, course.getCode(), 0, 0, List.of());
		}

		int competenciesCreated = 0;
		int capacitiesCreated = 0;
		List<CompetencyListItem> createdItems = new ArrayList<>(bundle.size());
		int competencyOrder = 1;
		for (DefaultCompetency defC : bundle) {
			Competency competency = new Competency();
			competency.setCourse(course);
			competency.setCode(defC.code());
			competency.setName(defC.name());
			competency.setDescription(defC.description());
			competency.setDisplayOrder(competencyOrder++);
			competency.setIsActive(Boolean.TRUE);
			Competency persisted = competencyRepository.save(competency);
			competenciesCreated++;

			int capacityOrder = 1;
			for (DefaultCapacity defCap : defC.capacities()) {
				Capacity capacity = new Capacity();
				capacity.setCompetency(persisted);
				capacity.setCode(defCap.code());
				capacity.setName(defCap.name());
				capacity.setDescription(defCap.description());
				capacity.setDisplayOrder(capacityOrder++);
				capacity.setIsActive(Boolean.TRUE);
				capacityRepository.save(capacity);
				capacitiesCreated++;
			}

			createdItems.add(competencyMapper.toListItem(persisted,
					(long) defC.capacities().size()));
		}

		competencyRepository.flush();
		capacityRepository.flush();

		log.info("[academic.competency.seed] courseCode={} -- seeded {} competencies "
						+ "and {} capacities",
				course.getCode(), competenciesCreated, capacitiesCreated);

		return new SeedCompetenciesResponse(
				true, false, course.getCode(),
				competenciesCreated, capacitiesCreated, createdItems);
	}
}
