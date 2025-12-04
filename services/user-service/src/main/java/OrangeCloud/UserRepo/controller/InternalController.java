package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal API Controller
 * 서비스 간 통신용 내부 API (auth-service에서 호출)
 * 외부에 노출되지 않음 (Swagger에서 숨김)
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
@Hidden // Swagger에서 숨김
@Tag(name = "Internal", description = "서비스 간 통신용 내부 API")
public class InternalController {

    private final UserService userService;

    /**
     * OAuth 로그인 시 사용자 조회 또는 생성
     * auth-service에서 호출
     */
    @PostMapping("/oauth/login")
    @Operation(summary = "OAuth 로그인", description = "OAuth 인증 후 사용자 조회/생성")
    public ResponseEntity<Map<String, String>> oauthLogin(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String name = request.get("name");
        String provider = request.get("provider");

        log.info("Internal OAuth login request: email={}, provider={}", email, provider);

        // provider를 googleId로 사용 (임시)
        // 실제로는 Google에서 받은 sub(googleId)를 별도로 전달해야 함
        User user = userService.findOrCreateUserByGoogle(email, email, name);

        log.info("OAuth login successful: userId={}", user.getUserId());

        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId().toString(),
                "email", user.getEmail()
        ));
    }

    /**
     * 사용자 존재 여부 확인
     */
    @GetMapping("/users/{userId}/exists")
    @Operation(summary = "사용자 존재 확인", description = "사용자 ID로 존재 여부 확인")
    public ResponseEntity<Map<String, Boolean>> userExists(@PathVariable UUID userId) {
        log.debug("Checking user existence: userId={}", userId);

        try {
            userService.getUserById(userId);
            return ResponseEntity.ok(Map.of("exists", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("exists", false));
        }
    }
}
