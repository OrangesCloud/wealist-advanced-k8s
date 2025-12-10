package OrangeCloud.AuthService.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoints for Kubernetes probes and docker-compose health checks.
 * Provides /health (liveness) and /ready (readiness) endpoints
 * that are consistent with Go services.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    /**
     * Liveness probe - checks if the application is running.
     * Returns 200 if the application is alive.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "auth-service"
        ));
    }

    /**
     * Readiness probe - checks if the application is ready to serve traffic.
     * Checks Redis connection via Spring Boot Actuator health.
     * Returns 200 if ready, 503 if not ready.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        try {
            var health = healthEndpoint.health();
            boolean isUp = Status.UP.equals(health.getStatus());

            if (isUp) {
                return ResponseEntity.ok(Map.of(
                        "status", "ready",
                        "service", "auth-service",
                        "details", health.getStatus().getCode()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "status", "not_ready",
                                "service", "auth-service",
                                "details", health.getStatus().getCode()
                        ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "not_ready",
                            "service", "auth-service",
                            "error", e.getMessage()
                    ));
        }
    }
}
