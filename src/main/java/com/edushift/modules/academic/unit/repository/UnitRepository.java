package com.edushift.modules.academic.unit.repository;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.unit.entity.Unit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Unit}. Tenant-scoped automatically
 * by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {

	Optional<Unit> findByPublicUuid(UUID publicUuid);

	@Query("select u from Unit u where u.course = :course "
			+ "order by u.displayOrder asc")
	List<Unit> findAllByCourseOrderByDisplayOrderAsc(@Param("course") Course course);

	@Query("select u from Unit u where u.course = :course "
			+ "and lower(u.name) = lower(:name)")
	Optional<Unit> findByCourseAndNameIgnoreCase(@Param("course") Course course,
			@Param("name") String name);

	@Query("select u from Unit u where u.course = :course "
			+ "and u.displayOrder = :displayOrder")
	Optional<Unit> findByCourseAndDisplayOrder(@Param("course") Course course,
			@Param("displayOrder") Integer displayOrder);

	@Query("select coalesce(max(u.displayOrder), 0) from Unit u where u.course = :course")
	Integer findMaxDisplayOrderForCourse(@Param("course") Course course);
}
