package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Client client = new Client();
    private Resend resend = new Resend();
    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private Cookie cookie = new Cookie();
    private Storage storage = new Storage();
    private Media media = new Media();
    private Call call = new Call();
    private Presence presence = new Presence();

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "./uploads";
        private long maxAvatarMb = 2;
    }

    @Getter
    @Setter
    public static class Call {
        private int ringTimeoutSeconds = 30;
        private int connectTimeoutSeconds = 45;
        private int disconnectGraceSeconds = 15;
        private String stunServer = "stun:stun.l.google.com:19302";
        private String turnUrls = "";
        private String turnSharedSecret = "";
        private long turnCredentialTtlSeconds = 3600;
    }

    @Getter
    @Setter
    public static class Presence {
        private int offlineGraceSeconds = 5;
        private int typingTtlSeconds = 7;
    }

    @Getter
    @Setter
    public static class Media {
        private String uploadDir = "./uploads";
        private long maxImageSize = 10;
        private long maxVideoSize = 50;
        private long maxFileSize = 25;
        private long maxVoiceSize = 10;

        public long maxBytes(com.vtchat.entity.MessageType type) {
            long mb = switch (type) {
                case IMAGE -> maxImageSize;
                case VIDEO -> maxVideoSize;
                case VOICE -> maxVoiceSize;
                default -> maxFileSize;
            };
            return mb * 1024L * 1024L;
        }
    }

    @Getter
    @Setter
    public static class Client {
        private String url = "http://localhost:5173";
    }

    @Getter
    @Setter
    public static class Resend {
        private String apiKey = "";
        private String emailFrom = "";
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private long accessExpiration = 900_000L;
        private String refreshSecret = "";
        private long refreshExpiration = 604_800_000L;
    }

    @Getter
    @Setter
    public static class Otp {
        private String secret = "";
        private long expirationSeconds = 300;
        private int maxAttempts = 5;
        private long resendCooldownSeconds = 60;
    }

    @Getter
    @Setter
    public static class Cookie {
        private String name = "vtchat_refresh";
        private boolean secure = false;
        private String sameSite = "Lax";
    }
}
