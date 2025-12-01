package com.selfservice.application.controller;

import com.selfservice.application.config.menu.BusinessMenuConfiguration;
import com.selfservice.application.config.menu.BusinessMenuConfigurationProvider;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menu-config")
@CrossOrigin
public class MenuConfigurationController {

    private final BusinessMenuConfigurationProvider configurationProvider;

    public MenuConfigurationController(BusinessMenuConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    @GetMapping
    public BusinessMenuConfiguration getMenuConfiguration() {
        return configurationProvider.getEffectiveConfiguration();
    }

    @GetMapping("/default")
    public BusinessMenuConfiguration getDefaultMenuConfiguration() {
        return configurationProvider.getDefaultConfiguration();
    }
}

