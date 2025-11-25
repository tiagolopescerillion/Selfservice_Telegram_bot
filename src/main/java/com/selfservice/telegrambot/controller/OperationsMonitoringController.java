package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.OperationsMonitoringService;
import com.selfservice.telegrambot.service.OperationsMonitoringService.SessionSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/operations")
@CrossOrigin
public class OperationsMonitoringController {

    private final OperationsMonitoringService monitoringService;
    private final String publicBaseUrl;

    public OperationsMonitoringController(OperationsMonitoringService monitoringService,
                                         @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.monitoringService = monitoringService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping("/sessions")
    public Snapshot getSessions() {
        return new Snapshot(monitoringService.getActiveSessions(), monitoringService.getRecentSessions());
    }

    @GetMapping("/config")
    public MonitoringConfig getConfig() {
        return new MonitoringConfig(publicBaseUrl);
    }

    public record Snapshot(List<SessionSnapshot> active, List<SessionSnapshot> history) { }

    public record MonitoringConfig(String publicBaseUrl) { }
}
