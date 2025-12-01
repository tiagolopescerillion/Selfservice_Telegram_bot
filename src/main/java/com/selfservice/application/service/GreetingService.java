package com.selfservice.application.service;

import org.springframework.stereotype.Service;

/**
 * Provides a simple demonstration service for connectivity checks.
 */
@Service
public class GreetingService {

    /**
     * Returns a static greeting used by health or smoke tests.
     */
    public String helloCerillion() {
        return "Hello Cerillion";
    }
}
