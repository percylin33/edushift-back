package com.edushift.modules.evaluations.rubric.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.CreateRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.dto.RubricFilters;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.dto.UpdateRubricRequest;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.error.RubricErrorCodes;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.evaluations.rubric.service.RubricSeedService;
import com.edushift.modules.evaluations.rubric.service.RubricService;
import com.edushift.modules.evaluations.rubric.service.RubricValidationService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link RubricServiceImpl} (Sprint 5B / BE-5B.2).
 *
 * <p>Covers every error code in the service contract, fork semantics,
 * system read-only protection, the shape-validation rules (delegated
 * to {@link RubricValidationService}), uniqueness on (tenant, name),
 * and the on-demand seed path.</p>
 */
@ExtendWith(MockitoExtension.class)
class RubricServiceImplTest {

	@Mock private RubricRepository rubricRepository;
	@Mock private RubricSeedService seedService;
	@Spy private RubricMapper rubricMapper = new RubricMapper();
	@Spy private RubricValidationService validationService = new RubricValidationService();

	private RubricServiceImpl service;

	@org.junit.jupiter.api.BeforeEach
	void wireService() {
		org.mockito.MockitoAnnotations.openMocks(this);
		service = new RubricServiceImpl(
				rubricRepository, rubricMapper, validationService, seedService);
	}

	// =========================================================================
	// listRubrics
	// =========================================================================

	@Nested
	@DisplayName("listRubrics")
	class ListRubrics {

		@Test
		@DisplayName("returns rubric list scoped to the current tenant")
		void happyPath() {
			Rubric r1 = newRubric("Ensayo", false);
			Rubric r2 = newRubric("Mapa conceptual", false);
			when(rubricRepository.findFiltered(null, null, null))
					.thenReturn(List.of(r1, r2));

			List<RubricListItem> result = service.listRubrics(
					new RubricFilters(null, null, null));

			assertThat(result).hasSize(2);
			assertThat(result.get(0).name()).isEqualTo("Ensayo");
		}

		@Test
		@DisplayName("null filters are normalised to an all-inclusive bag")
		void nullFilters() {
			when(rubricRepository.findFiltered(null, null, null))
					.thenReturn(List.of());
			List<RubricListItem> result = service.listRubrics(null);
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("blank q is treated as null (no LIKE filter applied)")
		void blankQueryTreatedAsNull() {
			Rubric r = newRubric("Ensayo", false);
			when(rubricRepository.findFiltered(null, null, null))
					.thenReturn(List.of(r));

			service.listRubrics(new RubricFilters(null, null, "   "));

			// findFiltered received nameQuery=null because the blank q
			// was normalised away.
			verify(rubricRepository).findFiltered(null, null, null);
		}
	}

	// =========================================================================
	// listSystemRubrics
	// =========================================================================

	@Nested
	@DisplayName("listSystemRubrics")
	class ListSystemRubrics {

		@Test
		@DisplayName("delegates to the seed service for on-demand materialisation")
		void delegatesToSeed() {
			Rubric r = newRubric("Ensayo argumentativo", true);
			// Build the ListItem once (outside the stubbing) so Mockito
			// does not see an unfinished stubbing.
			RubricListItem item = rubricMapper.toListItem(r);
			when(seedService.materializeSystemRubrics())
					.thenReturn(List.of(item));

			List<RubricListItem> result = service.listSystemRubrics();

			assertThat(result).hasSize(1);
			assertThat(result.get(0).isSystem()).isTrue();
			verify(seedService).materializeSystemRubrics();
		}
	}

	// =========================================================================
	// getRubric
	// =========================================================================

	@Nested
	@DisplayName("getRubric")
	class GetRubric {

		@Test
		@DisplayName("returns the full response projection")
		void happyPath() {
			Rubric r = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(r.getPublicUuid()))
					.thenReturn(Optional.of(r));

			RubricResponse response = service.getRubric(r.getPublicUuid());

