package com.edushift.modules.academic.course.repository;

import com.edushift.modules.academic.course.entity.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Course}. Tenant-scoped automatically
 * by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

	Optional<Course> findByPublicUuid(UUID publicUuid);

	@Query("select c from Course c where lower(c.code) = lower(:code)")
	Optional<Course> findByCodeIgnoreCase(@Param("code") String code);

	@Query("select c from Course c order by lower(c.name) asc")
	List<Course> findAllSorted();

	@Query("select c from Course c where c.isActive = :active order by lower(c.name) asc")
	List<Course> findAllByIsActiveSorted(@Param("active") boolean isActive);

	/**
	 * Returns courses that are linked to the given level (via course_levels
	 * pivot). Ordered by name asc.
	 */
	@Query("""
			select c from Course c
			where exists (
				select 1 from CourseLevel cl
				where cl.course = c and cl.level.publicUuid = :levelPublicUuid
			)
			order by lower(c.name) asc
			""")
	List<Course> findAllByLevelPublicUuid(@Param("levelPublicUuid") UUID levelPublicUuid);
}
