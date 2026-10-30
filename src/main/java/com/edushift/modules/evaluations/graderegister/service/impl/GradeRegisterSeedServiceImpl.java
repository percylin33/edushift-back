package com.edushift.modules.evaluations.graderegister.service.impl;

import com.edushift.modules.evaluations.graderegister.config.GradeRegisterSeedDefaults;
import com.edushift.modules.evaluations.graderegister.config.GradeRegisterSeedDefaults.DefaultCategory;
import com.edushift.modules.evaluations.graderegister.config.GradeRegisterSeedDefaults.DefaultTemplate;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import com.edushift.modules.evaluations.graderegister.mapper.GradeRegisterTemplateMapper;
import com.edushift.modules.evaluations.graderegister.repository.GradeCategoryRepository;
import com.edushift.modules.evaluations.graderegister.repository.GradeRegisterTemplateRepository;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterSeedService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeRegisterSeedServiceImpl implements GradeRegisterSeedService {

	private final GradeRegisterTemplateRepository templateRepository;
	private final GradeCategoryRepository categoryRepository;
	private final GradeRegisterTemplateMapper mapper;

	@Override
	@Transactional
	public List<GradeRegisterTemplateListItem> materializeCatalog() {
		for (DefaultTemplate def : GradeRegisterSeedDefaults.all()) {
			var existing = templateRepository.findByCatalogCodeIgnoreCaseAndIsSystemTrue(def.catalogCode());
			if (existing.isPresent()) {
				GradeRegisterTemplate t = existing.get();
				List<String> levels = def.recommendedLevels() != null
						? List.copyOf(def.recommendedLevels())
						: List.of();
				if (t.getRecommendedLevels() == null || !t.getRecommendedLevels().equals(levels)) {
					t.setRecommendedLevels(levels);
					templateRepository.save(t);
				}
				continue;
			}
			GradeRegisterTemplate template = new GradeRegisterTemplate();
			template.setPublicUuid(UUID.randomUUID());
			template.setName(def.name());
			template.setDescription(def.description());
			template.setScope(GradeRegisterTemplateScope.TENANT);
			template.setIsSystem(Boolean.TRUE);
			template.setIsDefault(Boolean.FALSE);
			template.setCatalogCode(def.catalogCode());
			template.setRecommendedLevels(
					def.recommendedLevels() != null ? List.copyOf(def.recommendedLevels()) : List.of());
			templateRepository.save(template);

			for (DefaultCategory catDef : def.categories()) {
				GradeCategory cat = new GradeCategory();
				cat.setPublicUuid(UUID.randomUUID());
				cat.setTemplate(template);
				cat.setName(catDef.name());
				cat.setColorToken(catDef.colorToken());
				cat.setSortOrder(catDef.sortOrder());
				cat.setWeight(catDef.weight());
				cat.setContributesToAverage(catDef.contributesToAverage());
				cat.setAllowsTeacherColumns(catDef.allowsTeacherColumns());
				cat.setColumnMode(catDef.columnMode() != null
						? catDef.columnMode()
						: GradeCategoryColumnMode.OPEN);
				cat.setSlotCount(catDef.slotCount());
				cat.setSlotLabelPrefix(
						catDef.slotLabelPrefix() != null ? catDef.slotLabelPrefix() : "S");
				cat.setSlotLabels(catDef.slotLabels());
				categoryRepository.save(cat);
			}
			log.info("[grade-register] seeded catalog template code={}", def.catalogCode());
		}

		List<GradeRegisterTemplateListItem> result = new ArrayList<>();
		List<GradeRegisterTemplate> system = templateRepository.findByIsSystemTrueOrderByNameAsc();
		for (GradeRegisterTemplate t : system) {
			int count = categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(t).size();
			result.add(mapper.toListItem(t, count));
		}
		return result;
	}
}
