package com.edushift.modules.disclosures.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewNoteRequest(@NotBlank @Size(max = 2000) String note) { }
