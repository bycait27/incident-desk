package com.incidentdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record McpToolAuditRequest(
        @NotBlank
        @Pattern(regexp = "list_incidents|get_incident|check_monitor_status|submit_incident")
        String toolName,
        @NotBlank
        @Pattern(regexp = "STARTED|AWAITING_CONFIRMATION|SUCCEEDED|FAILED|DENIED|CANCELLED")
        String outcome,
        Long resourceId) {
}
