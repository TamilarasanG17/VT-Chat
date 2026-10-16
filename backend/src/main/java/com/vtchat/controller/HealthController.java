package com.vtchat.controller;

import com.vtchat.config.ScaleProperties;
import com.vtchat.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisService redis;
    private final ScaleProperties scale;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean mysqlUp = mysqlUp();
        String redisState = !redis.isEnabled() ? "DISABLED" : (redis.ping() ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", !mysqlUp ? "DOWN" : ("DOWN".equals(redisState) ? "DEGRADED" : "UP"));
        body.put("mysql", mysqlUp ? "UP" : "DOWN");
        body.put("redis", redisState);
        body.put("websocket", "available");
        body.put("instance", scale.getInstanceId());
        return ResponseEntity.status(mysqlUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean mysqlUp() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            log.warn("MySQL health check failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
