package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import OrangeCloud.UserRepo.dto.user.UpdateUserRequest;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");


    /**
     * Google OAuth 로그인/가입
     * 기존 사용자면 반환, 없으면 새로 생성
     */
    public User findOrCreateUserByGoogle(String email, String googleId, String nickName) {
        log.info("Finding or creating user via Google OAuth: email={}, googleId={}", email, googleId);

        // 1. googleId로 먼저 조회 (정확한 매칭)
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Google user already exists: userId={}", user.getUserId());
            return user;
        }

        // 2. googleId가 없으면 이메일로 조회
        Optional<User> userByEmail = userRepository.findActiveByEmail(email);
        if (userByEmail.isPresent()) {
            User user = userByEmail.get();
            // 기존 계정에 googleId 추가
            user.setGoogleId(googleId);
            User updatedUser = userRepository.save(user);
            log.info("User exists by email, adding Google OAuth: userId={}", user.getUserId());
            return updatedUser;
        }

        // 3. 새로운 사용자 생성
        User newUser = User.builder()
                .email(email)
                .googleId(googleId)
                .provider("google")
                .isActive(true)
                .build();

        User savedUser = userRepository.save(newUser);
        log.debug("New User created: userId={}, email={}", savedUser.getUserId(), email);

        // UserProfile도 함께 생성
        UserProfile profile = UserProfile.builder()
                .userId(savedUser.getUserId())
                .nickName(nickName != null && !nickName.isEmpty() ? nickName : email)
                .workspaceId(DEFAULT_WORKSPACE_ID)
                .build();
//
        userProfileRepository.save(profile);
        log.debug("UserProfile created for userId={}, nickName={}", savedUser.getUserId(), nickName);

        log.info("New Google user created: userId={}, email={}", savedUser.getUserId(), email);
        return savedUser;
    }

    /**
     * 사용자 ID로 조회
     */
    public User getUserById(UUID userId) {
        log.debug("Fetching user by ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found for ID: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        log.debug("User retrieved: userId={}", userId);
        return user;
    }

    /**
     * 이메일로 조회
     */


    public User getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        User user = userRepository.findActiveByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found for email: {}", email);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        log.debug("User retrieved: email={}", email);
        return user;
    }

    @Transactional
    public User updateUser(UUID userId, UpdateUserRequest request) {
        log.debug("Updating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found for ID: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            user.setEmail(request.getEmail());
        }

        if (request.getGoogleId() != null && !request.getGoogleId().isEmpty()) {
            user.setGoogleId(request.getGoogleId());
        }

        return userRepository.save(user);
    }

    /**
     * 사용자 소프트 삭제
     */

    public void softDeleteUser(UUID userId) {
        log.debug("Soft deleting user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found for ID: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        user.softDelete();
        userRepository.save(user);
        log.info("User soft deleted: userId={}", userId);
    }

    /**
     * 사용자 복구
     */
    public void restoreUser(UUID userId) {
        log.debug("Restoring user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found for ID: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        user.restore();
        userRepository.save(user);
        log.info("User restored: userId={}", userId);
    }
}