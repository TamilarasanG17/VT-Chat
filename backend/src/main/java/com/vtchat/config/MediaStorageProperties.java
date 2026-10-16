package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "media-storage")
public class MediaStorageProperties {

    private String provider = "local";
    private boolean allowLocalInProduction = false;
    private Cloudinary cloudinary = new Cloudinary();

    public boolean isCloudinary() {
        return "cloudinary".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    public boolean isLocal() {
        return "local".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    @Getter
    @Setter
    public static class Cloudinary {
        private String cloudName = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String folder = "vtchat";
        private int apiTimeoutMs = 20000;
        private int uploadTimeoutMs = 300000;
        private int downloadTimeoutMs = 60000;
        private int connectTimeoutMs = 5000;
        private boolean verifyOnStartup = true;

        @Override
        public String toString() {
            return "Cloudinary{cloud=" + cloudName + ", folder=" + folder + ", credentials=****}";
        }
    }
}
