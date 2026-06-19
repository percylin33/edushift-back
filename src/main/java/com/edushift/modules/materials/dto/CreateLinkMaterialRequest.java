package com.edushift.modules.materials.dto;

import com.edushift.modules.materials.entity.MaterialKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URL;

/**
 * JSON request body for a {@code kind=VIDEO_LINK} material
 * (Sprint 7a / BE-7a.1). The URL is parsed (not regex'd) so we let
 * {@link URL#URL(String)} do the heavy lifting and reject anything
 * the JDK considers malformed.
 *
 * <p>Host whitelist is a future hardening (DEBT-7A-5). Today any
 * parseable URL is accepted.
 */
public record CreateLinkMaterialRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 2000) String description,
		@NotNull MaterialKind kind,
		@NotBlank @Size(max = 2048) String externalUrl
) {
}
