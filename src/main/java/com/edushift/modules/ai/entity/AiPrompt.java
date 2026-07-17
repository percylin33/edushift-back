package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_prompts", schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_ai_prompts_key_version", columnNames = {"template_key", "version"})
		})
@Getter
@Setter
@NoArgsConstructor
public class AiPrompt extends BaseEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "template_key", nullable = false, length = 64)
	private String templateKey;

	@Column(name = "version", nullable = false, length = 16)
	private String version;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "system_prompt", nullable = false, columnDefinition = "text")
	private String systemPrompt;

	@Column(name = "user_prompt_template", nullable = false, columnDefinition = "text")
	private String userPromptTemplate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@PrePersist
	public void autoAssignPublicUuid() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
