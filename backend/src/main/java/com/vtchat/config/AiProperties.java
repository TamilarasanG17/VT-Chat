package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Service service = new Service();
    private int maxMessageLength = 4000;
    private int maxContextMessages = 20;
    private int maxConversationsPerUser = 100;
    private int streamMaxMs = 120000;

    @Getter
    @Setter
    public static class Service {
        private String url = "http://localhost:8002";
        private String apiKey = "";
        private int timeoutMs = 30000;
        private int connectTimeoutMs = 2000;
    }
}
