package OrangeCloud.AuthService.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * User Service HTTP Client
 * OAuth 로그인 시 사용자 조회/생성을 위해 user-service를 호출
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceUrl;

    /**
     * OAuth 로그인 시 사용자 조회 또는 생성
     * user-service의 /api/internal/oauth/login 엔드포인트 호출
     *
     * @param email    사용자 이메일
     * @param name     사용자 이름
     * @param provider OAuth 제공자 (google 등)
     * @return 사용자 ID (UUID)
     */
    public UUID findOrCreateOAuthUser(String email, String name, String provider) {
        log.debug("Calling user-service to find or create OAuth user: email={}, provider={}", email, provider);

        String url = userServiceUrl + "/api/internal/oauth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = Map.of(
                "email", email,
                "name", name,
                "provider", provider
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String userIdStr = (String) response.getBody().get("userId");
                UUID userId = UUID.fromString(userIdStr);
                log.info("User found/created successfully: userId={}", userId);
                return userId;
            }

            throw new RuntimeException("Failed to get user from user-service");
        } catch (Exception e) {
            log.error("Error calling user-service: {}", e.getMessage(), e);
            throw new RuntimeException("User service communication error", e);
        }
    }

    /**
     * 사용자 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    public boolean userExists(UUID userId) {
        log.debug("Checking if user exists: userId={}", userId);

        String url = userServiceUrl + "/api/internal/users/" + userId + "/exists";

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody().get("exists"));
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking user existence: {}", e.getMessage());
            return false;
        }
    }
}