			assertThat(response.name()).isEqualTo("Ensayo");
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void unknown() {
			UUID any = UUID.randomUUID();
			when(rubricRepository.findByPublicUuid(any))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getRubric(any))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createRubric
	// =========================================================================

	@Nested
	@DisplayName("createRubric")
	class CreateRubric {

		@Test
		@DisplayName("valid payload → persisted, response returned")
		void happyPath() {
			CreateRubricRequest request = new CreateRubricRequest(
					"Ensayo",
					"desc",
					validCriteria(),
					validLevels());
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "createdAt", Instant.now());
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			RubricResponse response = service.createRubric(request);

			assertThat(response.name()).isEqualTo("Ensayo");
			assertThat(response.isSystem()).isFalse();
			verify(validationService).assertShapeValid(request.criteria(), request.levels());
		}

		@Test
		@DisplayName("name is trimmed before persistence")
		void trimsName() {
			CreateRubricRequest request = new CreateRubricRequest(
					"  Ensayo  ",
					null,
					validCriteria(),
					validLevels());
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "createdAt", Instant.now());
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			RubricResponse response = service.createRubric(request);

			assertThat(response.name()).isEqualTo("Ensayo");
		}

		@Test
		@DisplayName("DataIntegrityViolationException on save → 409 RUB_NAME_EXISTS")
		void raceOnUniqueName() {
			CreateRubricRequest request = new CreateRubricRequest(
					"Ensayo", null, validCriteria(), validLevels());
			when(rubricRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_rubrics_tenant_name_ci"));

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(ConflictException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_NAME_EXISTS);
		}

		@Test
		@DisplayName("validation failure on shape → propagates the BadRequestException")
		void shapeFailurePropagates() {
			// 4 criteria at 25.00 each = 100.0 ✓, but the request has
			// only 1 level → RUB_LEVELS_COUNT (size must be 2..4).
			CreateRubricRequest request = new CreateRubricRequest(
					"Bad", null, validCriteria(), List.of(
							new LevelInput("EN_INICIO", "En inicio", 1)));

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_LEVELS_COUNT);

			verify(rubricRepository, never()).saveAndFlush(any());
		}
	}

	// =========================================================================
	// forkRubric
	// =========================================================================

	@Nested
	@DisplayName("forkRubric")
	class ForkRubric {

		@Test
		@DisplayName("system rubric + empty body → fork with \" (fork)\" suffix")
		void forkWithDefaultName() {
			Rubric source = newRubric("Ensayo", true);
			when(rubricRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "createdAt", Instant.now());
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			RubricResponse fork = service.forkRubric(source.getPublicUuid(), null);

			assertThat(fork.name()).isEqualTo("Ensayo (fork)");
			assertThat(fork.isSystem()).isFalse();
			assertThat(fork.parentRubricPublicUuid()).isEqualTo(source.getPublicUuid());
		}

		@Test
		@DisplayName("system rubric + body override → uses the override name + criteria")
		void forkWithOverride() {
			Rubric source = newRubric("Ensayo", true);
			when(rubricRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "createdAt", Instant.now());
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			CreateRubricRequest override = new CreateRubricRequest(
					"Ensayo (mi version)",
					"adaptado al colegio",
					validCriteria(),
					validLevels());
			RubricResponse fork = service.forkRubric(source.getPublicUuid(), override);

			assertThat(fork.name()).isEqualTo("Ensayo (mi version)");
			assertThat(fork.description()).isEqualTo("adaptado al colegio");
		}

		@Test
		@DisplayName("forking a non-system rubric → 400 RUB_CANNOT_FORK_NON_SYSTEM")
		void cannotForkNonSystem() {
			Rubric tenantOwned = newRubric("Mi rubrica", false);
			when(rubricRepository.findByPublicUuid(tenantOwned.getPublicUuid()))
					.thenReturn(Optional.of(tenantOwned));

			assertThatThrownBy(() -> service.forkRubric(tenantOwned.getPublicUuid(), null))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_CANNOT_FORK_NON_SYSTEM);
		}

