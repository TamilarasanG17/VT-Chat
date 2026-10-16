package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "translation")
public class TranslationProperties {

    private Service service = new Service();
    private int maxTextLength = 2000;
    private Map<String, String> languages = defaultLanguages();

    @Getter
    @Setter
    public static class Service {
        private String url = "http://localhost:8001";
        private String apiKey = "";
        private int timeoutMs = 10000;
        private int connectTimeoutMs = 2000;
    }

    private static Map<String, String> defaultLanguages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("en", "English");
        m.put("ta", "Tamil");
        m.put("hi", "Hindi");
        m.put("ml", "Malayalam");
        m.put("kn", "Kannada");
        return m;
    }
}
