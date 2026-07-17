package com.edushift.modules.admin.plans;

import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformPlanService {

    private final PlatformPlanRepository planRepository;

    public List<PlanResponse> listAll() {
        return planRepository.findByIsActiveTrueOrderBySortOrder().stream()
                .map(PlanResponse::from)
                .toList();
    }

    public PlanResponse getById(UUID id) {
        return planRepository.findById(id)
                .map(PlanResponse::from)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        if (planRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("PLAN_CODE_TAKEN", "Plan code already exists");
        }

        PlatformPlan plan = new PlatformPlan();
        plan.setName(request.name());
        plan.setCode(request.code().toUpperCase());
        plan.setDescription(request.description());
        plan.setPricePerStudentCents(request.pricePerStudentCents());
        plan.setMaxStudents(request.maxStudents());
        plan.setMaxTeachers(request.maxTeachers());
        plan.setMaxStorageMb(request.maxStorageMb());
        plan.setFeatures(request.features());
        plan.setSortOrder(request.sortOrder());
        plan.setActive(true);

        plan = planRepository.save(plan);
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse update(UUID id, UpdatePlanRequest request) {
        PlatformPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));

        if (request.name() != null) plan.setName(request.name());
        if (request.code() != null) plan.setCode(request.code().toUpperCase());
        if (request.description() != null) plan.setDescription(request.description());
        if (request.pricePerStudentCents() != null) plan.setPricePerStudentCents(request.pricePerStudentCents());
        if (request.maxStudents() != null) plan.setMaxStudents(request.maxStudents());
        if (request.maxTeachers() != null) plan.setMaxTeachers(request.maxTeachers());
        if (request.maxStorageMb() != null) plan.setMaxStorageMb(request.maxStorageMb());
        if (request.features() != null) plan.setFeatures(request.features());
        if (request.sortOrder() != null) plan.setSortOrder(request.sortOrder());
        if (request.isActive() != null) plan.setActive(request.isActive());

        plan = planRepository.save(plan);
        return PlanResponse.from(plan);
    }

    @Transactional
    public void deactivate(UUID id) {
        PlatformPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));
        plan.setActive(false);
        planRepository.save(plan);
    }
}
