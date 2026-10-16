package com.vtchat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean trustForwardedHeaders = false;

    private Rule auth = new Rule(10, 900);
    private Rule otp = new Rule(20, 900);
    private Rule search = new Rule(30, 60);
    private Rule message = new Rule(60, 60);
    private Rule media = new Rule(20, 60);
    private Rule translation = new Rule(20, 60);
    private Rule callSignal = new Rule(60, 60);
    private Rule callIce = new Rule(600, 60);
    private Rule notification = new Rule(120, 60);
    private Rule chatAction = new Rule(240, 60);
    private Rule typing = new Rule(240, 60);
    private Rule ai = new Rule(20, 60);
    private Rule ragUpload = new Rule(5, 60);
    private Rule ragReprocess = new Rule(5, 60);
    private Rule ragQuery = new Rule(20, 60);

    @Getter
    @Setter
    public static class Rule {
        private int limit;
        private int windowSeconds;

        public Rule() {
        }

        public Rule(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }
    }
}
