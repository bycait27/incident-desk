package com.incidentdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IncidentUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 4_000) String description,
        @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
        Long assignedTo) {
}
