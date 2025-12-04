package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.client.BoardServiceClient;
import OrangeCloud.UserRepo.client.CreateBoardRequest;
import OrangeCloud.UserRepo.client.CreateProjectRequest;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.entity.WorkspaceMember;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceMemberRepository;
import OrangeCloud.UserRepo.util.InternalAuthTokenGenerator;
import OrangeCloud.UserRepo.util.SampleDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SampleDataSeederService
 */
@ExtendWith(MockitoExtension.class)
class SampleDataSeederServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private SampleDataGenerator sampleDataGenerator;

    @Mock
    private BoardServiceClient boardServiceClient;

    @Mock
    private InternalAuthTokenGenerator authTokenGenerator;

    @InjectMocks
    private SampleDataSeederService sampleDataSeederService;

    private UUID testWorkspaceId;
    private UUID testOwnerId;

    @BeforeEach
    void setUp() {
        testWorkspaceId = UUID.randomUUID();
        testOwnerId = UUID.randomUUID();
    }

    @Test
    void seedWorkspaceData_shouldCreateSampleUsers() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify 9 users were created (owner already exists)
        verify(userRepository, times(9)).save(any(User.class));
        verify(userProfileRepository, times(9)).save(any(UserProfile.class));
        verify(workspaceMemberRepository, times(9)).save(any(WorkspaceMember.class));
    }

    @Test
    void createSampleUsers_shouldCreateCorrectRoleDistribution() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Capture all WorkspaceMember saves
        ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberRepository, times(9)).save(memberCaptor.capture());

        List<WorkspaceMember> savedMembers = memberCaptor.getAllValues();

        // Count roles
        long adminCount = savedMembers.stream()
                .filter(m -> m.getRole() == WorkspaceMember.WorkspaceRole.ADMIN)
                .count();
        long memberCount = savedMembers.stream()
                .filter(m -> m.getRole() == WorkspaceMember.WorkspaceRole.MEMBER)
                .count();

        // Verify role distribution: 3 ADMIN, 6 MEMBER
        assertThat(adminCount).isEqualTo(3);
        assertThat(memberCount).isEqualTo(6);
    }

    @Test
    void createSampleUsers_shouldCreateUserProfileForEachUser() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify UserProfile was created for each user
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(9)).save(profileCaptor.capture());

        List<UserProfile> savedProfiles = profileCaptor.getAllValues();

        // All profiles should have the correct workspace ID
        assertThat(savedProfiles)
                .allMatch(profile -> profile.getWorkspaceId().equals(testWorkspaceId));

        // All profiles should have a Korean name
        assertThat(savedProfiles)
                .allMatch(profile -> profile.getNickName() != null && !profile.getNickName().isEmpty());
    }

    @Test
    void createSampleUsers_shouldHandleErrorsGracefully() {
        // Given - First user creation fails
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenThrow(new RuntimeException("Database error"))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        // When - Should not throw exception
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Should have attempted to create all users despite first failure
        verify(userRepository, times(9)).save(any(User.class));
    }

    @Test
    void createSampleUsers_shouldUseCorrectEmailFormat() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify email format includes workspace prefix
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(9)).save(userCaptor.capture());

        List<User> savedUsers = userCaptor.getAllValues();
        String workspacePrefix = testWorkspaceId.toString().substring(0, 8);

        // All users should have email in format sample.userN.{workspacePrefix}@example.com
        for (int i = 0; i < savedUsers.size(); i++) {
            String expectedEmail = String.format("sample.user%d.%s@example.com", i + 1, workspacePrefix);
            assertThat(savedUsers.get(i).getEmail()).isEqualTo(expectedEmail);
        }
    }

    @Test
    void createSampleUsers_shouldSetCorrectUserProperties() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify user properties
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getProvider()).isEqualTo("sample");
        assertThat(savedUser.getGoogleId()).isNull();
        assertThat(savedUser.getIsActive()).isTrue();
    }

    @Test
    void seedWorkspaceData_shouldCreateSampleBoards() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(sampleDataGenerator.generateProjectName())
                .thenReturn("웹 애플리케이션 개발", "모바일 앱 리뉴얼");

        when(sampleDataGenerator.generateBoardTitle())
                .thenReturn("로그인 기능 구현", "회원가입 페이지 디자인", "데이터베이스 스키마 설계",
                           "API 엔드포인트 개발", "프론트엔드 컴포넌트 작성", "테스트 코드 작성",
                           "배포 파이프라인 구축", "성능 최적화", "보안 취약점 점검",
                           "사용자 피드백 반영", "문서화 작업", "코드 리뷰",
                           "버그 수정", "UI/UX 개선", "알림 기능 추가",
                           "검색 기능 구현", "파일 업로드 기능", "권한 관리 시스템",
                           "대시보드 개발", "리포트 생성 기능");

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("status", "TODO");
        customFields.put("priority", "HIGH");
        when(sampleDataGenerator.generateBoardCustomFields()).thenReturn(customFields);

        when(sampleDataGenerator.generateRandomDate(-30, 30))
                .thenReturn(LocalDateTime.now());
        when(sampleDataGenerator.generateRandomDate(1, 90))
                .thenReturn(LocalDateTime.now().plusDays(30));

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(authTokenGenerator.generateInternalToken(any(UUID.class)))
                .thenReturn("test-token");

        when(boardServiceClient.createProject(any(CreateProjectRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        when(boardServiceClient.createBoard(any(CreateBoardRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify 2 projects and 20 boards were created
        verify(boardServiceClient, times(2)).createProject(any(CreateProjectRequest.class), anyString());
        verify(boardServiceClient, times(20)).createBoard(any(CreateBoardRequest.class), anyString());
    }

    @Test
    void createSampleBoards_shouldDistributeBoardsAcrossProjects() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(sampleDataGenerator.generateProjectName())
                .thenReturn("웹 애플리케이션 개발", "모바일 앱 리뉴얼");

        when(sampleDataGenerator.generateBoardTitle())
                .thenReturn("로그인 기능 구현", "회원가입 페이지 디자인", "데이터베이스 스키마 설계",
                           "API 엔드포인트 개발", "프론트엔드 컴포넌트 작성", "테스트 코드 작성",
                           "배포 파이프라인 구축", "성능 최적화", "보안 취약점 점검",
                           "사용자 피드백 반영", "문서화 작업", "코드 리뷰",
                           "버그 수정", "UI/UX 개선", "알림 기능 추가",
                           "검색 기능 구현", "파일 업로드 기능", "권한 관리 시스템",
                           "대시보드 개발", "리포트 생성 기능");

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("status", "IN_PROGRESS");
        customFields.put("priority", "MEDIUM");
        when(sampleDataGenerator.generateBoardCustomFields()).thenReturn(customFields);

        when(sampleDataGenerator.generateRandomDate(-30, 30))
                .thenReturn(LocalDateTime.now());
        when(sampleDataGenerator.generateRandomDate(1, 90))
                .thenReturn(LocalDateTime.now().plusDays(30));

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(authTokenGenerator.generateInternalToken(any(UUID.class)))
                .thenReturn("test-token");

        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();
        when(boardServiceClient.createProject(any(CreateProjectRequest.class), anyString()))
                .thenReturn(projectId1, projectId2);

        when(boardServiceClient.createBoard(any(CreateBoardRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Capture all board creation requests
        ArgumentCaptor<CreateBoardRequest> boardCaptor = ArgumentCaptor.forClass(CreateBoardRequest.class);
        verify(boardServiceClient, times(20)).createBoard(boardCaptor.capture(), anyString());

        List<CreateBoardRequest> boardRequests = boardCaptor.getAllValues();

        // Count boards per project (should be 10 each)
        long project1Boards = boardRequests.stream()
                .filter(req -> req.getProjectId().equals(projectId1))
                .count();
        long project2Boards = boardRequests.stream()
                .filter(req -> req.getProjectId().equals(projectId2))
                .count();

        assertThat(project1Boards).isEqualTo(10);
        assertThat(project2Boards).isEqualTo(10);
    }

    @Test
    void createSampleBoards_shouldIncludeCustomFields() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(sampleDataGenerator.generateProjectName())
                .thenReturn("웹 애플리케이션 개발", "모바일 앱 리뉴얼");

        when(sampleDataGenerator.generateBoardTitle())
                .thenReturn("로그인 기능 구현");

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("status", "DONE");
        customFields.put("priority", "LOW");
        when(sampleDataGenerator.generateBoardCustomFields()).thenReturn(customFields);

        when(sampleDataGenerator.generateRandomDate(-30, 30))
                .thenReturn(LocalDateTime.now());
        when(sampleDataGenerator.generateRandomDate(1, 90))
                .thenReturn(LocalDateTime.now().plusDays(30));

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(authTokenGenerator.generateInternalToken(any(UUID.class)))
                .thenReturn("test-token");

        when(boardServiceClient.createProject(any(CreateProjectRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        when(boardServiceClient.createBoard(any(CreateBoardRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        // When
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Verify custom fields are included
        ArgumentCaptor<CreateBoardRequest> boardCaptor = ArgumentCaptor.forClass(CreateBoardRequest.class);
        verify(boardServiceClient, atLeastOnce()).createBoard(boardCaptor.capture(), anyString());

        CreateBoardRequest boardRequest = boardCaptor.getValue();
        assertThat(boardRequest.getCustomFields()).isNotNull();
        assertThat(boardRequest.getCustomFields().get("status")).isEqualTo("DONE");
        assertThat(boardRequest.getCustomFields().get("priority")).isEqualTo("LOW");
    }

    @Test
    void createSampleBoards_shouldHandleErrorsGracefully() {
        // Given
        when(sampleDataGenerator.generateKoreanName())
                .thenReturn("김철수", "이영희", "박민수", "정수진", "최동욱",
                           "강서연", "윤지호", "임하늘", "한소희");

        when(sampleDataGenerator.generateProjectName())
                .thenReturn("웹 애플리케이션 개발", "모바일 앱 리뉴얼");

        when(sampleDataGenerator.generateBoardTitle())
                .thenReturn("로그인 기능 구현");

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("status", "TODO");
        customFields.put("priority", "HIGH");
        when(sampleDataGenerator.generateBoardCustomFields()).thenReturn(customFields);

        when(sampleDataGenerator.generateRandomDate(-30, 30))
                .thenReturn(LocalDateTime.now());
        when(sampleDataGenerator.generateRandomDate(1, 90))
                .thenReturn(LocalDateTime.now().plusDays(30));

        when(userRepository.findByEmail(anyString()))
                .thenReturn(java.util.Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setUserId(UUID.randomUUID());
                    return user;
                });

        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(authTokenGenerator.generateInternalToken(any(UUID.class)))
                .thenReturn("test-token");

        when(boardServiceClient.createProject(any(CreateProjectRequest.class), anyString()))
                .thenReturn(UUID.randomUUID());

        // First board creation fails all retries (3 attempts), rest succeed
        when(boardServiceClient.createBoard(any(CreateBoardRequest.class), anyString()))
                .thenThrow(new RuntimeException("API error"))
                .thenThrow(new RuntimeException("API error"))
                .thenThrow(new RuntimeException("API error"))
                .thenReturn(UUID.randomUUID());

        // When - Should not throw exception
        sampleDataSeederService.seedWorkspaceData(testWorkspaceId, testOwnerId);

        // Then - Should have attempted to create all boards despite first failure
        // First board: 3 retries, remaining 19 boards: 1 attempt each = 3 + 19 = 22 total attempts
        verify(boardServiceClient, atLeast(20)).createBoard(any(CreateBoardRequest.class), anyString());
    }
}
