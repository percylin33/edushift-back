package com.edushift.modules.sessions.template.service;

import com.edushift.modules.sessions.template.dto.CreateSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplateResponse;
import com.edushift.modules.sessions.template.dto.UpdateSessionTemplateRequest;
import com.edushift.modules.sessions.template.entity.SessionTemplate;
import com.edushift.modules.sessions.template.repository.SessionTemplateRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionTemplateService {

    private static final String ERROR_KEY_TAKEN = "TEMPLATE_KEY_TAKEN";

    private final SessionTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<SessionTemplateResponse> listAll() {
        return repository.findAllByOrderByIsSystemDescNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionTemplateResponse get(UUID publicUuid) {
        SessionTemplate t = repository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("SessionTemplate", publicUuid));
        return toResponse(t);
    }

    @Transactional
    public SessionTemplateResponse create(CreateSessionTemplateRequest request) {
        if (repository.findByTemplateKey(request.templateKey()).isPresent()) {
            throw new ConflictException(ERROR_KEY_TAKEN,
                    "Template key '" + request.templateKey() + "' already exists");
        }
        SessionTemplate entity = new SessionTemplate();
        entity.setTemplateKey(request.templateKey());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSchema(request.schema());
        entity.setIsSystem(false);
        SessionTemplate saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public SessionTemplateResponse update(UUID publicUuid, UpdateSessionTemplateRequest request) {
        SessionTemplate entity = repository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("SessionTemplate", publicUuid));
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ConflictException("TEMPLATE_SYSTEM_READONLY",
                    "System templates cannot be modified");
        }
        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.schema() != null) entity.setSchema(request.schema());
        SessionTemplate saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID publicUuid) {
        SessionTemplate entity = repository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("SessionTemplate", publicUuid));
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new ConflictException("TEMPLATE_SYSTEM_READONLY",
                    "System templates cannot be deleted");
        }
        repository.delete(entity);
    }

    private SessionTemplateResponse toResponse(SessionTemplate t) {
        return new SessionTemplateResponse(
                t.getPublicUuid(),
                t.getTemplateKey(),
                t.getName(),
                t.getDescription(),
                t.getSchema(),
                Boolean.TRUE.equals(t.getIsSystem()),
                t.getCreatedAt()
        );
    }
}
