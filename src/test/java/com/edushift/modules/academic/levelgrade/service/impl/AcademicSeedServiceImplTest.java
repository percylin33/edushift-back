package com.edushift.modules.academic.levelgrade.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.levelgrade.config.AcademicDefaults;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AcademicSeedServiceImpl} (Sprint 4 — BE-4.2).
 */
@ExtendWith(MockitoExtension.class)
class AcademicSeedServiceImplTest {

	@Mock private AcademicLevelRepository levelRepository;
	@Mock private GradeRepository gradeRepository;

	@InjectMocks private AcademicSeedServiceImpl service;

	private static final UUID TENANT_ID = UUID.randomUUID();

	@Nested
	@DisplayName("seedDefaults")
	class SeedDefaults {

		@Test
		@DisplayName("happy path — empty tenant gets all defaults seeded")
		void happyPath() {
			when(levelRepository.countBy()).thenReturn(0L);
			when(levelRepository.save(any(AcademicLevel.class))).thenAnswer(inv -> inv.getArgument(0));
			when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

			boolean result = service.seedDefaults(TENANT_ID);

			assertThat(result).isTrue();
			int expectedLevels = AcademicDefaults.LEVELS.size();
			int expectedGrades = AcademicDefaults.LEVELS.stream()
					.mapToInt(l -> l.grades().size()).sum();
			verify(levelRepository, times(expectedLevels)).save(any(AcademicLevel.class));
			verify(gradeRepository, times(expectedGrades)).save(any(Grade.class));
			// Sanity: 3 levels (INICIAL/PRIMARIA/SECUNDARIA) and 14 grades total
			assertThat(expectedLevels).isEqualTo(3);
			assertThat(expectedGrades).isEqualTo(14);
		}

		@Test
		@DisplayName("idempotent — tenant with existing levels is a no-op")
		void idempotent() {
			when(levelRepository.countBy()).thenReturn(3L);

			boolean result = service.seedDefaults(TENANT_ID);

			assertThat(result).isFalse();
			verify(levelRepository, never()).save(any(AcademicLevel.class));
			verify(gradeRepository, never()).save(any(Grade.class));
		}
	}
}
