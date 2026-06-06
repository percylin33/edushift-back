package com.edushift.modules.academic.year.repository;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AcademicYear}.
 *
 * <p>Hibernate auto-scopes every query by {@code tenant_id} via the
 * {@code @TenantId} discriminator, so finders here never need to join
 * tenants or filter manually.</p>
 */
@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

	Optional<AcademicYear> findByPublicUuid(UUID publicUuid);

	@Query("select y from AcademicYear y where lower(y.name) = lower(:name)")
	Optional<AcademicYear> findByNameIgnoreCase(@Param("name") String name);

	Optional<AcademicYear> findFirstByStatus(AcademicYearStatus status);

	List<AcademicYear> findAllByOrderByStartDateDesc();

	List<AcademicYear> findAllByStatusOrderByStartDateDesc(AcademicYearStatus status);
}
