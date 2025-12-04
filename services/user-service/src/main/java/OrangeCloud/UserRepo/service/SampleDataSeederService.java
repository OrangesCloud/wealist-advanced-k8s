package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.client.BoardServiceClient;
import OrangeCloud.UserRepo.client.CreateBoardRequest;
import OrangeCloud.UserRepo.client.CreateCommentRequest;
import OrangeCloud.UserRepo.client.CreateProjectRequest;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.entity.WorkspaceMember;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceMemberRepository;
import OrangeCloud.UserRepo.util.InternalAuthTokenGenerator;
import OrangeCloud.UserRepo.util.SampleDataGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 샘플 데이터 생성 서비스
 * 개발 및 스테이징 환경에서만 활성화되어 워크스페이스 생성 시 샘플 데이터를 자동으로 생성합니다.
 */
@Service
@ConditionalOnProperty(name = "sample-data.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SampleDataSeederService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final SampleDataGenerator sampleDataGenerator;
    private final BoardServiceClient boardServiceClient;
    private final InternalAuthTokenGenerator authTokenGenerator;

    private static final int MAX_RETRIES = 3;
    private static final int PROJECT_COUNT = 2;
    private static final int BOARD_COUNT = 20;
    private static final int BOARDS_PER_PROJECT = BOARD_COUNT / PROJECT_COUNT; // 10 per project

    /**
     * 워크스페이스에 대한 샘플 데이터를 비동기로 생성합니다.
     * 
     * @param workspaceId 생성된 워크스페이스 ID
     * @param ownerId 워크스페이스 소유자 ID
     */
    @Async("sampleDataExecutor")
    public void seedWorkspaceData(UUID workspaceId, UUID ownerId) {
        try {
            log.info("Starting sample data generation for workspace: {}", workspaceId);
            long startTime = System.currentTimeMillis();

            // 샘플 사용자 생성 (owner 포함 10명)
            List<UUID> userIds = createSampleUsersWithErrorHandling(workspaceId, ownerId);
            
            if (userIds.isEmpty()) {
                log.error("No users available for workspace {}, cannot create projects/boards", workspaceId);
                return;
            }

            // 샘플 프로젝트 생성 (2개)
            List<UUID> projectIds = createSampleProjectsWithErrorHandling(workspaceId, ownerId, userIds);
            
            if (projectIds.isEmpty()) {
                log.warn("No projects created for workspace {}, skipping board/comment creation", workspaceId);
                return;
            }

            // 샘플 보드 생성 (20개)
            List<UUID> boardIds = createSampleBoardsWithErrorHandling(projectIds, userIds, ownerId);

            // 샘플 댓글 생성 (각 보드당 0~10개)
            createSampleCommentsWithErrorHandling(boardIds, userIds, ownerId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Sample data generation completed for workspace: {} in {}ms", workspaceId, duration);

        } catch (Exception e) {
            log.error("Sample data generation failed for workspace: {}", workspaceId, e);
            // 예외를 전파하지 않음 - 워크스페이스 생성은 성공해야 함
        }
    }

    /**
     * 기존 사용자를 워크스페이스에 추가합니다.
     * UserProfile과 WorkspaceMember를 생성합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param userId 사용자 ID
     * @param role 워크스페이스 역할
     * @param email 사용자 이메일
     * @param nickName 사용자 닉네임
     */
    private void addExistingUserToWorkspace(UUID workspaceId, UUID userId, WorkspaceMember.WorkspaceRole role, 
                                           String email, String nickName) {
        // UserProfile이 이미 존재하는지 확인
        Optional<UserProfile> existingProfile = userProfileRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (existingProfile.isEmpty()) {
            UserProfile userProfile = UserProfile.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .nickName(nickName)
                    .email(email)
                    .profileImageUrl(null)
                    .build();
            userProfileRepository.save(userProfile);
            log.debug("Created user profile for existing user: userId={}, nickName={}", userId, nickName);
        }

        // WorkspaceMember가 이미 존재하는지 확인
        Optional<WorkspaceMember> existingMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (existingMember.isEmpty()) {
            WorkspaceMember workspaceMember = WorkspaceMember.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .role(role)
                    .isDefault(false)
                    .isActive(true)
                    .build();
            workspaceMemberRepository.save(workspaceMember);
            log.debug("Created workspace member for existing user: userId={}, role={}", userId, role);
        }
    }

    /**
     * 샘플 사용자 10명을 생성합니다 (owner 포함).
     * 오류 처리를 포함하여 각 사용자 생성 실패 시에도 계속 진행합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param ownerId 워크스페이스 소유자 ID (이미 존재)
     * @return 생성된 사용자 ID 목록 (owner 포함)
     */
    @Transactional
    private List<UUID> createSampleUsersWithErrorHandling(UUID workspaceId, UUID ownerId) {
        List<UUID> createdUserIds = new ArrayList<>();
        createdUserIds.add(ownerId); // Owner는 이미 존재

        // 역할 분배: 1 OWNER (이미 존재), 3 ADMIN, 6 MEMBER
        // 추가로 생성할 사용자: 3 ADMIN + 6 MEMBER = 9명
        int adminsToCreate = 3;
        int membersToCreate = 6;

        int createdAdmins = 0;
        int createdMembers = 0;

        for (int i = 0; i < 9; i++) {
            try {
                WorkspaceMember.WorkspaceRole role;
                if (createdAdmins < adminsToCreate) {
                    role = WorkspaceMember.WorkspaceRole.ADMIN;
                    createdAdmins++;
                } else {
                    role = WorkspaceMember.WorkspaceRole.MEMBER;
                    createdMembers++;
                }

                UUID userId = createSingleSampleUser(workspaceId, i, role);
                createdUserIds.add(userId);
            } catch (Exception e) {
                log.warn("Failed to create sample user {} for workspace {}: {}", 
                         i, workspaceId, e.getMessage());
                // 계속 진행
            }
        }

        log.info("Created {} sample users for workspace {} (1 OWNER, {} ADMIN, {} MEMBER)", 
                 createdUserIds.size(), workspaceId, createdAdmins, createdMembers);
        return createdUserIds;
    }

    /**
     * 단일 샘플 사용자를 생성합니다.
     * User, UserProfile, WorkspaceMember를 모두 생성합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param index 사용자 인덱스 (이메일 생성용)
     * @param role 워크스페이스 역할
     * @return 생성된 사용자 ID
     */
    private UUID createSingleSampleUser(UUID workspaceId, int index, WorkspaceMember.WorkspaceRole role) {
        // 1. User 엔티티 생성
        // 워크스페이스 ID의 앞 8자리를 사용하여 유니크한 이메일 생성
        String workspacePrefix = workspaceId.toString().substring(0, 8);
        String email = String.format("sample.user%d.%s@example.com", index + 1, workspacePrefix);
        String koreanName = sampleDataGenerator.generateKoreanName();

        // 이미 존재하는 이메일인지 확인
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            log.warn("User with email {} already exists, skipping creation", email);
            // 기존 사용자를 워크스페이스 멤버로 추가
            User user = existingUser.get();
            addExistingUserToWorkspace(workspaceId, user.getUserId(), role, email, koreanName);
            return user.getUserId();
        }

        User user = User.builder()
                .email(email)
                .provider("sample")
                .googleId(null)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.debug("Created sample user: userId={}, email={}", savedUser.getUserId(), email);

        // 2. UserProfile 생성
        UserProfile userProfile = UserProfile.builder()
                .workspaceId(workspaceId)
                .userId(savedUser.getUserId())
                .nickName(koreanName)
                .email(email)
                .profileImageUrl(null)
                .build();

        userProfileRepository.save(userProfile);
        log.debug("Created user profile: userId={}, nickName={}", savedUser.getUserId(), koreanName);

        // 3. WorkspaceMember 생성
        WorkspaceMember workspaceMember = WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(savedUser.getUserId())
                .role(role)
                .isDefault(false)
                .isActive(true)
                .build();

        workspaceMemberRepository.save(workspaceMember);
        log.debug("Created workspace member: userId={}, role={}", savedUser.getUserId(), role);

        return savedUser.getUserId();
    }

    /**
     * 샘플 프로젝트 2개를 생성합니다.
     * 오류 처리를 포함하여 각 프로젝트 생성 실패 시에도 계속 진행합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param ownerId 프로젝트 소유자 ID
     * @param memberIds 프로젝트 멤버 ID 목록
     * @return 생성된 프로젝트 ID 목록
     */
    private List<UUID> createSampleProjectsWithErrorHandling(UUID workspaceId, UUID ownerId, List<UUID> memberIds) {
        List<UUID> createdProjectIds = new ArrayList<>();

        try {
            // 인증 토큰 생성
            String authToken = authTokenGenerator.generateInternalToken(ownerId);
            log.debug("Generated auth token for project creation: ownerId={}", ownerId);

            for (int i = 0; i < PROJECT_COUNT; i++) {
                try {
                    log.info("Attempting to create project {} of {} for workspace {}", 
                             i + 1, PROJECT_COUNT, workspaceId);
                    UUID projectId = createSingleProjectWithRetry(workspaceId, ownerId, i, authToken);
                    if (projectId != null) {
                        createdProjectIds.add(projectId);
                        log.info("Successfully created project {} for workspace {}: projectId={}", 
                                 i + 1, workspaceId, projectId);
                    } else {
                        log.warn("Project creation returned null for project {} of workspace {}", 
                                 i + 1, workspaceId);
                    }
                } catch (Exception e) {
                    log.error("Failed to create sample project {} for workspace {}: {}", 
                             i + 1, workspaceId, e.getMessage(), e);
                    // 계속 진행
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize project creation for workspace {}: {}", 
                     workspaceId, e.getMessage(), e);
        }

        log.info("Created {} out of {} sample projects for workspace {}", 
                 createdProjectIds.size(), PROJECT_COUNT, workspaceId);
        return createdProjectIds;
    }

    /**
     * 단일 샘플 프로젝트를 재시도 로직과 함께 생성합니다.
     * 
     * @param workspaceId 워크스페이스 ID
     * @param ownerId 프로젝트 소유자 ID
     * @param index 프로젝트 인덱스
     * @param authToken 인증 토큰
     * @return 생성된 프로젝트 ID, 실패 시 null
     */
    private UUID createSingleProjectWithRetry(UUID workspaceId, UUID ownerId, int index, String authToken) {
        String projectName = sampleDataGenerator.generateProjectName();
        String description = generateProjectDescription(index);

        CreateProjectRequest request = CreateProjectRequest.builder()
                .workspaceId(workspaceId)
                .name(projectName)
                .description(description)
                .build();

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                UUID projectId = boardServiceClient.createProject(request, authToken);
                log.info("Created sample project: projectId={}, name={}, attempt={}", 
                         projectId, projectName, attempt + 1);
                return projectId;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                    log.warn("Project creation failed, retrying in {}ms... (attempt {}/{})", 
                             backoffMs, attempt, MAX_RETRIES);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Project creation retry interrupted", ie);
                        break;
                    }
                }
            }
        }

        log.error("Failed to create project after {} attempts: name={}", MAX_RETRIES, projectName, lastException);
        return null;
    }

    /**
     * 프로젝트 인덱스에 따라 적절한 설명을 생성합니다.
     * 
     * @param index 프로젝트 인덱스
     * @return 프로젝트 설명
     */
    private String generateProjectDescription(int index) {
        if (index == 0) {
            return "고객 관리 시스템 구축 프로젝트";
        } else {
            return "사용자 경험 개선을 위한 앱 리뉴얼 프로젝트";
        }
    }

    /**
     * 샘플 보드 20개를 생성합니다.
     * 오류 처리를 포함하여 각 보드 생성 실패 시에도 계속 진행합니다.
     * 
     * @param projectIds 프로젝트 ID 목록
     * @param userIds 사용자 ID 목록
     * @param ownerId 워크스페이스 소유자 ID
     * @return 생성된 보드 ID 목록
     */
    private List<UUID> createSampleBoardsWithErrorHandling(List<UUID> projectIds, List<UUID> userIds, UUID ownerId) {
        List<UUID> createdBoardIds = new ArrayList<>();

        if (projectIds.isEmpty()) {
            log.warn("No projects available for board creation");
            return createdBoardIds;
        }

        if (userIds.isEmpty()) {
            log.warn("No users available for board creation");
            return createdBoardIds;
        }

        // 인증 토큰 생성
        String authToken = authTokenGenerator.generateInternalToken(ownerId);

        // 보드를 프로젝트에 균등하게 분배 (각 프로젝트에 10개씩)
        for (int i = 0; i < projectIds.size(); i++) {
            UUID projectId = projectIds.get(i);
            
            for (int j = 0; j < BOARDS_PER_PROJECT; j++) {
                try {
                    int boardIndex = i * BOARDS_PER_PROJECT + j;
                    UUID boardId = createSingleBoardWithRetry(projectId, userIds, boardIndex, authToken);
                    if (boardId != null) {
                        createdBoardIds.add(boardId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to create sample board {} for project {}: {}", 
                             j, projectId, e.getMessage());
                    // 계속 진행
                }
            }
        }

        log.info("Created {} sample boards", createdBoardIds.size());
        return createdBoardIds;
    }

    /**
     * 단일 샘플 보드를 재시도 로직과 함께 생성합니다.
     * 
     * @param projectId 프로젝트 ID
     * @param userIds 사용자 ID 목록
     * @param boardIndex 보드 인덱스
     * @param authToken 인증 토큰
     * @return 생성된 보드 ID, 실패 시 null
     */
    private UUID createSingleBoardWithRetry(UUID projectId, List<UUID> userIds, int boardIndex, String authToken) {
        String title = sampleDataGenerator.generateBoardTitle();
        String content = generateBoardContent(title);
        
        // 랜덤하게 assignee 선택
        UUID assigneeId = userIds.get(new Random().nextInt(userIds.size()));
        
        // 커스텀 필드 생성 (status, priority)
        Map<String, Object> customFields = sampleDataGenerator.generateBoardCustomFields();
        
        // 날짜 생성: startDate는 -30일 ~ +30일, dueDate는 +1일 ~ +90일
        LocalDateTime startDate = sampleDataGenerator.generateRandomDate(-30, 30);
        LocalDateTime dueDate = sampleDataGenerator.generateRandomDate(1, 90);

        CreateBoardRequest request = CreateBoardRequest.builder()
                .projectId(projectId)
                .assigneeId(assigneeId)
                .title(title)
                .content(content)
                .customFields(customFields)
                .startDate(startDate)
                .dueDate(dueDate)
                .build();

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                UUID boardId = boardServiceClient.createBoard(request, authToken);
                log.info("Created sample board: boardId={}, title={}, status={}, attempt={}", 
                         boardId, title, customFields.get("status"), attempt + 1);
                return boardId;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                    log.warn("Board creation failed, retrying in {}ms... (attempt {}/{})", 
                             backoffMs, attempt, MAX_RETRIES);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Board creation retry interrupted", ie);
                        break;
                    }
                }
            }
        }

        log.error("Failed to create board after {} attempts: title={}", MAX_RETRIES, title, lastException);
        return null;
    }

    /**
     * 보드 제목에 따라 적절한 내용을 생성합니다.
     * 
     * @param title 보드 제목
     * @return 보드 내용
     */
    private String generateBoardContent(String title) {
        return title + "에 대한 상세 내용입니다.";
    }

    /**
     * 각 보드에 0~10개의 샘플 댓글을 생성합니다.
     * 오류 처리를 포함하여 각 댓글 생성 실패 시에도 계속 진행합니다.
     * 
     * @param boardIds 보드 ID 목록
     * @param userIds 사용자 ID 목록
     * @param ownerId 워크스페이스 소유자 ID
     */
    private void createSampleCommentsWithErrorHandling(List<UUID> boardIds, List<UUID> userIds, UUID ownerId) {
        if (boardIds.isEmpty()) {
            log.warn("No boards available for comment creation");
            return;
        }

        if (userIds.isEmpty()) {
            log.warn("No users available for comment creation");
            return;
        }

        // 인증 토큰 생성
        String authToken = authTokenGenerator.generateInternalToken(ownerId);

        int totalCommentsCreated = 0;
        Random random = new Random();

        for (UUID boardId : boardIds) {
            // 각 보드당 0~10개의 댓글을 랜덤하게 생성
            int commentCount = random.nextInt(11); // 0 to 10 inclusive

            for (int i = 0; i < commentCount; i++) {
                try {
                    UUID commentId = createSingleCommentWithRetry(boardId, userIds, authToken);
                    if (commentId != null) {
                        totalCommentsCreated++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to create sample comment {} for board {}: {}", 
                             i, boardId, e.getMessage());
                    // 계속 진행
                }
            }
        }

        log.info("Created {} sample comments across {} boards", totalCommentsCreated, boardIds.size());
    }

    /**
     * 단일 샘플 댓글을 재시도 로직과 함께 생성합니다.
     * 
     * @param boardId 보드 ID
     * @param userIds 사용자 ID 목록
     * @param authToken 인증 토큰
     * @return 생성된 댓글 ID, 실패 시 null
     */
    private UUID createSingleCommentWithRetry(UUID boardId, List<UUID> userIds, String authToken) {
        // 랜덤하게 댓글 작성자 선택
        UUID authorId = userIds.get(new Random().nextInt(userIds.size()));
        
        // 랜덤한 댓글 내용 생성
        String content = sampleDataGenerator.generateCommentContent();

        CreateCommentRequest request = CreateCommentRequest.builder()
                .boardId(boardId)
                .content(content)
                .build();

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                UUID commentId = boardServiceClient.createComment(request, authToken);
                log.debug("Created sample comment: commentId={}, boardId={}, authorId={}, attempt={}", 
                         commentId, boardId, authorId, attempt + 1);
                return commentId;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                    log.warn("Comment creation failed, retrying in {}ms... (attempt {}/{})", 
                             backoffMs, attempt, MAX_RETRIES);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Comment creation retry interrupted", ie);
                        break;
                    }
                }
            }
        }

        log.error("Failed to create comment after {} attempts for board: {}", MAX_RETRIES, boardId, lastException);
        return null;
    }
}
