// user-service/src/main/java/OrangeCloud/UserRepo/controller/AuthController.java
// ⚠️ 토큰 관련 API(logout, refresh, validate)는 auth-service에서 처리합니다.
// 이 컨트롤러는 현재 사용자 정보 조회만 담당합니다.

package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Authentication", description = "인증 관련 API (user-service)")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 현재 인증된 사용자 정보 조회
     */
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 정보를 조회합니다.")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        logger.debug("Received request to get current user info for ID: {}", userId);
        User user = authService.getCurrentUser(userId);
        logger.info("사용자 정보 조회 성공: {}", userId);
        return ResponseEntity.ok(user);
    }
}