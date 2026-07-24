package com.edushift.modules.academic.classrooms.repository;

import com.edushift.modules.academic.classrooms.entity.Classroom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

	Optional<Classroom> findByPublicUuidAndDeletedFalse(UUID publicUuid);

	Optional<Classroom> findByPublicUuid(UUID publicUuid);

	Optional<Classroom> findByCodeAndDeletedFalse(String code);

	Page<Classroom> findByDeletedFalseOrderByCodeAsc(Pageable pageable);

	List<Classroom> findByDeletedFalseAndActiveTrueOrderByCodeAsc();
}