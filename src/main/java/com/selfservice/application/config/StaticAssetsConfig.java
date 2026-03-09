package com.selfservice.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticAssetsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path assetsPath = Paths.get("assets").toAbsolutePath().normalize();
        String location = assetsPath.toUri().toString();
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
