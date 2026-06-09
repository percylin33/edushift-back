package com.edushift.modules.academic.competency.repository;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Capacity}. Tenant-scoped automatically
 * by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface CapacityRepository extends JpaRepository<Capacity, UUID> {

	Optional<Capacity> findByPublicUuid(UUID publicUuid);

	@Query("select c from Capacity c where c.competency = :competency "
			+ "order by c.displayOrder asc")
	List<Capacity> findAllByCompetencyOrderByDisplayOrderAsc(
			@Param("competency") Competency competency);

	@Query("select c from Capacity c where c.competency in :competencies "
			+ "order by c.competency.id, c.displayOrder asc")
	List<Capacity> findAllByCompetencyIn(
			@Param("competencies") List<Competency> competencies);

	@Query("select c from Capacity c where c.competency = :competency "
			+ "and lower(c.code) = lower(:code)")
	Optional<Capacity> findByCompetencyAndCodeIgnoreCase(
			@Param("competency") Competency competency,
			@Param("code") String code);

	@Query("select coalesce(max(c.displayOrder), 0) from Capacity c "
			+ "where c.competency = :competency")
	Integer findMaxDisplayOrderForCompetency(@Param("competency") Competency competency);

	@Query("select c from Capacity c where c.publicUuid in :uuids")
	List<Capacity> findAllByPublicUuidIn(@Param("uuids") List<UUID> uuids);
}
