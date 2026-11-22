package com.incidentdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 4_000) String description) {
}
