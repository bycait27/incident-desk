package com.incidentdesk.controllers;

import com.incidentdesk.services.BetterStackMonitorService;
import com.incidentdesk.services.BetterStackMonitorService.MonitorStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uptime")
public class BetterStackMonitorController {

    private final BetterStackMonitorService monitorService;

    public BetterStackMonitorController(BetterStackMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/monitors")
    public MonitorStatusResponse getMonitors() {
        return monitorService.getMonitors();
    }
}