		@Test
		@DisplayName("forking an unknown rubric → 404 RESOURCE_NOT_FOUND")
		void forkUnknown() {
			UUID any = UUID.randomUUID();
			when(rubricRepository.findByPublicUuid(any))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.forkRubric(any, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("fork name collision → 409 RUB_NAME_EXISTS")
		void forkNameCollision() {
			Rubric source = newRubric("Ensayo", true);
			when(rubricRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(rubricRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_rubrics_tenant_name_ci"));

			assertThatThrownBy(() -> service.forkRubric(source.getPublicUuid(),
					new CreateRubricRequest("Duplicado", null, validCriteria(), validLevels())))
					.isInstanceOf(ConflictException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_NAME_EXISTS);
		}
	}

	// =========================================================================
	// updateRubric
	// =========================================================================

	@Nested
	@DisplayName("updateRubric")
	class UpdateRubric {

		@Test
		@DisplayName("empty body → no-op (200 with unchanged entity)")
		void emptyBody() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			RubricResponse response = service.updateRubric(
					existing.getPublicUuid(), new UpdateRubricRequest(null, null, null, null));

			assertThat(response.name()).isEqualTo("Ensayo");
			verify(rubricRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("system rubric + any patch → 403 RUB_SYSTEM_READ_ONLY")
		void systemIsReadOnly() {
			Rubric systemRubric = newRubric("Ensayo (MINEDU)", true);
			when(rubricRepository.findByPublicUuid(systemRubric.getPublicUuid()))
					.thenReturn(Optional.of(systemRubric));

			assertThatThrownBy(() -> service.updateRubric(
					systemRubric.getPublicUuid(),
					new UpdateRubricRequest("Renombrado", null, null, null)))
					.isInstanceOf(ForbiddenException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_SYSTEM_READ_ONLY);
		}

		@Test
		@DisplayName("name change + available → 200 with new name")
		void renameAvailable() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(rubricRepository.findByNameIgnoreCase("Ensayo (renombrado)"))
					.thenReturn(Optional.empty());
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			RubricResponse response = service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest("Ensayo (renombrado)", null, null, null));

			assertThat(response.name()).isEqualTo("Ensayo (renombrado)");
		}

		@Test
		@DisplayName("name change + collision (other rubric) → 409 RUB_NAME_EXISTS")
		void renameCollision() {
			Rubric existing = newRubric("Ensayo", false);
			Rubric other = newRubric("Otro nombre", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(rubricRepository.findByNameIgnoreCase("Otro nombre"))
					.thenReturn(Optional.of(other));

			assertThatThrownBy(() -> service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest("Otro nombre", null, null, null)))
					.isInstanceOf(ConflictException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_NAME_EXISTS);
		}

		@Test
		@DisplayName("name change + same name (case-insensitive) skips the uniqueness probe")
		void renameSameName() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
							Rubric r = inv.getArgument(0);
							setField(r, "updatedAt", Instant.now());
							return r;
					});

			RubricResponse response = service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest("ensayo", null, null, null));

