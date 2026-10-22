package com.edushift.modules.disclosures.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SaveDisclosureRequest(@NotNull @Valid DisclosureContent content) { }
