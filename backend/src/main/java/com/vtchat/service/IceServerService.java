package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.dto.call.IceServersResponse;
import com.vtchat.dto.call.IceServersResponse.IceServer;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
public class IceServerService {

    private final AppProperties.Call config;

    public IceServerService(AppProperties props) {
        this.config = props.getCall();
    }

    public IceServersResponse forUser(Long userId) {
        List<IceServer> servers = new ArrayList<>();
        String stun = config.getStunServer();
        if (stun != null && !stun.isBlank()) {
            servers.add(new IceServer(List.of(stun.trim()), null, null));
        }
        List<String> turn = Arrays.stream(config.getTurnUrls() == null ? new String[0] : config.getTurnUrls().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        String secret = config.getTurnSharedSecret();
        if (!turn.isEmpty() && secret != null && !secret.isBlank()) {
            String username = (Instant.now().getEpochSecond() + config.getTurnCredentialTtlSeconds()) + ":" + userId;
            servers.add(new IceServer(turn, username, sign(secret, username)));
        }
        return new IceServersResponse(servers);
    }

    private static String sign(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign TURN credentials", e);
        }
    }
}
