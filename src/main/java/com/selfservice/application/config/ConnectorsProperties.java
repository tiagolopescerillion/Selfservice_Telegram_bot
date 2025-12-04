package com.selfservice.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "connectors")
public class ConnectorsProperties {

    private Boolean whatsapp = true;
    private Boolean telegram = true;
    private Boolean messenger = true;

    public boolean isWhatsappEnabled() {
        return whatsapp == null || whatsapp;
    }

    public boolean isTelegramEnabled() {
        return telegram == null || telegram;
    }

    public boolean isMessengerEnabled() {
        return messenger == null || messenger;
    }

    public void setWhatsapp(Boolean whatsapp) {
        this.whatsapp = whatsapp;
    }

    public void setTelegram(Boolean telegram) {
        this.telegram = telegram;
    }

    public void setMessenger(Boolean messenger) {
        this.messenger = messenger;
    }
}