			// The uniqueness probe is skipped (case-insensitive match).
			verify(rubricRepository, never()).findByNameIgnoreCase(any());
			// The mapper still applies the trim so the response reflects
			// the canonical name (no leading/trailing whitespace).
			assertThat(response.name()).isEqualTo("ensayo");
		}

		@Test
		@DisplayName("criteria patch with weight sum 100.0 → 200")
		void validCriteriaPatch() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(rubricRepository.saveAndFlush(any()))
					.thenAnswer(inv -> {
						Rubric r = inv.getArgument(0);
						setField(r, "updatedAt", Instant.now());
						return r;
					});

			RubricResponse response = service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest(null, "new desc",
							validCriteria(), validLevels()));

			assertThat(response.description()).isEqualTo("new desc");
		}

		@Test
		@DisplayName("criteria patch with weight sum 80.0 → 400 RUB_CRITERIA_WEIGHT_SUM")
		void invalidWeightSum() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			// 4 criteria at 20.00 each = 80.0, not 100.0
			List<CriterionInput> badCriteria = List.of(
					new CriterionInput("a", "A", null,
							BigDecimal.valueOf(20.00), List.of()),
					new CriterionInput("b", "B", null,
							BigDecimal.valueOf(20.00), List.of()),
					new CriterionInput("c", "C", null,
							BigDecimal.valueOf(20.00), List.of()),
					new CriterionInput("d", "D", null,
							BigDecimal.valueOf(20.00), List.of()));

			assertThatThrownBy(() -> service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest(null, null, badCriteria, validLevels())))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM);

			verify(rubricRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("data integrity violation on save → 409 RUB_NAME_EXISTS")
		void saveRace() {
			Rubric existing = newRubric("Ensayo", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(rubricRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_rubrics_tenant_name_ci"));

			assertThatThrownBy(() -> service.updateRubric(
					existing.getPublicUuid(),
					new UpdateRubricRequest("Nuevo", null, null, null)))
					.isInstanceOf(ConflictException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_NAME_EXISTS);
		}
	}

	// =========================================================================
	// deleteRubric
	// =========================================================================

	@Nested
	@DisplayName("deleteRubric")
	class DeleteRubric {

		@Test
		@DisplayName("happy path → soft-delete (repository.delete invoked)")
		void happyPath() {
			Rubric existing = newRubric("Mi rubrica", false);
			when(rubricRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			service.deleteRubric(existing.getPublicUuid());

			verify(rubricRepository).delete(existing);
		}

		@Test
		@DisplayName("system rubric → 403 RUB_SYSTEM_READ_ONLY (delete is a mutation)")
		void systemIsReadOnly() {
			Rubric systemRubric = newRubric("Ensayo (MINEDU)", true);
			when(rubricRepository.findByPublicUuid(systemRubric.getPublicUuid()))
					.thenReturn(Optional.of(systemRubric));

			assertThatThrownBy(() -> service.deleteRubric(systemRubric.getPublicUuid()))
					.isInstanceOf(ForbiddenException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_SYSTEM_READ_ONLY);

			verify(rubricRepository, never()).delete(any());
		}

		@Test
		@DisplayName("unknown → 404 RESOURCE_NOT_FOUND")
		void unknown() {
			UUID any = UUID.randomUUID();
			when(rubricRepository.findByPublicUuid(any))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteRubric(any))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// Validation: the service delegates to RubricValidationService, so the
	// cross-test on shape failures is covered transitively. A few sanity
	// checks at the service level are enough.
	// =========================================================================

	@Nested
	@DisplayName("ValidationService delegation")
	class ValidationDelegation {

		@Test
		@DisplayName("create: criteria count = 0 → 400 RUB_CRITERIA_COUNT")
		void zeroCriteria() {
			CreateRubricRequest request = new CreateRubricRequest(
					"X", null, List.of(), validLevels());

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_CRITERIA_COUNT);
		}

		@Test
		@DisplayName("create: criteria key duplicate → 400 RUB_CRITERION_KEY_DUPLICATE")
		void duplicateCriterionKey() {
			List<CriterionInput> criteria = List.of(
					new CriterionInput("dup", "A", null,
							BigDecimal.valueOf(50.00), List.of()),
					new CriterionInput("dup", "B", null,
							BigDecimal.valueOf(50.00), List.of()));
			CreateRubricRequest request = new CreateRubricRequest(
					"X", null, criteria, validLevels());

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_CRITERION_KEY_DUPLICATE);
		}

		@Test
		@DisplayName("create: descriptor references unknown level → 400 RUB_LEVEL_UNKNOWN")
		void descriptorUnknownLevel() {
			List<CriterionInput> criteria = List.of(
					new CriterionInput("a", "A", null,
							BigDecimal.valueOf(100.00),
							List.of(new DescriptorInput("DESCONOCIDO", "txt"))));
			CreateRubricRequest request = new CreateRubricRequest(
					"X", null, criteria, validLevels());

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_LEVEL_UNKNOWN);
		}

		@Test
		@DisplayName("create: duplicate level code → 400 RUB_LEVEL_CODE_DUPLICATE")
		void duplicateLevelCode() {
			List<LevelInput> levels = List.of(
					new LevelInput("EN_INICIO", "En inicio", 1),
					new LevelInput("EN_INICIO", "En inicio (dup)", 2),
					new LevelInput("ESPERADO", "Esperado", 3));
			CreateRubricRequest request = new CreateRubricRequest(
					"X", null, validCriteria(), levels);

			assertThatThrownBy(() -> service.createRubric(request))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo(RubricErrorCodes.RUB_LEVEL_CODE_DUPLICATE);
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Rubric newRubric(String name, boolean isSystem) {
		Rubric r = new Rubric();
		r.setName(name);
		r.setDescription("desc");
		r.setIsSystem(isSystem);
		r.setIsActive(Boolean.TRUE);
		r.setPublicUuid(UUID.randomUUID());
		r.setCriteria(defaultCriteriaMaps());
		r.setLevels(defaultLevelMaps());
		setField(r, "id", UUID.randomUUID());
		setField(r, "createdAt", Instant.now());
		setField(r, "updatedAt", Instant.now());
		return r;
	}

	/** 4 criteria at 25% each — sums to 100.0. */
	private static List<CriterionInput> validCriteria() {
		return List.of(
				new CriterionInput("a", "A", null,
						BigDecimal.valueOf(25.00), List.of()),
				new CriterionInput("b", "B", null,
						BigDecimal.valueOf(25.00), List.of()),
				new CriterionInput("c", "C", null,
						BigDecimal.valueOf(25.00), List.of()),
				new CriterionInput("d", "D", null,
						BigDecimal.valueOf(25.00), List.of()));
	}

	/** 4 canonical MINEDU levels. */
	private static List<LevelInput> validLevels() {
		return List.of(
				new LevelInput("EN_INICIO", "En inicio", 1),
				new LevelInput("EN_PROCESO", "En proceso", 2),
				new LevelInput("ESPERADO", "Esperado", 3),
				new LevelInput("SOBRESALIENTE", "Sobresaliente", 4));
	}

	/** Default criteria JSONB for fresh entities (so the mapper does not NPE on toListItem). */
	private static List<Map<String, Object>> defaultCriteriaMaps() {
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("key", "k" + i);
			m.put("name", "K" + i);
			m.put("weight", BigDecimal.valueOf(25.00));
			m.put("descriptors", List.of());
			out.add(m);
		}
		return out;
	}

	/** Default levels JSONB for fresh entities. */
	private static List<Map<String, Object>> defaultLevelMaps() {
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		String[] codes = {"EN_INICIO", "EN_PROCESO", "ESPERADO", "SOBRESALIENTE"};
		for (int i = 0; i < codes.length; i++) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", codes[i]);
			m.put("name", codes[i]);
			m.put("order", i + 1);
			out.add(m);
		}
		return out;
	}

	private static void setField(Object target, String field, Object value) {
		try {
			Class<?> c = target.getClass();
			while (c != null) {
				try {
					Field f = c.getDeclaredField(field);
					f.setAccessible(true);
					f.set(target, value);
					return;
				}
				catch (NoSuchFieldException ex) {
					c = c.getSuperclass();
				}
			}
			throw new RuntimeException("Field " + field + " not found on " + target.getClass());
		}
		catch (IllegalAccessException ex) {
			throw new RuntimeException(ex);
		}
	}
}
