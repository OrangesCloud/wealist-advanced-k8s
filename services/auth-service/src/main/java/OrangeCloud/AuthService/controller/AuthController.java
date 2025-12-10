package OrangeCloud.AuthService.controller;

import OrangeCloud.AuthService.dto.*;
import OrangeCloud.AuthService.exception.InvalidTokenException;
import OrangeCloud.AuthService.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Authentication", description = "인증 관련 API")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 세션을 종료하고 토큰을 무효화합니다.")
    public ResponseEntity<MessageApiResponse> logout(HttpServletRequest request) {
        log.debug("Received logout request.");
        String token = extractTokenFromRequest(request);
        authService.logout(token);
        log.info("로그아웃 성공");
        return ResponseEntity.ok(new MessageApiResponse(true, "로그아웃 성공"));
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token을 사용하여 Access Token을 갱신합니다.")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        log.debug("Received refresh token request.");
        AuthResponse authResponse = authService.refreshToken(refreshRequest.getRefreshToken());
        log.info("토큰 갱신 성공");
        return ResponseEntity.ok(authResponse);
    }

    /**
     * 외부 서비스용 Access Token 유효성 검증
     * POST /api/auth/validate
     * Request Body: {"token": "..."}
     */
    @PostMapping("/validate")
    @Operation(summary = "토큰 검증", description = "다른 서비스에서 JWT 검증 (POST Body 방식)")
    public ResponseEntity<TokenValidationResponse> validateToken(@RequestBody Map<String, String> request) {
        log.debug("Received token validation request (POST).");

        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            log.warn("Token is missing in request body.");
            return ResponseEntity.ok(new TokenValidationResponse(null, false, "Token is required"));
        }

        try {
            UUID userId = authService.validateTokenAndGetUserId(token);
            log.info("Token validation successful for user ID: {}", userId);
            return ResponseEntity.ok(new TokenValidationResponse(userId.toString(), true, "Token is valid"));
        } catch (InvalidTokenException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.ok(new TokenValidationResponse(null, false, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during token validation", e);
            return ResponseEntity.ok(new TokenValidationResponse(null, false, "Internal server error"));
        }
    }

    /**
     * Request에서 Bearer 토큰 추출
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        log.debug("Attempting to extract token from request.");
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("Token extracted successfully.");
            return token;
        }
        log.warn("Authorization 헤더에서 토큰을 찾을 수 없습니다.");
        throw new InvalidTokenException("Authorization 헤더에서 토큰을 찾을 수 없습니다.");
    }
}
