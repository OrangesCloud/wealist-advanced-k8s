package OrangeCloud.UserRepo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.info("Health check endpoint was called.");
        Map<String, Object> response = new HashMap<>();
        Map<String, String> checks = new HashMap<>();

        try {
            // 기본 상태
            response.put("status", "UP");
            response.put("timestamp", LocalDateTime.now());
            response.put("service", "UserRepo");

            // 데이터베이스 연결 확인
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    checks.put("database", "UP");
                } else {
                    checks.put("database", "DOWN");
                }
            } catch (Exception e) {
                checks.put("database", "DOWN - " + e.getMessage());
            }

            response.put("checks", checks);
            response.put("message", "Health check completed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }
}