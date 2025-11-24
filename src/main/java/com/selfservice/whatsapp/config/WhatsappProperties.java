package com.selfservice.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsappProperties {

    public enum WhatsappUxMode {
        BASIC,
        PRODUCTION,
        TEST;

        public static WhatsappUxMode from(String raw) {
            if (raw == null) {
                return TEST;
            }
            try {
                return WhatsappUxMode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return TEST;
            }
        }
    }

    private final WhatsappUxMode uxMode;

    public WhatsappProperties(@Value("${whatsapp.ux-mode:TEST}") String uxMode) {
        this.uxMode = WhatsappUxMode.from(uxMode);
    }

    public WhatsappUxMode getUxMode() {
        return uxMode;
    }

    public boolean isBasicUxEnabled() {
        return uxMode == WhatsappUxMode.BASIC || uxMode == WhatsappUxMode.TEST;
    }

    public boolean isInteractiveUxEnabled() {
        return uxMode == WhatsappUxMode.PRODUCTION || uxMode == WhatsappUxMode.TEST;
    }
}
