package com.selfservice.telegrambot;




import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SelfserviceTelegramBotApplication {
    public static void main(String[] args) {
        // Tell Spring to also load configs from your CONFIGURATIONS/ folder
        System.setProperty("spring.config.additional-location", "file:CONFIGURATIONS/");
        SpringApplication.run(SelfserviceTelegramBotApplication.class, args);
    }
}
