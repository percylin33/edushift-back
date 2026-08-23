package com.edushift.modules.schedule.daytemplate.repository;

import com.edushift.modules.schedule.daytemplate.entity.DayScheduleBlock;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DayScheduleBlockRepository extends JpaRepository<DayScheduleBlock, UUID> {

	Optional<DayScheduleBlock> findByPublicUuid(UUID publicUuid);

	@Query("""
			select b from DayScheduleBlock b
			where b.template = :template
			  and b.deleted = false
			order by b.dayOfWeek asc nulls first, b.startTime asc
			""")
	List<DayScheduleBlock> findByTemplateOrdered(@Param("template") DayScheduleTemplate template);

	List<DayScheduleBlock> findByTemplate_PublicUuidOrderByStartTimeAsc(UUID templatePublicUuid);
}
