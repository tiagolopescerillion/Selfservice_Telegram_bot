package com.selfservice.application.config.menu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginMenuItem {
    private int order;
    private String label;
    private LoginMenuFunction function;
    private String translationKey;
    private String callbackData;

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LoginMenuFunction getFunction() {
        return function;
    }

    public void setFunction(LoginMenuFunction function) {
        this.function = function;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public void setTranslationKey(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getCallbackData() {
        return callbackData;
    }

    public void setCallbackData(String callbackData) {
        this.callbackData = callbackData;
    }

    public LoginMenuFunction resolvedFunction() {
        return function;
    }
}
