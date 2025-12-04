package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.dto.userprofile.PresignedUrlRequest;
import OrangeCloud.UserRepo.dto.userprofile.UpdateProfileImageByKeyRequest;
import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import OrangeCloud.UserRepo.entity.Workspace;
import OrangeCloud.UserRepo.repository.UserProfileRepository;
import OrangeCloud.UserRepo.repository.UserRepository;
import OrangeCloud.UserRepo.repository.WorkspaceRepository;
import OrangeCloud.UserRepo.dto.userprofile.PresignedUrlResponse;
import OrangeCloud.UserRepo.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProfileImage 간소화된 통합 테스트
 * 핵심 기능만 테스트합니다.
 * 
 * TODO: S3 Mock 설정 필요 - 현재 S3Service 의존성 문제로 비활성화
 */
@Disabled("S3 Mock 설정 필요")
@SpringBootTest(classes = {OrangeCloud.UserRepo.UserRepoApplication.class, OrangeCloud.UserRepo.config.TestRedisConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileImageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @SpyBean
    private S3Service s3Service;

    private User testUser;
    private Workspace testWorkspace;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .email("test@example.com")
                .provider("google")
                .googleId("test-google-id")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        testWorkspace = Workspace.builder()
                .ownerId(testUser.getUserId())
                .workspaceName("Test Workspace")
                .workspaceDescription("Test Description")
                .isPublic(true)
                .needApproved(false)
                .isActive(true)
                .build();
        testWorkspace = workspaceRepository.save(testWorkspace);

        testProfile = UserProfile.builder()
                .userId(testUser.getUserId())
                .workspaceId(testWorkspace.getWorkspaceId())
                .nickName("Test Nickname")
                .email(testUser.getEmail())
                .build();
        testProfile = userProfileRepository.save(testProfile);

        // Mock S3Service - generatePresignedUrl 메서드
        PresignedUrlResponse mockResponse = new PresignedUrlResponse(
                "https://mock-s3-bucket.s3.amazonaws.com/test-file?presigned=true",
                "user/" + testWorkspace.getWorkspaceId() + "/test-file.jpg",
                300
        );
        doReturn(mockResponse)
                .when(s3Service)
                .generatePresignedUrl(any(UUID.class), any(UUID.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Presigned URL 생성 테스트")
    void testGeneratePresignedUrl() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                testWorkspace.getWorkspaceId(),
                "avatar.jpg",
                512000L,
                "image/jpeg"
        );

        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .with(user(testUser.getUserId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.fileKey").exists())
                .andExpect(jsonPath("$.fileKey").value(org.hamcrest.Matchers.startsWith("user/" + testWorkspace.getWorkspaceId())))
                .andExpect(jsonPath("$.expiresIn").value(300));
    }



    @Test
    @DisplayName("파일 크기 검증 - 20MB 제한")
    void testFileSizeValidation() throws Exception {
        // 20MB는 성공
        PresignedUrlRequest validRequest = new PresignedUrlRequest(
                testWorkspace.getWorkspaceId(),
                "large.jpg",
                20 * 1024 * 1024L,
                "image/jpeg"
        );

        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .with(user(testUser.getUserId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk());

        // 21MB는 실패
        PresignedUrlRequest invalidRequest = new PresignedUrlRequest(
                testWorkspace.getWorkspaceId(),
                "too-large.jpg",
                21 * 1024 * 1024L,
                "image/jpeg"
        );

        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .with(user(testUser.getUserId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 파일 타입 거부")
    void testInvalidFileTypeRejection() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                testWorkspace.getWorkspaceId(),
                "document.pdf",
                512000L,
                "application/pdf"
        );

        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .with(user(testUser.getUserId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 attachmentId로 프로필 업데이트 실패")
    void testUpdateProfileWithInvalidAttachmentId() throws Exception {
        UpdateProfileImageByKeyRequest request = new UpdateProfileImageByKeyRequest(
                testWorkspace.getWorkspaceId(),
                UUID.randomUUID() // 존재하지 않는 attachmentId
        );

        mockMvc.perform(put("/api/profiles/me/image")
                        .with(user(testUser.getUserId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
