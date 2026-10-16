package com.vtchat.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile({"prod", "production"})
@RequiredArgsConstructor
public class ProductionSafetyCheck {

    private final AppProperties app;

    @PostConstruct
    void check() {
        List<String> problems = new ArrayList<>();
        String clientUrls = app.getClient().getUrl() == null ? "" : app.getClient().getUrl();
        for (String raw : clientUrls.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            try {
                URI uri = URI.create(origin);
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    problems.add("CLIENT_URL entries must be https URLs (found a non-https origin)");
                } else if (host.equals("localhost") || host.equals("127.0.0.1") || origin.contains("*")) {
                    problems.add("CLIENT_URL must not contain localhost or wildcards in production");
                }
            } catch (IllegalArgumentException e) {
                problems.add("CLIENT_URL contains an invalid URL");
            }
        }
        if (clientUrls.isBlank()) {
            problems.add("CLIENT_URL is empty");
        }
        if (!app.getCookie().isSecure()) {
            problems.add("COOKIE_SECURE must be true in production");
        }
        if (blank(app.getJwt().getSecret()) || blank(app.getJwt().getRefreshSecret())) {
            problems.add("JWT_SECRET and JWT_REFRESH_SECRET must be set");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
