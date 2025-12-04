package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.dto.MessageApiResponse;
import OrangeCloud.UserRepo.dto.user.CreateUserRequest;
import OrangeCloud.UserRepo.dto.user.UpdateUserRequest;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.service.UserService;
import OrangeCloud.UserRepo.util.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "사용자 관리 API")
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping
    @Operation(summary = "사용자 생성", description = "새로운 사용자를 생성합니다.")
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getEmail());
        User user = userService.findOrCreateUserByGoogle(request.getEmail(), request.getGoogleId(), null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 정보를 조회합니다.")
    public ResponseEntity<User> getMyInfo(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        log.debug("Fetching user info for ID: {}", userId);
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 정보 조회", description = "특정 사용자의 정보를 조회합니다.")
    public ResponseEntity<User> getUserInfo(
            @PathVariable UUID userId,
            jakarta.servlet.http.HttpServletRequest request) {
        // Enhanced logging: Log incoming request details
        log.info("=== RECEIVED REQUEST: Get User Info ===");
        log.info("Request Method: {}", request.getMethod());
        log.info("Request URI: {}", request.getRequestURI());
        log.info("Request URL: {}", request.getRequestURL());
        log.info("Path Variable - userId: {}", userId);
        log.info("Remote Address: {}", request.getRemoteAddr());
        log.info("Authorization Header Present: {}", request.getHeader("Authorization") != null);

        User user = userService.getUserById(userId);

        log.info("User info retrieved successfully: userId={}, email={}", userId, user.getEmail());
        log.info("=== END REQUEST: Get User Info ===");

        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "사용자 정보 수정", description = "특정 사용자의 정보를 수정합니다.")
    public ResponseEntity<User> updateUser(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        log.info("Updating user: {}", userId);
        User user = userService.updateUser(userId, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/me")
    @Operation(summary = "계정 삭제", description = "현재 사용자 계정을 삭제합니다.")
    public ResponseEntity<MessageApiResponse> deleteMyAccount(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Deleting user account: {}", userId);
        userService.softDeleteUser(userId);
        return ResponseEntity.ok(MessageApiResponse.success("계정이 삭제되었습니다."));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "사용자 삭제 (관리자용)", description = "특정 사용자를 삭제합니다.")
    public ResponseEntity<MessageApiResponse> deleteUser(@PathVariable UUID userId) {
        log.info("Deleting user: {}", userId);
        userService.softDeleteUser(userId);
        return ResponseEntity.ok(MessageApiResponse.success("사용자가 삭제되었습니다."));
    }

    @PutMapping("/{userId}/restore")
    @Operation(summary = "사용자 복구", description = "삭제된 사용자를 복구합니다.")
    public ResponseEntity<MessageApiResponse> restoreUser(@PathVariable UUID userId) {
        log.info("Restoring user: {}", userId);
        userService.restoreUser(userId);
        return ResponseEntity.ok(MessageApiResponse.success("사용자가 복구되었습니다."));
    }

    @GetMapping("/test/{userid}")
    public String returnToken(@PathVariable UUID userid) {
        String accessToken = tokenProvider.generateToken(userid);
        return accessToken;
    }

}