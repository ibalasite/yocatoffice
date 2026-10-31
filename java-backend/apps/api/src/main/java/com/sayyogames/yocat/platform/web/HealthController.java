package com.sayyogames.yocat.platform.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform liveness / readiness endpoints (API §3 平台底座: {@code GET /health/live}, {@code GET /health/ready};
 * EDD §3.6 probes). Liveness reflects the process being up; readiness probes the critical dependencies (the
 * database, and Redis which degrades open) and returns 503 {@code {"status":"DOWN"}} when the DB is unreachable
 * so Kubernetes can gate traffic. Distinct from the actuator probes under {@code /actuator/health}.
 */
@RestController
@RequestMapping(path = "/health", produces = "application/json")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/live")
    public Map<String, Object> live() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean dbUp = checkDb();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("db", dbUp ? "UP" : "DOWN");
        // Redis degrades open (EDD §8.3 circuit breaker: cache failure never fails a request), so readiness
        // treats it as UP; a hard DB failure is the only not-ready condition.
        body.put("redis", "UP");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkDb() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (RuntimeException dbDown) {
            return false;
        }
    }
}
