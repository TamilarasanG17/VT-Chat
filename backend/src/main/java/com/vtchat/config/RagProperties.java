package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enabled = true;
    private int maxDocumentSizeMb = 20;
    private int maxDocumentsPerUser = 50;
    private int maxQueryLength = 2000;
    private int maxDocumentIds = 100;
    private int processTimeoutMs = 600000;
    private int stuckAfterMinutes = 20;
    private int processingThreads = 2;
    private int accessTokenTtlSeconds = 3600;

    public long maxDocumentBytes() {
        return maxDocumentSizeMb * 1024L * 1024L;
    }
}
