package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.dto.userprofile.AttachmentResponse;
import OrangeCloud.UserRepo.dto.userprofile.CreateProfileRequest;
import OrangeCloud.UserRepo.dto.userprofile.UserProfileResponse;
import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.repository.AttachmentRepository;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceMemberRepository;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import OrangeCloud.UserRepo.dto.userprofile.UpdateProfileRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private final org.springframework.cache.CacheManager cacheManager;
    private static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Transactional
    public UserProfileResponse createProfile(CreateProfileRequest request, UUID userId) {
        log.info("Creating profile for user: {}", userId);
        UserProfile userProfile = UserProfile.create(
                request.workspaceId(),
                userId,
                request.nickName(),
                request.email(),
                null
        );
        UserProfile savedProfile = userProfileRepository.save(userProfile);
        return UserProfileResponse.from(savedProfile);
    }

    /**
     * 사용자 프로필을 조회하고 DTO로 반환합니다. (Redis 캐시 적용)
     * 캐시 이름: "userProfile", 키: workspaceId::userId
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userProfile", key = "T(java.util.UUID).fromString('00000000-0000-0000-0000-000000000000') + '::' + #userId")
    public UserProfileResponse getProfile(UUID userId) {
        log.info("[Cacheable] Attempting to retrieve profile from DB for user: {}, workspaceId: {}", userId, DEFAULT_WORKSPACE_ID);
        // DB 조회 (UserProfile 엔티티)
        UserProfile profile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, userId)
                .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));

        // 프로필 이미지 첨부파일 조회 (있으면 하나만)
        AttachmentResponse attachment = getProfileImageAttachment(profile.getProfileId());

        return UserProfileResponse.from(profile, attachment);
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "userProfile", key = "#workspaceId + '::' + #userId")
    public UserProfileResponse workSpaceIdGetProfile(UUID workspaceId, UUID userId) {
        log.info("[Cacheable] Attempting to retrieve profile from DB for user: {}, workspaceId: {}", userId, workspaceId);

        try {
            // DB 조회 (UserProfile 엔티티)
            Optional<UserProfile> profileOpt = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
            
            if (profileOpt.isPresent()) {
                // 워크스페이스별 프로필이 존재하는 경우
                UserProfile profile = profileOpt.get();
                log.debug("Found workspace-specific profile: profileId={}", profile.getProfileId());
                
                // 프로필 이미지 첨부파일 조회 (있으면 하나만)
                AttachmentResponse attachment = getProfileImageAttachment(profile.getProfileId());
                
                // 워크스페이스별 이미지가 없으면 기본 프로필 이미지 사용
                if (attachment == null && !workspaceId.equals(DEFAULT_WORKSPACE_ID)) {
                    log.debug("No workspace-specific image found, falling back to default profile image for userId: {}", userId);
                    Optional<UserProfile> defaultProfile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, userId);
                    if (defaultProfile.isPresent()) {
                        attachment = getProfileImageAttachment(defaultProfile.get().getProfileId());
                        log.debug("Using default profile image: {}", attachment != null ? attachment.getFileUrl() : "none");
                    }
                }
                
                return UserProfileResponse.from(profile, attachment);
            } else {
                // 워크스페이스별 프로필이 없는 경우 - 기본 프로필로 fallback
                log.debug("Workspace-specific profile not found, falling back to default profile for userId: {}, workspaceId: {}", userId, workspaceId);
                
                UserProfile defaultProfile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, userId)
                        .orElseThrow(() -> {
                            log.error("Default profile not found for userId: {}", userId);
                            return new UserNotFoundException("기본 프로필을 찾을 수 없습니다. 사용자 등록이 완료되지 않았습니다.");
                        });
                
                // 기본 프로필의 첨부파일 조회
                AttachmentResponse attachment = getProfileImageAttachment(defaultProfile.getProfileId());
                log.debug("Using default profile with attachment: {}", attachment != null ? attachment.getFileUrl() : "none");
                
                // 기본 프로필 값을 사용하되, workspaceId는 요청한 workspaceId로 설정
                return UserProfileResponse.builder()
                        .profileId(defaultProfile.getProfileId())
                        .workspaceId(workspaceId)  // 요청한 workspaceId 사용
                        .userId(defaultProfile.getUserId())
                        .nickName(defaultProfile.getNickName())
                        .email(defaultProfile.getEmail())
                        .profileImageUrl(defaultProfile.getProfileImageUrl())
                        .profileImageAttachment(attachment)
                        .build();
            }
        } catch (CustomException e) {
            // Re-throw known exceptions (includes UserNotFoundException)
            log.error("Profile retrieval failed: workspaceId={}, userId={}, error={}", 
                    workspaceId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while retrieving workspace profile: workspaceId={}, userId={}", 
                    workspaceId, userId, e);
            throw new RuntimeException("프로필 조회 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 프로필 이미지 첨부파일 조회
     * 프로필 이미지는 하나만 존재하므로 첫 번째 것을 반환
     */
    private AttachmentResponse getProfileImageAttachment(UUID profileId) {
        List<Attachment> attachments = attachmentRepository.findByEntityTypeAndEntityIdAndDeletedAtIsNull(
                Attachment.EntityType.USER_PROFILE,
                profileId
        );
        
        // 프로필 이미지가 있으면 첫 번째 것 반환, 없으면 null
        return attachments.isEmpty() ? null : AttachmentResponse.from(attachments.get(0));
    }

    /**
     * 특정 사용자의 워크스페이스 프로필을 조회합니다.
     * 요청자는 해당 워크스페이스의 멤버여야 합니다.
     * 워크스페이스 전용 프로필이 없는 경우 사용자의 기본 프로필 정보를 반환합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param targetUserId 조회할 사용자 ID
     * @param requestingUserId 요청하는 사용자 ID
     * @return 사용자 프로필 응답 DTO (워크스페이스 프로필 또는 기본 프로필)
     * @throws CustomException 요청자가 워크스페이스 멤버가 아닌 경우 (403 Forbidden)
     * @throws UserNotFoundException 대상 사용자가 시스템에 존재하지 않는 경우 (404 Not Found)
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getWorkspaceProfileByUserId(
            UUID workspaceId, 
            UUID targetUserId, 
            UUID requestingUserId) {
        
        log.info("Fetching workspace profile: workspaceId={}, targetUserId={}, requestingUserId={}", 
                workspaceId, targetUserId, requestingUserId);
        
        // 1. 요청자가 워크스페이스 멤버인지 검증
        boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, requestingUserId);
        if (!isMember) {
            log.warn("Access denied: User {} is not a member of workspace {}", requestingUserId, workspaceId);
            throw new CustomException(ErrorCode.HANDLE_ACCESS_DENIED, 
                    "You must be a member of this workspace to view member profiles");
        }
        
        // 2. 워크스페이스 전용 프로필 조회 시도
        Optional<UserProfile> workspaceProfile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId);
        
        if (workspaceProfile.isPresent()) {
            // 워크스페이스 프로필이 존재하는 경우 - 반환
            log.info("Workspace profile found: profileId={}, workspaceId={}, userId={}", 
                    workspaceProfile.get().getProfileId(), workspaceId, targetUserId);
            return UserProfileResponse.from(workspaceProfile.get());
        }
        
        // 3. 워크스페이스 프로필이 없는 경우 - 기본 프로필로 fallback
        log.info("Workspace profile not found, falling back to default profile: workspaceId={}, userId={}", 
                workspaceId, targetUserId);
        
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> {
                    log.warn("User not found: userId={}", targetUserId);
                    return new UserNotFoundException("User not found in the system");
                });
        
        // 4. 기본 프로필로 UserProfileResponse 생성
        log.info("Returning default profile for user: userId={}, email={}", targetUserId, user.getEmail());
        
        // 기본 워크스페이스의 프로필 이미지 조회 (있으면 사용)
        AttachmentResponse defaultAttachment = null;
        Optional<UserProfile> defaultProfile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, targetUserId);
        if (defaultProfile.isPresent()) {
            defaultAttachment = getProfileImageAttachment(defaultProfile.get().getProfileId());
            log.debug("Using default profile image for workspace fallback: {}", defaultAttachment != null ? defaultAttachment.getFileUrl() : "none");
        }
        
        return UserProfileResponse.builder()
                .profileId(null)  // 워크스페이스 전용 프로필 ID 없음
                .workspaceId(workspaceId)
                .userId(user.getUserId())
                .nickName(null)  // 커스텀 닉네임 없음
                .email(user.getEmail())
                .profileImageUrl(defaultProfile.map(UserProfile::getProfileImageUrl).orElse(null))
                .profileImageAttachment(defaultAttachment)  // 기본 프로필 이미지 첨부파일
                .build();
    }

    // 해당 사용자id에 따른 모든 프로필 가져오기
    @Transactional(readOnly = true)
    @Cacheable(value = "userProfiles", key = "#userId")
    public List<UserProfileResponse> getAllProfiles(UUID userId) {
        log.info("[Cacheable] Attempting to retrieve all profiles from DB for user: {}", userId);

        // DB 조회 (해당 사용자의 모든 UserProfile 엔티티)
        List<UserProfile> profiles = userProfileRepository.findAllByUserId(userId);

        if (profiles.isEmpty()) {
            throw new UserNotFoundException("사용자의 프로필을 찾을 수 없습니다.");
        }

        // DTO 변환 후 반환 (첨부파일 정보 포함)
        return profiles.stream()
                .map(profile -> {
                    AttachmentResponse attachment = getProfileImageAttachment(profile.getProfileId());
                    return UserProfileResponse.from(profile, attachment);
                })
                .collect(Collectors.toList());

    }



    /**
     * 워크스페이스별 프로필을 조회하거나 없으면 기본 프로필에서 복사하여 생성합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param userId 사용자 ID
     * @return 기존 또는 새로 생성된 워크스페이스 프로필
     * @throws UserNotFoundException 기본 프로필이 없는 경우
     * @throws CustomException 워크스페이스 멤버가 아닌 경우
     */
    private UserProfile getOrCreateWorkspaceProfile(UUID workspaceId, UUID userId) {
        log.debug("Getting or creating workspace profile: workspaceId={}, userId={}", workspaceId, userId);
        
        try {
            // 1. 기존 워크스페이스 프로필이 있는지 확인
            Optional<UserProfile> existingProfile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
            if (existingProfile.isPresent()) {
                log.debug("Found existing workspace profile: profileId={}", existingProfile.get().getProfileId());
                return existingProfile.get();
            }
            
            // 2. 기본 프로필 조회 (없으면 예외 발생)
            UserProfile defaultProfile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, userId)
                    .orElseThrow(() -> {
                        log.error("Default profile not found for userId: {}", userId);
                        return new UserNotFoundException("기본 프로필을 찾을 수 없습니다. 사용자 등록이 완료되지 않았습니다.");
                    });
            
            // 3. 워크스페이스 멤버십 검증
            boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
            if (!isMember) {
                log.warn("Access denied: User {} is not a member of workspace {}", userId, workspaceId);
                throw new CustomException(ErrorCode.HANDLE_ACCESS_DENIED, 
                        "해당 워크스페이스의 멤버만 프로필을 수정할 수 있습니다.");
            }
            
            // 4. 기본 프로필에서 복사하여 새 워크스페이스 프로필 생성
            UserProfile newProfile = UserProfile.create(
                    workspaceId,
                    userId,
                    defaultProfile.getNickName(),
                    defaultProfile.getEmail(),
                    defaultProfile.getProfileImageUrl()
            );
            
            UserProfile savedProfile = userProfileRepository.save(newProfile);
            log.info("Created new workspace profile: profileId={}, workspaceId={}, userId={}", 
                    savedProfile.getProfileId(), workspaceId, userId);
            
            return savedProfile;
        } catch (CustomException e) {
            // Re-throw known exceptions (includes UserNotFoundException)
            log.error("Error in getOrCreateWorkspaceProfile: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Log and wrap unexpected exceptions
            log.error("Unexpected error while getting or creating workspace profile: workspaceId={}, userId={}", 
                    workspaceId, userId, e);
            throw new RuntimeException("워크스페이스 프로필 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 사용자 프로필 닉네임, 이메일 및 이미지 URL을 통합 업데이트하고 캐시를 무효화합니다.
     * 워크스페이스별 프로필이 없으면 자동으로 생성합니다 (lazy creation).
     * @param request
     * @return 업데이트된 UserProfile 엔티티 (Service 내부에서 사용되므로 엔티티 반환 유지)
     */
    @Transactional
    @CacheEvict(value = "userProfile", key = "#request.workspaceId + '::' + #request.userId")
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        log.info("[CacheEvict] Updating profile for user: workspaceId={}, userId={}, nickName={}, email={}, imageUrl={}, attachmentId={}", 
                request.workspaceId(), request.userId(), request.nickName(), request.email(), request.profileImageUrl(), request.attachmentId());

        try {
            // 1. UserProfile 조회 또는 생성 (lazy creation)
            UserProfile profile = getOrCreateWorkspaceProfile(request.workspaceId(), request.userId());

            // 2. 닉네임 업데이트 (값이 존재하고 비어있지 않을 경우에만)
            if (request.nickName() != null && !request.nickName().trim().isEmpty()) {
                profile.updateNickName(request.nickName().trim());
                log.debug("Profile nickName updated to: {}", request.nickName().trim());
            }

            // 3. 이메일 업데이트 (값이 존재하고 비어있지 않을 경우에만)
            if (request.email() != null && !request.email().trim().isEmpty()) {
                profile.updateEmail(request.email().trim());
                log.debug("Profile email updated to: {}", request.email().trim());
            }

            // 4. 첨부파일 확정 (attachmentId가 있는 경우)
            if (request.attachmentId() != null) {
                Attachment confirmedAttachment = attachmentService.confirmAttachment(request.attachmentId(), profile.getProfileId());
                profile.updateProfileImageUrl(confirmedAttachment.getFileUrl());
                log.debug("Attachment confirmed and profile image URL updated: attachmentId={}, profileId={}, fileUrl={}", 
                        request.attachmentId(), profile.getProfileId(), confirmedAttachment.getFileUrl());
            }
            // 5. 이미지 URL 직접 업데이트 (attachmentId가 없고 profileImageUrl이 제공된 경우)
            else if (request.profileImageUrl() != null) {
                String urlToSave = request.profileImageUrl().trim().isEmpty() ? null : request.profileImageUrl().trim();
                profile.updateProfileImageUrl(urlToSave);
                log.debug("Profile image URL updated to: {}", urlToSave);
            }

            // 6. 변경된 프로필 저장
            UserProfile updatedProfile = userProfileRepository.save(profile);
            
            // 7. userProfiles 캐시도 무효화 (모든 프로필 목록 갱신)
            org.springframework.cache.Cache userProfilesCache = cacheManager.getCache("userProfiles");
            if (userProfilesCache != null) {
                userProfilesCache.evict(request.userId());
                log.debug("Evicted userProfiles cache for userId: {}", request.userId());
            }
            
            log.info("Profile updated successfully: profileId={}, workspaceId={}, userId={}", 
                    updatedProfile.getProfileId(), request.workspaceId(), request.userId());
            
            return UserProfileResponse.from(updatedProfile);
        } catch (CustomException e) {
            // Re-throw known exceptions (includes UserNotFoundException)
            log.error("User profile update failed: workspaceId={}, userId={}, error={}", 
                    request.workspaceId(), request.userId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during profile update: workspaceId={}, userId={}", 
                    request.workspaceId(), request.userId(), e);
            throw new RuntimeException("프로필 업데이트 중 오류가 발생했습니다.", e);
        }
    }


    /**
     * 사용자 프로필을 삭제하고 캐시를 무효화합니다.
     * @param userId 사용자 ID (UUID)
     * @param workspaceId 워크스페이스 ID (UUID)
     */
    @Transactional
    @CacheEvict(value = "userProfile", key = "#workspaceId + '::' + #userId")
    public void deleteProfile(UUID userId, UUID workspaceId) {
        log.info("[CacheEvict] Deleting profile for user: workspaceId={}, userId={}", workspaceId, userId);

        UserProfile profile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UserNotFoundException("삭제할 프로필을 찾을 수 없습니다."));

        userProfileRepository.delete(profile);
    }

    /**
     * 워크스페이스별 프로필을 삭제하고 캐시를 무효화합니다.
     * 기본 워크스페이스(DEFAULT_WORKSPACE_ID) 프로필은 삭제할 수 없습니다.
     * 
     * @param userId 사용자 ID
     * @param workspaceId 워크스페이스 ID (DEFAULT_WORKSPACE_ID가 아니어야 함)
     * @throws IllegalArgumentException 기본 프로필 삭제를 시도하는 경우
     * @throws UserNotFoundException 삭제할 프로필을 찾을 수 없는 경우
     */
    @Transactional
    @CacheEvict(value = "userProfile", key = "#workspaceId + '::' + #userId")
    public void deleteWorkspaceProfile(UUID userId, UUID workspaceId) {
        log.info("Attempting to delete workspace profile: workspaceId={}, userId={}", workspaceId, userId);
        
        try {
            // 1. 기본 워크스페이스 프로필 삭제 방지
            if (DEFAULT_WORKSPACE_ID.equals(workspaceId)) {
                log.warn("Attempted to delete default profile: userId={}, workspaceId={}", userId, workspaceId);
                throw new IllegalArgumentException("기본 프로필은 삭제할 수 없습니다.");
            }
            
            // 2. 워크스페이스별 프로필 조회
            UserProfile profile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .orElseThrow(() -> {
                        log.warn("Workspace profile not found for deletion: workspaceId={}, userId={}", workspaceId, userId);
                        return new UserNotFoundException("삭제할 워크스페이스 프로필을 찾을 수 없습니다.");
                    });
            
            // 3. 프로필 삭제
            userProfileRepository.delete(profile);
            log.info("Successfully deleted workspace profile: profileId={}, workspaceId={}, userId={}", 
                    profile.getProfileId(), workspaceId, userId);
            
            // 4. userProfiles 캐시도 무효화 (모든 프로필 목록 갱신)
            org.springframework.cache.Cache userProfilesCache = cacheManager.getCache("userProfiles");
            if (userProfilesCache != null) {
                userProfilesCache.evict(userId);
                log.debug("Evicted userProfiles cache for userId: {}", userId);
            }
        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            log.error("Validation error deleting workspace profile: workspaceId={}, userId={}, error={}", 
                    workspaceId, userId, e.getMessage());
            throw e;
        } catch (CustomException e) {
            // Re-throw known exceptions (includes UserNotFoundException)
            log.error("Error deleting workspace profile: workspaceId={}, userId={}, error={}", 
                    workspaceId, userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // Log and wrap unexpected exceptions
            log.error("Unexpected error while deleting workspace profile: workspaceId={}, userId={}", 
                    workspaceId, userId, e);
            throw new RuntimeException("워크스페이스 프로필 삭제 중 오류가 발생했습니다.", e);
        }
    }

}