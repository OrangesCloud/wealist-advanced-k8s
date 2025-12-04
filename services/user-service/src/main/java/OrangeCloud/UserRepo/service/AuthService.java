package OrangeCloud.UserRepo.service;

// ⚠️ 토큰 관련 로직(logout, refresh, validate)은 auth-service로 이동되었습니다.
// 이 서비스는 사용자 정보 조회와 블랙리스트 확인만 담당합니다.

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ============================================================================
    // 사용자 정보 조회
    // ============================================================================

    /**
     * 현재 인증된 사용자 정보 조회
     */
    public User getCurrentUser(UUID userId) {
        log.debug("Fetching user for ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found for ID: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        log.debug("Successfully retrieved user for ID: {}", userId);
        return user;
    }

    // ============================================================================
    // 토큰 블랙리스트 확인 (JwtAuthenticationFilter에서 사용)
    // ============================================================================

    /**
     * 토큰이 Redis 블랙리스트에 있는지 확인
     * auth-service와 동일한 Redis를 사용하므로 직접 확인 가능
     */
    public boolean isTokenBlacklisted(String token) {
        log.debug("Checking if token is blacklisted");
        Boolean isBlacklisted = redisTemplate.hasKey(token);
        return Boolean.TRUE.equals(isBlacklisted);
    }
}