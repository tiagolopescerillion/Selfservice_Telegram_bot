package com.selfservice.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Profile "oauth" → enable RHSSO / Keycloak login
    @Bean
    @Profile("oauth")
    public SecurityFilterChain oauthChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/webhook/**", "/actuator/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login();
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }

    
    // Default profile → no auth (local development)
    @Bean
    @Profile("!oauth")
    public SecurityFilterChain devChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }
}
