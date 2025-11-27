package com.selfservice.telegrambot.controller;

import com.selfservice.application.config.ApimanEndpointsProperties;
import com.selfservice.telegrambot.dto.ServiceFunctionDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class ServiceFunctionController {

    private final ApimanEndpointsProperties apimanEndpoints;

    public ServiceFunctionController(ApimanEndpointsProperties apimanEndpoints) {
        this.apimanEndpoints = apimanEndpoints;
    }

    @GetMapping("/admin/service-functions")
    public Map<String, Object> listServiceFunctions() {
        List<ServiceFunctionDescriptor> endpoints = new ArrayList<>();

        addEndpoint(endpoints, "apiman.find-user.url", "Find user (APIMAN)",
                apimanEndpoints.getFindUserUrl(), List.of("FindUserService"));

        addEndpoint(endpoints, "apiman.account-services.url", "Account services (APIMAN)",
                apimanEndpoints.getAccountServicesUrl(), List.of("MainServiceCatalogService"));

        addEndpoint(endpoints, "apiman.trouble-ticket.url", "Trouble ticket (APIMAN)",
                apimanEndpoints.getTroubleTicketUrl(), List.of("TroubleTicketService"));

        return Map.of("endpoints", endpoints);
    }

    private void addEndpoint(List<ServiceFunctionDescriptor> endpoints, String key, String name, String url,
                             List<String> services) {
        boolean configured = url != null && !url.isBlank();
        endpoints.add(new ServiceFunctionDescriptor(key, name, configured ? url : null, configured, services));
    }
}
