package com.selfservice.telegrambot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final String TRANSLATION_PATH = "classpath:/i18n/*.json";
    private static final String DEFAULT_LANGUAGE = "en";

    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();

    public TranslationService(ObjectMapper objectMapper) {
        loadTranslations(objectMapper);
    }

    private void loadTranslations(ObjectMapper objectMapper) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TRANSLATION_PATH);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".json")) {
                    continue;
                }
                String language = filename.substring(0, filename.length() - 5);
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, String> map = objectMapper.readValue(inputStream, new TypeReference<>() {
                    });
                    translations.put(language, map);
                    log.info("Loaded {} translation keys for language {}", map.size(), language);
                } catch (IOException e) {
                    log.warn("Failed to load translation file for language {}", language, e);
                }
            }
        } catch (IOException e) {
            log.error("Unable to load translation resources", e);
        }

        translations.computeIfAbsent(DEFAULT_LANGUAGE, lang -> Map.of());
    }

    public String getDefaultLanguage() {
        return DEFAULT_LANGUAGE;
    }

    public boolean isSupportedLanguage(String language) {
        return translations.containsKey(language);
    }

    public String get(String language, String key) {
        if (language != null) {
            Map<String, String> map = translations.get(language);
            if (map != null && map.containsKey(key)) {
                return map.get(key);
            }
        }
        Map<String, String> defaultMap = translations.get(DEFAULT_LANGUAGE);
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return defaultMap.get(key);
        }
        return key;
    }

    public boolean hasTranslation(String language, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (language != null) {
            Map<String, String> map = translations.get(language);
            if (map != null && map.containsKey(key)) {
                return true;
            }
        }
        Map<String, String> defaultMap = translations.get(DEFAULT_LANGUAGE);
        return defaultMap != null && defaultMap.containsKey(key);
    }

    public String format(String language, String key, Object... args) {
        String template = get(language, key);
        Locale locale = Locale.forLanguageTag(language == null ? DEFAULT_LANGUAGE : language);
        try {
            return String.format(locale, template, args);
        } catch (Exception ex) {
            return String.format(Locale.ENGLISH, template, args);
        }
    }
}
