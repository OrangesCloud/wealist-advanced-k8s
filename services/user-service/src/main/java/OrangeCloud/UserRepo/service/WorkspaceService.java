package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.dto.workspace.*;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.entity.Workspace;
import OrangeCloud.UserRepo.entity.WorkspaceJoinRequest;
import OrangeCloud.UserRepo.entity.WorkspaceMember;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceJoinRequestRepository;
import OrangeCloud.UserRepo.repository.WorkspaceMemberRepository;
import OrangeCloud.UserRepo.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceJoinRequestRepository workspaceJoinRequestRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final Optional<SampleDataSeederService> sampleDataSeederService;
    private static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    // ============================================================================
    // Workspace 생성/수정/삭제
    // ============================================================================

    /**
     * 새로운 Workspace 생성 (생성자가 자동으로 OWNER)
     */
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID creatorId) {
        log.info("Creating workspace: name={}, creator={}", request.getWorkspaceName(), creatorId);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", creatorId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        boolean isPublic = request.getIsPublic() != null ? request.getIsPublic() : false;

        Workspace workspace = Workspace.builder()
                .ownerId(creatorId)
                .workspaceName(request.getWorkspaceName())
                .workspaceDescription(request.getWorkspaceDescription())
                .isPublic(isPublic)
                .needApproved(true)
                .isActive(true)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info("Workspace created: workspaceId={}", savedWorkspace.getWorkspaceId());

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspaceId(savedWorkspace.getWorkspaceId())
                .userId(creatorId)
                .role(WorkspaceMember.WorkspaceRole.OWNER)
                .isDefault(true)
                .isActive(true)
                .build();

        workspaceMemberRepository.save(ownerMember);
        log.info("Creator added as OWNER: workspaceId={}, userId={}", savedWorkspace.getWorkspaceId(), creatorId);

        UserProfile creatorProfile = userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, creatorId)
                .orElseThrow(() -> {
                    log.warn("Profile not found for user: {}", creatorId);
                    return new UserNotFoundException("프로필을 찾을 수 없습니다.");
                });

        // Trigger sample data generation asynchronously if enabled
        sampleDataSeederService.ifPresent(seeder -> {
            log.info("Triggering sample data generation for workspace: {}", savedWorkspace.getWorkspaceId());
            seeder.seedWorkspaceData(savedWorkspace.getWorkspaceId(), creatorId);
        });

        return convertToWorkspaceResponse(savedWorkspace, creator, creatorProfile);
    }

    /**
     * Workspace 정보 수정 (OWNER만)
     */
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID requesterId) {
        log.info("Updating workspace: workspaceId={}, requester={}", workspaceId, requesterId);

        checkWorkspaceOwner(workspaceId, requesterId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace not found");
                });

        if (request.getWorkspaceName() != null && !request.getWorkspaceName().isEmpty()) {
            workspace.setWorkspaceName(request.getWorkspaceName());
        }

        if (request.getWorkspaceDescription() != null && !request.getWorkspaceDescription().isEmpty()) {
            workspace.setWorkspaceDescription(request.getWorkspaceDescription());
        }

        Workspace updated = workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = workspaceMemberRepository.findOwnerByWorkspaceId(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace owner not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace owner not found");
                });

        User owner = userRepository.findById(ownerMember.getUserId())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        UserProfile ownerProfile = userProfileRepository.findByUserId(ownerMember.getUserId())
                .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));

        log.info("Workspace updated: workspaceId={}", workspaceId);
        return convertToWorkspaceResponse(updated, owner, ownerProfile);
    }

    /**
     * Workspace 소프트 삭제 (OWNER만)
     */
    public void deleteWorkspace(UUID workspaceId, UUID requesterId) {
        log.info("Deleting workspace: workspaceId={}, requester={}", workspaceId, requesterId);

        checkWorkspaceOwner(workspaceId, requesterId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace not found");
                });

        workspace.softDelete();
        workspaceRepository.save(workspace);
        log.info("Workspace deleted: workspaceId={}", workspaceId);
    }

    // ============================================================================
    // Workspace 조회
    // ============================================================================

    /**
     * 특정 Workspace 정보를 조회합니다.
     * Board Service에서 호출하는 엔드포인트용
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        log.info("Fetching workspace: workspaceId={}", workspaceId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace not found");
                });

        User owner = userRepository.findById(workspace.getOwnerId())
                .orElseThrow(() -> {
                    log.warn("Owner not found: {}", workspace.getOwnerId());
                    return new UserNotFoundException("Owner not found");
                });

        UserProfile ownerProfile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, workspace.getOwnerId())
                .orElseThrow(() -> {
                    log.warn("Owner profile not found: {}", workspace.getOwnerId());
                    return new UserNotFoundException("Owner profile not found");
                });

        log.info("Workspace retrieved: workspaceId={}, name={}", workspaceId, workspace.getWorkspaceName());
        return convertToWorkspaceResponse(workspace, owner, ownerProfile);
    }

    // ============================================================================
    // Workspace 설정 관리 (신규 추가)
    // ============================================================================

    /**
     * 특정 Workspace의 설정 정보를 조회합니다. (멤버만 가능)
     */
    @Transactional(readOnly = true)
    public WorkspaceSettingsResponse getWorkspaceSettings(UUID workspaceId, UUID requesterId) {
        log.debug("Fetching workspace settings: workspaceId={}, requester={}", workspaceId, requesterId);

        checkWorkspaceMember(workspaceId, requesterId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace not found");
                });

        return convertToWorkspaceSettingsResponse(workspace);
    }

    /**
     * Workspace 설정 정보를 수정합니다. (OWNER/ADMIN만 가능)
     */
    public WorkspaceSettingsResponse updateWorkspaceSettings(
            UUID workspaceId,
            UpdateWorkspaceSettingsRequest request,
            UUID requesterId) {
        log.info("Updating workspace settings: workspaceId={}, requester={}", workspaceId, requesterId);

        checkWorkspaceAdminOrOwner(workspaceId, requesterId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> {
                    log.warn("Workspace not found: {}", workspaceId);
                    return new IllegalArgumentException("Workspace not found");
                });

        if (request.getWorkspaceName() != null && !request.getWorkspaceName().isEmpty()) {
            workspace.setWorkspaceName(request.getWorkspaceName());
        }
        if (request.getWorkspaceDescription() != null) {
            workspace.setWorkspaceDescription(request.getWorkspaceDescription());
        }
        if (request.getIsPublic() != null) {
            workspace.setIsPublic(request.getIsPublic());
        }
        if (request.getNeedApproved() != null) {
            workspace.setNeedApproved(request.getNeedApproved());
        }

        Workspace updated = workspaceRepository.save(workspace);
        log.info("Workspace settings updated: workspaceId={}", workspaceId);
        return convertToWorkspaceSettingsResponse(updated);
    }

    // ============================================================================
    // Workspace 조회
    // ============================================================================

    /**
     * 사용자의 모든 Workspace 조회 (멤버,owner 가능)
     * 워크스페이스 , 설명, 가입 상태, 역할
     */
    // 사용자가 갖고있는 모든 워크스페이스, 신청
    @Transactional(readOnly = true)
    public List<UserWorkspaceResponse> getUserWorkspaces(UUID userId) {
        log.debug("Fetching workspaces for user: userId={}", userId);

        // 1️⃣ 가입한 워크스페이스 (WorkspaceMember)
        List<WorkspaceMember> activeMemberships = workspaceMemberRepository.findActiveByUserId(userId);

        // 2️⃣ 신청한 워크스페이스 (WorkspaceJoinRequest)
        List<WorkspaceJoinRequest> approvedOrPendingRequests = workspaceJoinRequestRepository
                .findByUserIdAndStatusIn(userId, List.of(
                        WorkspaceJoinRequest.JoinRequestStatus.APPROVED,
                        WorkspaceJoinRequest.JoinRequestStatus.PENDING));

        // 3️⃣ 워크스페이스 ID 합치기
        Set<UUID> workspaceIds = new HashSet<>();
        activeMemberships.forEach(member -> workspaceIds.add(member.getWorkspaceId()));
        approvedOrPendingRequests.forEach(req -> workspaceIds.add(req.getWorkspaceId()));

        // 4️⃣ Workspace + 상태정보 변환
        return workspaceIds.stream()
                .map(workspaceId -> {
                    Workspace workspace = workspaceRepository.findById(workspaceId)
                            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

                    // 기본 role은 "UNKNOWN"
                    String role = "UNKNOWN";
                    boolean isOwner = workspace.getOwnerId().equals(userId);

                    // ✅ Owner인 경우
                    if (isOwner) {
                        role = "OWNER";
                    }
                    // ✅ 멤버로 등록된 경우
                    else {
                        Optional<WorkspaceMember> membership = activeMemberships.stream()
                                .filter(m -> m.getWorkspaceId().equals(workspaceId))
                                .findFirst();

                        if (membership.isPresent()) {
                            role = membership.get().getRole().name(); // MEMBER / ADMIN 등
                        } else {
                            // ✅ 가입 신청 중인 경우
                            Optional<WorkspaceJoinRequest> request = approvedOrPendingRequests.stream()
                                    .filter(r -> r.getWorkspaceId().equals(workspaceId))
                                    .findFirst();
                            if (request.isPresent()) {
                                role = request.get().getStatus().name(); // APPROVED / PENDING 등
                            }
                        }
                    }

                    return UserWorkspaceResponse.builder()
                            .workspaceId(workspace.getWorkspaceId())
                            .workspaceName(workspace.getWorkspaceName())
                            .workspaceDescription(workspace.getWorkspaceDescription())
                            .createdAt(workspace.getCreatedAt())
                            .role(role)
                            .isOwner(isOwner)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 이름에 해당하는 모든 public Workspace 조회
     * 이름 없으면 다 가져옴
     */
    // 이름에 해당하는 모든 워크스페이스 가져오기
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> searchPublicWorkspaces(String query) {
        log.debug("Searching public workspaces with query: {}", query);

        List<Workspace> publicWorkspaces;

        if (query != null && !query.trim().isEmpty()) {
            // 이름으로 검색된 공개 워크스페이스 조회
            publicWorkspaces = workspaceRepository.findAllPublicWorkspacesByNameContaining(query.trim());
        } else {
            // 모든 공개 워크스페이스 조회
            publicWorkspaces = workspaceRepository.findAllPublicWorkspaces();
        }

        // owner 정보 없이 WorkspaceResponse로 변환
        return publicWorkspaces.stream()
                .map(workspace -> WorkspaceResponse.builder()
                        .workspaceId(workspace.getWorkspaceId())
                        .workspaceName(workspace.getWorkspaceName())
                        .needApproved(workspace.getNeedApproved())
                        .workspaceDescription(workspace.getWorkspaceDescription())
                        .createdAt(workspace.getCreatedAt())

                        .build())
                .collect(Collectors.toList());
    }

    /**
     * owner인 모든 워크스페이스 조회
     *
     */
    // 사용자 owner 등급 모든 워크스페이스 query 없으면 모든 워크스페이스 가져옴 private도
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getWorkspacesByOwner(UUID ownerId, String query) {
        log.debug("Fetching workspaces created by ownerId={} with query={}", ownerId, query);

        // 1️⃣ 한 번만 owner 조회
        User ownerUser = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        List<Workspace> workspaces;

        // 2️⃣ 조건 분기
        if (query != null && !query.trim().isEmpty()) {
            workspaces = workspaceRepository.findByOwnerIdAndWorkspaceNameContainingIgnoreCase(ownerId, query.trim());
        } else {
            workspaces = workspaceRepository.findByOwnerId(ownerId);
        }

        // 3️⃣ 변환
        return workspaces.stream()
                .map(workspace -> {
                    UserProfile ownerProfile = userProfileRepository
                            .findByWorkspaceIdAndUserId(workspace.getWorkspaceId(), ownerId)
                            .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));

                    return convertToWorkspaceResponse(workspace, ownerUser, ownerProfile);
                })
                .collect(Collectors.toList());
    }

    /**
     * 기본 Workspace 설정
     */
    public void setDefaultWorkspace(UUID workspaceId, UUID userId) {
        log.info("Setting default workspace: workspaceId={}, userId={}", workspaceId, userId);

        checkWorkspaceMember(workspaceId, userId);

        List<WorkspaceMember> userMembers = workspaceMemberRepository.findActiveByUserId(userId);
        userMembers.forEach(member -> {
            member.setDefault(false);
            workspaceMemberRepository.save(member);
        });

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> {
                    log.warn("User is not a member of workspace: workspaceId={}, userId={}", workspaceId, userId);
                    return new IllegalArgumentException("User is not a member of this workspace");
                });

        member.setDefault(true);
        workspaceMemberRepository.save(member);
        log.info("Default workspace set: workspaceId={}", workspaceId);
    }

    // ============================================================================
    // Workspace 멤버 관리
    // ============================================================================

    /**
     * 워크스페이스에 이메일 기반 사용자 초대
     */
    @Transactional
    public WorkspaceMemberResponse inviteUser(UUID workspaceId, InviteUserRequest request, UUID requesterId) {
        String query = request.getQuery().trim();
        log.info("Inviting user to workspace: workspaceId={}, query={}, requester={}", workspaceId, query, requesterId);

        checkWorkspaceAdminOrOwner(workspaceId, requesterId);

        // ✅ 이메일 형식인지 검사
        boolean isEmail = query.contains("@");

        // ✅ 사용자 조회
        Optional<User> targetUserOpt;

        targetUserOpt = userRepository.findByEmail(query);

        User targetUser = targetUserOpt
                .orElseThrow(() -> new UserNotFoundException("해당 " + (isEmail ? "이메일" : "이름") + "의 사용자를 찾을 수 없습니다."));

        // ✅ 이미 멤버인지 확인
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUser.getUserId())) {
            log.warn("User is already a member of workspace: workspaceId={}, userId={}", workspaceId,
                    targetUser.getUserId());
            throw new IllegalArgumentException("해당 사용자는 이미 워크스페이스 멤버입니다.");
        }

        // ✅ 새 멤버 등록
        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(targetUser.getUserId())
                .role(WorkspaceMember.WorkspaceRole.MEMBER)
                .isDefault(false)
                .isActive(true)
                .build();

        WorkspaceMember savedMember = workspaceMemberRepository.save(newMember);
        log.info("User invited and added as member: workspaceId={}, userId={}", workspaceId, targetUser.getUserId());

        return convertToWorkspaceMemberResponse(savedMember);
    }

    /**
     * 특정 워크스페이스의 모든 멤버 목록을 조회합니다 (UserProfile 포함).
     */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getWorkspaceMembers(UUID workspaceId, UUID currentUserId) {
        // 권한 확인 (멤버인지 확인) - 생략 가능하나 안정성을 위해 checkWorkspaceMember(workspaceId,
        // currentUserId);

        List<WorkspaceMember> members = workspaceMemberRepository.findAllByWorkspaceId(workspaceId);

        return members.stream()
                .<WorkspaceMemberResponse>map(member -> {
                    Optional<User> userOpt = userRepository.findById(member.getUserId());
                    // 워크스페이스별 프로필 조회 (워크스페이스 ID와 사용자 ID로 조회)
                    Optional<UserProfile> profileOpt = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, member.getUserId());

                    User user = userOpt.orElseGet(
                            () -> User.builder().userId(member.getUserId()).email("unknown@user.com").build());
                    
                    // 워크스페이스별 프로필이 없으면 기본 워크스페이스 프로필을 fallback으로 사용
                    UserProfile profile = profileOpt.orElseGet(() -> {
                        log.debug("Workspace-specific profile not found for userId={}, workspaceId={}. Falling back to default profile.", 
                                member.getUserId(), workspaceId);
                        return userProfileRepository.findByWorkspaceIdAndUserId(DEFAULT_WORKSPACE_ID, member.getUserId())
                                .orElseGet(() -> UserProfile.builder()
                                        .nickName("Deleted User")
                                        .userId(member.getUserId())
                                        .build());
                    });

                    return convertToWorkspaceMemberResponse(
                            member,
                            profile.getNickName(),
                            profile.getProfileImageUrl(),
                            user);
                })
                .collect(Collectors.toList());
    }

    /**
     * 멤버 역할 변경 (OWNER만)
     */
    public WorkspaceMemberResponse updateMemberRole(UUID workspaceId, UUID memberId, UpdateMemberRoleRequest request,
            UUID requesterId) {
        log.info("Updating member role: workspaceId={}, memberId={}, newRole={}, requester={}",
                workspaceId, memberId, request.getRoleName(), requesterId);

        checkWorkspaceOwner(workspaceId, requesterId);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("Member not found: {}", memberId);
                    return new IllegalArgumentException("Member not found");
                });

        if (!member.getWorkspaceId().equals(workspaceId)) {
            log.warn("Member does not belong to workspace: memberId={}, workspaceId={}", memberId, workspaceId);
            throw new IllegalArgumentException("Member does not belong to this workspace");
        }

        member.setRole(WorkspaceMember.WorkspaceRole.valueOf(request.getRoleName()));
        WorkspaceMember updated = workspaceMemberRepository.save(member);

        return convertToWorkspaceMemberResponse(updated);
    }

    /**
     * 멤버 제거 (OWNER/ADMIN만, OWNER는 제거 불가)
     */
    public void removeMember(UUID workspaceId, UUID memberId, UUID requesterId) {
        log.info("Removing member: workspaceId={}, memberId={}, requester={}", workspaceId, memberId, requesterId);

        checkWorkspaceAdminOrOwner(workspaceId, requesterId);

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("Member not found: {}", memberId);
                    return new IllegalArgumentException("Member not found");
                });

        if (!member.getWorkspaceId().equals(workspaceId)) {
            log.warn("Member does not belong to workspace: memberId={}, workspaceId={}", memberId, workspaceId);
            throw new IllegalArgumentException("Member does not belong to this workspace");
        }

        if (member.getRole() == WorkspaceMember.WorkspaceRole.OWNER) {
            log.warn("Cannot remove workspace owner: memberId={}", memberId);
            throw new IllegalArgumentException("Cannot remove workspace owner");
        }

        // requesterId가 memberId와 일치하는지 확인 (스스로를 제거하는 행위 방지)
        if (member.getUserId().equals(requesterId)) {
            log.warn("User cannot remove themselves: userId={}", requesterId);
            throw new IllegalArgumentException("Cannot remove yourself");
        }

        member.setIsActive(false);
        workspaceMemberRepository.save(member);
        log.info("Member removed: workspaceId={}, memberId={}", workspaceId, memberId);
    }

    // ============================================================================
    // 가입 신청 관리
    // ============================================================================

    /**
     * Workspace 가입 신청
     */
    public JoinRequestResponse createJoinRequest(UUID workspaceId, UUID userId) {
        log.info("Creating join request: workspaceId={}, userId={}", workspaceId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다.");
                });

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            log.warn("User is already a member of workspace: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("User is already a member of this workspace");
        }

        WorkspaceJoinRequest request = WorkspaceJoinRequest.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .status(WorkspaceJoinRequest.JoinRequestStatus.PENDING)
                .build();

        WorkspaceJoinRequest saved = workspaceJoinRequestRepository.save(request);
        log.info("Join request created: requestId={}", saved.getJoinRequestId());

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));

        return convertToJoinRequestResponse(saved, user, userProfile);
    }

    /**
     * 가입 신청 승인 (POST /{userId}/approve)
     */
    public void approveJoinRequest(UUID workspaceId, UUID userId, UUID responderId) {
        log.info("Approving join request: workspaceId={}, userId={}, responder={}", workspaceId, userId, responderId);

        checkWorkspaceAdminOrOwner(workspaceId, responderId);

        // ✅ 개선된 코드: Repository 메서드를 사용하여 DB 레벨에서 정확히 조회
        Optional<WorkspaceJoinRequest> pendingRequestOpt = workspaceJoinRequestRepository
                .findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId,
                        userId,
                        WorkspaceJoinRequest.JoinRequestStatus.PENDING);
        WorkspaceJoinRequest joinRequest = pendingRequestOpt.orElseThrow(() -> {
            log.warn("Pending join request not found for user: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("Pending join request not found");
        });

        // 멤버 추가
        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .role(WorkspaceMember.WorkspaceRole.MEMBER)
                .isDefault(false)
                .isActive(true)
                .build();
        workspaceMemberRepository.save(newMember);
        log.info("User approved and added as member: workspaceId={}, userId={}", workspaceId, userId);

        // 신청 상태 업데이트
        joinRequest.setStatus(WorkspaceJoinRequest.JoinRequestStatus.APPROVED);
        workspaceJoinRequestRepository.save(joinRequest);
    }

    /**
     * 가입 신청 거절 (POST /{userId}/reject)
     */
    public void rejectJoinRequest(UUID workspaceId, UUID userId, UUID responderId) {
        log.info("Rejecting join request: workspaceId={}, userId={}, responder={}", workspaceId, userId, responderId);

        checkWorkspaceAdminOrOwner(workspaceId, responderId);

        // ✅ 개선된 코드: Repository 메서드를 사용하여 DB 레벨에서 정확히 조회
        Optional<WorkspaceJoinRequest> pendingRequestOpt = workspaceJoinRequestRepository
                .findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId,
                        userId,
                        WorkspaceJoinRequest.JoinRequestStatus.PENDING);
        WorkspaceJoinRequest joinRequest = pendingRequestOpt.orElseThrow(() -> {
            log.warn("Pending join request not found for user: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("Pending join request not found");
        });

        // 신청 상태 업데이트 (거절)
        joinRequest.setStatus(WorkspaceJoinRequest.JoinRequestStatus.REJECTED);
        workspaceJoinRequestRepository.save(joinRequest);
        log.info("Join request rejected: workspaceId={}, userId={}", workspaceId, userId);
    }

    /**
     * 가입 신청 승인/거절 (기존 PUT /joinRequests/{requestId} 매핑)
     */
    public JoinRequestResponse updateJoinRequest(UUID workspaceId, UUID requestId,
            UpdateJoinRequestRequest updateRequest, UUID responderId) {
        log.info("Updating join request: workspaceId={}, requestId={}, status={}, responder={}",
                workspaceId, requestId, updateRequest.getStatus(), responderId);

        checkWorkspaceAdminOrOwner(workspaceId, responderId);

        WorkspaceJoinRequest joinRequest = workspaceJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.warn("Join request not found: {}", requestId);
                    return new IllegalArgumentException("Join request not found");
                });

        if (!joinRequest.getWorkspaceId().equals(workspaceId)) {
            log.warn("Join request does not belong to workspace: requestId={}, workspaceId={}", requestId, workspaceId);
            throw new IllegalArgumentException("Join request does not belong to this workspace");
        }

        WorkspaceJoinRequest.JoinRequestStatus newStatus = WorkspaceJoinRequest.JoinRequestStatus
                .valueOf(updateRequest.getStatus());

        if (newStatus == WorkspaceJoinRequest.JoinRequestStatus.APPROVED) {
            // 멤버 추가 로직
            WorkspaceMember newMember = WorkspaceMember.builder()
                    .workspaceId(workspaceId)
                    .userId(joinRequest.getUserId())
                    .role(WorkspaceMember.WorkspaceRole.MEMBER)
                    .isDefault(false)
                    .isActive(true)
                    .build();
            workspaceMemberRepository.save(newMember);
            log.info("User approved and added as member: workspaceId={}, userId={}", workspaceId,
                    joinRequest.getUserId());
        }

        joinRequest.setStatus(newStatus);
        WorkspaceJoinRequest updated = workspaceJoinRequestRepository.save(joinRequest);

        User user = userRepository.findById(joinRequest.getUserId())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        UserProfile userProfile = userProfileRepository.findByUserId(joinRequest.getUserId())
                .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));

        return convertToJoinRequestResponse(updated, user, userProfile);
    }

    /**
     * Workspace의 가입 신청 목록 조회 (OWNER/ADMIN만)
     */
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getJoinRequests(UUID workspaceId, UUID requesterId, String status) {
        log.debug("Fetching join requests: workspaceId={}, status={}", workspaceId, status);

        checkWorkspaceAdminOrOwner(workspaceId, requesterId);

        List<WorkspaceJoinRequest> requests;

        if (status != null && !status.isEmpty()) {
            // ✅ 수정: Repository의 findByWorkspaceIdAndStatus 메서드 사용
            WorkspaceJoinRequest.JoinRequestStatus requestStatus = WorkspaceJoinRequest.JoinRequestStatus
                    .valueOf(status.toUpperCase());
            requests = workspaceJoinRequestRepository.findByWorkspaceIdAndStatus(workspaceId, requestStatus);
            log.debug("Filtered join requests by status {}: count={}", status, requests.size());
        } else {
            requests = workspaceJoinRequestRepository.findByWorkspaceId(workspaceId);
            log.debug("Fetched all join requests: count={}", requests.size());
        }

        return requests.stream()
                .map(req -> {
                    User user = userRepository.findById(req.getUserId())
                            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
                    UserProfile userProfile = userProfileRepository.findByUserId(req.getUserId())
                            .orElseThrow(() -> new UserNotFoundException("프로필을 찾을 수 없습니다."));
                    return convertToJoinRequestResponse(req, user, userProfile);
                })
                .collect(Collectors.toList());
    }

    // ============================================================================
    // 워크스페이스 접근 확인 (Public API for Board Service)
    // ============================================================================

    /**
     * 워크스페이스 접근 권한 확인 (Board Service용 Public API)
     */
    @Transactional(readOnly = true)
    public boolean validateWorkspaceAccess(UUID workspaceId, UUID userId) {
        log.info("Validating workspace access: workspaceId={}, userId={}", workspaceId, userId);

        // 워크스페이스 존재 여부 확인
        boolean workspaceExists = workspaceRepository.existsById(workspaceId);
        log.info("Workspace exists check: workspaceId={}, exists={}", workspaceId, workspaceExists);
        if (!workspaceExists) {
            log.warn("Workspace not found: workspaceId={}", workspaceId);
            return false;
        }

        // 사용자가 워크스페이스 멤버인지 확인
        boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
        log.info("Workspace membership check: workspaceId={}, userId={}, isMember={}", workspaceId, userId, isMember);
        log.info("Workspace access validation result: workspaceId={}, userId={}, isValid={}", workspaceId, userId,
                isMember);
        return isMember;
    }

    // ============================================================================
    // 권한 확인 (Private Methods)
    // ============================================================================

    /**
     * Workspace 멤버 확인
     */
    private void checkWorkspaceMember(UUID workspaceId, UUID userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            log.warn("User is not a member of workspace: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("User is not a member of this workspace");
        }
    }

    /**
     * Workspace OWNER 확인
     */
    private void checkWorkspaceOwner(UUID workspaceId, UUID userId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> {
                    log.warn("User is not a member of workspace: workspaceId={}, userId={}", workspaceId, userId);
                    return new IllegalArgumentException("User is not a member of this workspace");
                });

        if (member.getRole() != WorkspaceMember.WorkspaceRole.OWNER) {
            log.warn("User is not OWNER of workspace: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("Only workspace owner can perform this action");
        }
    }

    /**
     * Workspace OWNER 또는 ADMIN 확인
     */
    private void checkWorkspaceAdminOrOwner(UUID workspaceId, UUID userId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> {
                    log.warn("User is not a member of workspace: workspaceId={}, userId={}", workspaceId, userId);
                    return new IllegalArgumentException("User is not a member of this workspace");
                });

        if (member.getRole() != WorkspaceMember.WorkspaceRole.OWNER &&
                member.getRole() != WorkspaceMember.WorkspaceRole.ADMIN) {
            log.warn("User is not OWNER or ADMIN of workspace: workspaceId={}, userId={}", workspaceId, userId);
            throw new IllegalArgumentException("Only workspace owner or admin can perform this action");
        }
    }

    // ============================================================================
    // DTO 변환 (Private Methods)
    // ============================================================================

    private WorkspaceResponse convertToWorkspaceResponse(Workspace workspace, User owner, UserProfile ownerProfile) {
        return WorkspaceResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .workspaceName(workspace.getWorkspaceName())
                .workspaceDescription(workspace.getWorkspaceDescription())
                .ownerId(owner.getUserId())
                .ownerName(ownerProfile.getNickName())
                .ownerEmail(owner.getEmail())
                .isPublic(workspace.getIsPublic())
                .needApproved(workspace.getNeedApproved())
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    /**
     * WorkspaceSettingsResponse DTO로 변환
     */
    private WorkspaceSettingsResponse convertToWorkspaceSettingsResponse(Workspace workspace) {
        return WorkspaceSettingsResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .workspaceName(workspace.getWorkspaceName())
                .workspaceDescription(workspace.getWorkspaceDescription())
                .isPublic(workspace.getIsPublic())
                .requiresApproval(workspace.getNeedApproved()) // BE needApproved -> FE requiresApproval 매핑
                .onlyOwnerCanInvite(false) // BE 엔티티에 필드 없음. 임시값
                .build();
    }

    private WorkspaceMemberResponse convertToWorkspaceMemberResponse(WorkspaceMember member, String userName,
            String profileImageUrl, User user) {
        return WorkspaceMemberResponse.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspaceId())
                .userId(member.getUserId())
                .userName(userName)
                .userEmail(user.getEmail())
                .profileImageUrl(profileImageUrl)
                .roleName(member.getRole().name())
                .isDefault(member.isDefault())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private WorkspaceMemberResponse convertToWorkspaceMemberResponse(WorkspaceMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElse(null);

        UserProfile userProfile = userProfileRepository.findByUserId(member.getUserId())
                .orElse(null);

        String userName = userProfile != null ? userProfile.getNickName() : "Deleted/Missing User";
        String userEmail = user != null ? user.getEmail() : "missing@user.com";
        String profileImageUrl = userProfile != null ? userProfile.getProfileImageUrl() : null;

        return WorkspaceMemberResponse.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspaceId())
                .userId(member.getUserId())
                .userName(userName)
                .userEmail(userEmail)
                .profileImageUrl(profileImageUrl)
                .roleName(member.getRole().name())
                .isDefault(member.isDefault())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private JoinRequestResponse convertToJoinRequestResponse(WorkspaceJoinRequest request, User user,
            UserProfile userProfile) {
        return JoinRequestResponse.builder()
                .id(request.getJoinRequestId())
                .workspaceId(request.getWorkspaceId())
                .userId(request.getUserId())
                .userName(userProfile.getNickName())
                .userEmail(user.getEmail())
                .status(request.getStatus().name())
                .requestedAt(request.getRequestedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

}