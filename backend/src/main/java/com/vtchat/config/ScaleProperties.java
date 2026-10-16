package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vtchat")
public class ScaleProperties {

    private String instanceId = "instance-1";
    private Redis redis = new Redis();

    @Getter
    @Setter
    public static class Redis {
        private boolean enabled = true;
        private long cacheTtlSeconds = 300;
        private long searchCacheTtlSeconds = 30;
        private long unreadTtlSeconds = 60;
        private long sessionTtlSeconds = 90;
        private long heartbeatSeconds = 30;
        private int maxEventBytes = 262_144;
        private Channels channels = new Channels();
    }

    @Getter
    @Setter
    public static class Channels {
        private String messages = "vtchat:messages";
        private String notifications = "vtchat:notifications";
        private String calls = "vtchat:calls";
        private String readState = "vtchat:read-state";
        private String presence = "vtchat:presence";
    }
}
