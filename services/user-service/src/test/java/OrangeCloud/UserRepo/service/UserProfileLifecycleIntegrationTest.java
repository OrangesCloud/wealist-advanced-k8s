package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.dto.userprofile.UpdateProfileRequest;
import OrangeCloud.UserRepo.dto.userprofile.UserProfileResponse;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.entity.Workspace;
import OrangeCloud.UserRepo.entity.WorkspaceMember;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceMemberRepository;
import OrangeCloud.UserRepo.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 워크스페이스 프로필 전체 라이프사이클 통합 테스트
 * 
 * 테스트 시나리오:
 * 1. 사용자 생성 (기본 프로필 자동 생성)
 * 2. 워크스페이스 가입
 * 3. 프로필 조회 (fallback - 기본 프로필 반환)
 * 4. 프로필 수정 (lazy creation - 워크스페이스별 프로필 생성)
 * 5. 프로필 조회 (workspace-specific - 워크스페이스별 프로필 반환)
 * 6. 프로필 삭제
 * 7. 프로필 조회 (fallback - 다시 기본 프로필 반환)
 * 8. 캐시 동작 확인
 * 
 * Requirements: 1.1, 2.1, 3.1, 3.2
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(OrangeCloud.UserRepo.config.TestRedisConfig.class)
@Transactional
class UserProfileLifecycleIntegrationTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private User testUser;
    private Workspace testWorkspace;
    private UserProfile defaultProfile;

    @BeforeEach
    void setUp() {
        // 캐시 초기화 (인메모리 캐시 사용)
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }

    @Test
    @DisplayName("전체 프로필 라이프사이클 테스트: 생성 → 가입 → 조회(fallback) → 수정(lazy creation) → 조회(workspace-specific) → 삭제 → 조회(fallback)")
    void testCompleteProfileLifecycle() {
        // ========================================
        // 1. 사용자 생성 및 기본 프로필 생성
        // ========================================
        testUser = User.builder()
                .email("lifecycle-test@example.com")
                .provider("google")
                .googleId("lifecycle-google-id")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        defaultProfile = UserProfile.builder()
                .userId(testUser.getUserId())
                .workspaceId(DEFAULT_WORKSPACE_ID)
                .nickName("Default Nickname")
                .email(testUser.getEmail())
                .profileImageUrl("https://default-image.com/avatar.jpg")
                .build();
        defaultProfile = userProfileRepository.save(defaultProfile);

        assertThat(testUser.getUserId()).isNotNull();
        assertThat(defaultProfile.getProfileId()).isNotNull();
        assertThat(defaultProfile.getWorkspaceId()).isEqualTo(DEFAULT_WORKSPACE_ID);

        // ========================================
        // 2. 워크스페이스 생성 및 사용자 가입
        // ========================================
        testWorkspace = Workspace.builder()
                .ownerId(testUser.getUserId())
                .workspaceName("Test Workspace")
                .workspaceDescription("Test Description")
                .isPublic(true)
                .needApproved(false)
                .isActive(true)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(testWorkspace.getWorkspaceId())
                .userId(testUser.getUserId())
                .role(WorkspaceMember.WorkspaceRole.OWNER)
                .isActive(true)
                .build();
        workspaceMemberRepository.save(member);

        assertThat(testWorkspace.getWorkspaceId()).isNotNull();
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                testWorkspace.getWorkspaceId(), testUser.getUserId())).isTrue();

        // ========================================
        // 3. 프로필 조회 (fallback) - 워크스페이스별 프로필이 없으므로 기본 프로필 반환
        // ========================================
        UserProfileResponse fallbackProfile = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );

        // 기본 프로필 값이 반환되어야 함
        assertThat(fallbackProfile).isNotNull();
        assertThat(fallbackProfile.getWorkspaceId()).isEqualTo(testWorkspace.getWorkspaceId()); // 요청한 workspaceId
        assertThat(fallbackProfile.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(fallbackProfile.getNickName()).isEqualTo("Default Nickname");
        assertThat(fallbackProfile.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(fallbackProfile.getProfileImageUrl()).isEqualTo("https://default-image.com/avatar.jpg");

        // DB에는 워크스페이스별 프로필이 아직 없어야 함
        Optional<UserProfile> workspaceProfileBeforeUpdate = userProfileRepository.findByWorkspaceIdAndUserId(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(workspaceProfileBeforeUpdate).isEmpty();

        // ========================================
        // 4. 프로필 수정 (lazy creation) - 워크스페이스별 프로필 자동 생성
        // ========================================
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                testWorkspace.getWorkspaceId(),
                testUser.getUserId(),
                "Workspace Nickname",  // 새로운 닉네임
                null,  // 이메일은 변경하지 않음
                "https://workspace-image.com/avatar.jpg",  // 새로운 이미지 URL
                null   // attachmentId 없음
        );

        UserProfileResponse updatedProfile = userProfileService.updateProfile(updateRequest);

        // 업데이트된 프로필 확인
        assertThat(updatedProfile).isNotNull();
        assertThat(updatedProfile.getWorkspaceId()).isEqualTo(testWorkspace.getWorkspaceId());
        assertThat(updatedProfile.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(updatedProfile.getNickName()).isEqualTo("Workspace Nickname");
        assertThat(updatedProfile.getEmail()).isEqualTo(testUser.getEmail()); // 기본 프로필에서 복사됨
        assertThat(updatedProfile.getProfileImageUrl()).isEqualTo("https://workspace-image.com/avatar.jpg");

        // DB에 워크스페이스별 프로필이 생성되었는지 확인
        Optional<UserProfile> workspaceProfileAfterUpdate = userProfileRepository.findByWorkspaceIdAndUserId(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(workspaceProfileAfterUpdate).isPresent();
        assertThat(workspaceProfileAfterUpdate.get().getNickName()).isEqualTo("Workspace Nickname");
        assertThat(workspaceProfileAfterUpdate.get().getProfileImageUrl()).isEqualTo("https://workspace-image.com/avatar.jpg");

        // ========================================
        // 5. 프로필 조회 (workspace-specific) - 워크스페이스별 프로필 반환
        // ========================================
        UserProfileResponse workspaceSpecificProfile = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );

        // 워크스페이스별 프로필이 반환되어야 함
        assertThat(workspaceSpecificProfile).isNotNull();
        assertThat(workspaceSpecificProfile.getWorkspaceId()).isEqualTo(testWorkspace.getWorkspaceId());
        assertThat(workspaceSpecificProfile.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(workspaceSpecificProfile.getNickName()).isEqualTo("Workspace Nickname");
        assertThat(workspaceSpecificProfile.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(workspaceSpecificProfile.getProfileImageUrl()).isEqualTo("https://workspace-image.com/avatar.jpg");

        // ========================================
        // 6. 프로필 삭제
        // ========================================
        userProfileService.deleteWorkspaceProfile(testUser.getUserId(), testWorkspace.getWorkspaceId());

        // DB에서 워크스페이스별 프로필이 삭제되었는지 확인
        Optional<UserProfile> workspaceProfileAfterDelete = userProfileRepository.findByWorkspaceIdAndUserId(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(workspaceProfileAfterDelete).isEmpty();

        // 기본 프로필은 여전히 존재해야 함
        Optional<UserProfile> defaultProfileAfterDelete = userProfileRepository.findByWorkspaceIdAndUserId(
                DEFAULT_WORKSPACE_ID, 
                testUser.getUserId()
        );
        assertThat(defaultProfileAfterDelete).isPresent();

        // ========================================
        // 7. 프로필 조회 (fallback) - 다시 기본 프로필 반환
        // ========================================
        UserProfileResponse fallbackProfileAfterDelete = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );

        // 기본 프로필 값이 다시 반환되어야 함
        assertThat(fallbackProfileAfterDelete).isNotNull();
        assertThat(fallbackProfileAfterDelete.getWorkspaceId()).isEqualTo(testWorkspace.getWorkspaceId()); // 요청한 workspaceId
        assertThat(fallbackProfileAfterDelete.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(fallbackProfileAfterDelete.getNickName()).isEqualTo("Default Nickname");
        assertThat(fallbackProfileAfterDelete.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(fallbackProfileAfterDelete.getProfileImageUrl()).isEqualTo("https://default-image.com/avatar.jpg");
    }

    @Test
    @DisplayName("캐시 동작 확인: 프로필 생성 시 캐시 무효화")
    void testCacheInvalidationOnProfileCreation() {
        // ========================================
        // Setup: 사용자 및 워크스페이스 생성
        // ========================================
        testUser = User.builder()
                .email("cache-test@example.com")
                .provider("google")
                .googleId("cache-google-id")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        defaultProfile = UserProfile.builder()
                .userId(testUser.getUserId())
                .workspaceId(DEFAULT_WORKSPACE_ID)
                .nickName("Cache Test Nickname")
                .email(testUser.getEmail())
                .build();
        defaultProfile = userProfileRepository.save(defaultProfile);

        testWorkspace = Workspace.builder()
                .ownerId(testUser.getUserId())
                .workspaceName("Cache Test Workspace")
                .workspaceDescription("Cache Test")
                .isPublic(true)
                .needApproved(false)
                .isActive(true)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(testWorkspace.getWorkspaceId())
                .userId(testUser.getUserId())
                .role(WorkspaceMember.WorkspaceRole.OWNER)
                .isActive(true)
                .build();
        workspaceMemberRepository.save(member);

        // ========================================
        // 1. 첫 번째 조회 - 캐시에 저장됨
        // ========================================
        UserProfileResponse firstQuery = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(firstQuery.getNickName()).isEqualTo("Cache Test Nickname");

        // 캐시 확인
        var cache = cacheManager.getCache("userProfile");
        assertThat(cache).isNotNull();
        String cacheKey = testWorkspace.getWorkspaceId() + "::" + testUser.getUserId();
        var cachedValue = cache.get(cacheKey);
        assertThat(cachedValue).isNotNull();

        // ========================================
        // 2. 프로필 업데이트 (lazy creation) - 캐시 무효화되어야 함
        // ========================================
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                testWorkspace.getWorkspaceId(),
                testUser.getUserId(),
                "Updated Cache Nickname",
                null,
                null,
                null
        );

        userProfileService.updateProfile(updateRequest);

        // 캐시가 무효화되었는지 확인
        var cachedValueAfterUpdate = cache.get(cacheKey);
        // @CacheEvict로 인해 캐시가 무효화되었으므로 null이어야 함
        assertThat(cachedValueAfterUpdate).isNull();

        // ========================================
        // 3. 다시 조회 - 업데이트된 값이 반환되고 다시 캐시됨
        // ========================================
        UserProfileResponse secondQuery = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(secondQuery.getNickName()).isEqualTo("Updated Cache Nickname");

        // 다시 캐시에 저장되었는지 확인
        var cachedValueAfterSecondQuery = cache.get(cacheKey);
        assertThat(cachedValueAfterSecondQuery).isNotNull();

        // ========================================
        // 4. 프로필 삭제 - 캐시 무효화되어야 함
        // ========================================
        userProfileService.deleteWorkspaceProfile(testUser.getUserId(), testWorkspace.getWorkspaceId());

        // 캐시가 무효화되었는지 확인
        var cachedValueAfterDelete = cache.get(cacheKey);
        assertThat(cachedValueAfterDelete).isNull();

        // ========================================
        // 5. 삭제 후 조회 - fallback 값이 반환되고 다시 캐시됨
        // ========================================
        UserProfileResponse thirdQuery = userProfileService.workSpaceIdGetProfile(
                testWorkspace.getWorkspaceId(), 
                testUser.getUserId()
        );
        assertThat(thirdQuery.getNickName()).isEqualTo("Cache Test Nickname"); // 기본 프로필로 fallback

        // 다시 캐시에 저장되었는지 확인
        var cachedValueAfterThirdQuery = cache.get(cacheKey);
        assertThat(cachedValueAfterThirdQuery).isNotNull();
    }

    @Test
    @DisplayName("기본 프로필 삭제 시도 시 예외 발생")
    void testCannotDeleteDefaultProfile() {
        // ========================================
        // Setup: 사용자 생성
        // ========================================
        testUser = User.builder()
                .email("default-delete-test@example.com")
                .provider("google")
                .googleId("default-delete-google-id")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        defaultProfile = UserProfile.builder()
                .userId(testUser.getUserId())
                .workspaceId(DEFAULT_WORKSPACE_ID)
                .nickName("Default Profile")
                .email(testUser.getEmail())
                .build();
        defaultProfile = userProfileRepository.save(defaultProfile);

        // ========================================
        // 기본 프로필 삭제 시도 - 예외 발생해야 함
        // ========================================
        assertThatThrownBy(() -> 
                userProfileService.deleteWorkspaceProfile(testUser.getUserId(), DEFAULT_WORKSPACE_ID)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기본 프로필은 삭제할 수 없습니다");

        // 기본 프로필이 여전히 존재하는지 확인
        Optional<UserProfile> defaultProfileAfterAttempt = userProfileRepository.findByWorkspaceIdAndUserId(
                DEFAULT_WORKSPACE_ID, 
                testUser.getUserId()
        );
        assertThat(defaultProfileAfterAttempt).isPresent();
    }

    @Test
    @DisplayName("기본 프로필이 없을 때 프로필 업데이트 시 예외 발생")
    void testUpdateProfileWithoutDefaultProfile() {
        // ========================================
        // Setup: 사용자 생성 (기본 프로필 없음)
        // ========================================
        testUser = User.builder()
                .email("no-default-test@example.com")
                .provider("google")
                .googleId("no-default-google-id")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        testWorkspace = Workspace.builder()
                .ownerId(testUser.getUserId())
                .workspaceName("No Default Test Workspace")
                .workspaceDescription("Test")
                .isPublic(true)
                .needApproved(false)
                .isActive(true)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(testWorkspace.getWorkspaceId())
                .userId(testUser.getUserId())
                .role(WorkspaceMember.WorkspaceRole.OWNER)
                .isActive(true)
                .build();
        workspaceMemberRepository.save(member);

        // ========================================
        // 기본 프로필 없이 프로필 업데이트 시도 - 예외 발생해야 함
        // ========================================
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                testWorkspace.getWorkspaceId(),
                testUser.getUserId(),
                "New Nickname",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> 
                userProfileService.updateProfile(updateRequest)
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("기본 프로필을 찾을 수 없습니다");
    }
}
