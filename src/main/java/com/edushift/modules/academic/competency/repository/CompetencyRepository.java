package com.edushift.modules.academic.competency.repository;

import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Competency}. Tenant-scoped automatically
 * by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

	Optional<Competency> findByPublicUuid(UUID publicUuid);

	@Query("select c from Competency c where c.course = :course "
			+ "order by c.displayOrder asc")
	List<Competency> findAllByCourseOrderByDisplayOrderAsc(@Param("course") Course course);

	@Query("select c from Competency c where c.course = :course "
			+ "and lower(c.code) = lower(:code)")
	Optional<Competency> findByCourseAndCodeIgnoreCase(@Param("course") Course course,
			@Param("code") String code);

	@Query("select c from Competency c where c.course = :course "
			+ "and c.displayOrder = :displayOrder")
	Optional<Competency> findByCourseAndDisplayOrder(@Param("course") Course course,
			@Param("displayOrder") Integer displayOrder);

	@Query("select coalesce(max(c.displayOrder), 0) from Competency c "
			+ "where c.course = :course")
	Integer findMaxDisplayOrderForCourse(@Param("course") Course course);

	@Query("select count(c) from Competency c where c.course = :course")
	long countByCourse(@Param("course") Course course);

	@Query("select c from Competency c where c.publicUuid in :uuids")
	List<Competency> findAllByPublicUuidIn(@Param("uuids") List<UUID> uuids);
}
