package com.edushift.modules.ai.mapper;

import com.edushift.modules.ai.dto.AiPromptResponse;
import com.edushift.modules.ai.entity.AiPrompt;
import org.springframework.stereotype.Component;

/**
 * Pure-function mapper for {@link AiPrompt} ↔ {@link AiPromptResponse}
 * (Sprint 18 / BE-18.5).
 *
 * <p>Kept as a stateless Spring component (rather than static helpers)
 * so the controller / service layer can {@code @Autowired} it once
 * and reuse the same instance — simpler to mock in unit tests.</p>
 */
@Component
public class AiPromptMapper {

	public AiPromptResponse toResponse(AiPrompt p) {
		return new AiPromptResponse(
				p.getPublicUuid(),
				p.getTemplateKey(),
				p.getVersion(),
				p.getDescription(),
				p.getSystemPrompt(),
				p.getUserPromptTemplate(),
				p.isActive(),
				p.getCreatedAt(),
				p.getUpdatedAt());
	}
}
