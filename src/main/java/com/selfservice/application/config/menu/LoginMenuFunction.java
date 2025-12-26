package com.selfservice.application.config.menu;

import java.util.Locale;

public enum LoginMenuFunction {
    DIGITAL_LOGIN,
    CRM_LOGIN,
    OPT_IN,
    CHANGE_LANGUAGE,
    SETTINGS,
    HOME;

    public static LoginMenuFunction fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LoginMenuFunction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
