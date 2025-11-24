package com.selfservice.telegrambot.controller;

import com.selfservice.telegrambot.service.OperationsMonitoringService;
import com.selfservice.telegrambot.service.OperationsMonitoringService.SessionSnapshot;
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

    public OperationsMonitoringController(OperationsMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/sessions")
    public Snapshot getSessions() {
        return new Snapshot(monitoringService.getActiveSessions(), monitoringService.getRecentSessions());
    }

    public record Snapshot(List<SessionSnapshot> active, List<SessionSnapshot> history) { }
}
