package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.dto.userprofile.PresignedUrlRequest;
import OrangeCloud.UserRepo.dto.userprofile.UpdateProfileImageByKeyRequest;
import OrangeCloud.UserRepo.dto.userprofile.UpdateProfileRequest;
import OrangeCloud.UserRepo.dto.userprofile.UserProfileResponse;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import OrangeCloud.UserRepo.exception.GlobalExceptionHandler;
import OrangeCloud.UserRepo.exception.UserNotFoundException;
import OrangeCloud.UserRepo.service.S3Service;
import OrangeCloud.UserRepo.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProfileImageController 단위 테스트
 * Requirements: 2.1, 2.2
 */
@ExtendWith(MockitoExtension.class)
class ProfileImageControllerTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private ProfileImageController profileImageController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileImageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    private Principal createMockPrincipal(UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userId.toString());
        return authentication;
    }

    @Test
    @DisplayName("유효한 요청으로 Presigned URL 생성 성공")
    void generatePresignedUrl_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "avatar.jpg",
                512000L,
                "image/jpeg"
        );

        S3Service.PresignedUrlResponse s3Response = new S3Service.PresignedUrlResponse(
                "https://test-bucket.s3.amazonaws.com/user/test/2024/01/user_123456789.jpg?signature=xyz",
                "user/" + workspaceId + "/2024/01/" + userId + "_123456789.jpg",
                300
        );

        when(s3Service.generatePresignedUrl(eq(workspaceId), eq(userId), eq("avatar.jpg"), eq("image/jpeg")))
                .thenReturn(s3Response);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(s3Response.getUploadUrl()))
                .andExpect(jsonPath("$.fileKey").value(s3Response.getFileKey()))
                .andExpect(jsonPath("$.expiresIn").value(300));

        verify(s3Service, times(1)).generatePresignedUrl(
                eq(workspaceId), eq(userId), eq("avatar.jpg"), eq("image/jpeg"));
    }

    @Test
    @DisplayName("파일 크기 초과 시 400 에러 반환")
    void generatePresignedUrl_FileTooLarge_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        long fileSizeOver20MB = 21 * 1024 * 1024L; // 21MB

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "large-image.jpg",
                fileSizeOver20MB,
                "image/jpeg"
        );

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // S3Service가 호출되지 않았는지 검증
        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 이미지 타입 - application/pdf - 400 에러 반환")
    void generatePresignedUrl_InvalidImageType_Pdf_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "document.pdf",
                512000L,
                "application/pdf"
        );

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 이미지 타입 - video/mp4 - 400 에러 반환")
    void generatePresignedUrl_InvalidImageType_Video_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "video.mp4",
                512000L,
                "video/mp4"
        );

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 파일 확장자 - .txt - 400 에러 반환")
    void generatePresignedUrl_InvalidFileExtension_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "file.txt",
                512000L,
                "image/jpeg" // Content-Type은 이미지지만 확장자가 다름
        );

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("허용된 모든 이미지 타입 검증 - jpg, jpeg, png, gif, webp")
    void generatePresignedUrl_AllAllowedImageTypes_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String[][] testCases = {
                {"image.jpg", "image/jpeg"},
                {"image.jpeg", "image/jpeg"},
                {"image.png", "image/png"},
                {"image.gif", "image/gif"},
                {"image.webp", "image/webp"}
        };

        for (String[] testCase : testCases) {
            String fileName = testCase[0];
            String contentType = testCase[1];

            PresignedUrlRequest request = new PresignedUrlRequest(
                    workspaceId,
                    fileName,
                    512000L,
                    contentType
            );

            S3Service.PresignedUrlResponse s3Response = new S3Service.PresignedUrlResponse(
                    "https://test-bucket.s3.amazonaws.com/test",
                    "user/test/2024/01/test.jpg",
                    300
            );

            when(s3Service.generatePresignedUrl(any(), any(), any(), any()))
                    .thenReturn(s3Response);

            Principal principal = createMockPrincipal(userId);

            // When & Then
            mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 5개의 이미지 타입 모두 테스트되었는지 검증
        verify(s3Service, times(5)).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("정확히 20MB 파일은 허용됨")
    void generatePresignedUrl_Exactly20MB_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        long exactly20MB = 20 * 1024 * 1024L;

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "large-image.jpg",
                exactly20MB,
                "image/jpeg"
        );

        S3Service.PresignedUrlResponse s3Response = new S3Service.PresignedUrlResponse(
                "https://test-bucket.s3.amazonaws.com/test",
                "user/test/2024/01/test.jpg",
                300
        );

        when(s3Service.generatePresignedUrl(any(), any(), any(), any()))
                .thenReturn(s3Response);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(s3Service, times(1)).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 에러 반환 - workspaceId")
    void generatePresignedUrl_MissingWorkspaceId_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String requestJson = """
                {
                    "fileName": "avatar.jpg",
                    "fileSize": 512000,
                    "contentType": "image/jpeg"
                }
                """;

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 에러 반환 - fileName")
    void generatePresignedUrl_MissingFileName_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s",
                    "fileSize": 512000,
                    "contentType": "image/jpeg"
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 에러 반환 - fileSize")
    void generatePresignedUrl_MissingFileSize_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s",
                    "fileName": "avatar.jpg",
                    "contentType": "image/jpeg"
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 에러 반환 - contentType")
    void generatePresignedUrl_MissingContentType_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s",
                    "fileName": "avatar.jpg",
                    "fileSize": 512000
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("음수 파일 크기는 거부됨")
    void generatePresignedUrl_NegativeFileSize_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s",
                    "fileName": "avatar.jpg",
                    "fileSize": -1000,
                    "contentType": "image/jpeg"
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(s3Service, never()).generatePresignedUrl(any(), any(), any(), any());
    }

    @Test
    @DisplayName("대소문자 구분 없이 Content-Type 검증")
    void generatePresignedUrl_CaseInsensitiveContentType_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        PresignedUrlRequest request = new PresignedUrlRequest(
                workspaceId,
                "avatar.jpg",
                512000L,
                "IMAGE/JPEG" // 대문자
        );

        S3Service.PresignedUrlResponse s3Response = new S3Service.PresignedUrlResponse(
                "https://test-bucket.s3.amazonaws.com/test",
                "user/test/2024/01/test.jpg",
                300
        );

        when(s3Service.generatePresignedUrl(any(), any(), any(), any()))
                .thenReturn(s3Response);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(post("/api/profiles/me/image/presigned-url")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(s3Service, times(1)).generatePresignedUrl(any(), any(), any(), any());
    }

    // ========== Task 16.1: 프로필 이미지 업데이트 엔드포인트 테스트 ==========

    @Test
    @DisplayName("유효한 attachmentId로 프로필 이미지 업데이트 성공")
    void updateProfileImage_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String s3Url = "https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/user/" + workspaceId + "/2024/01/" + userId + "_1704326400.jpg";

        UpdateProfileImageByKeyRequest request = new UpdateProfileImageByKeyRequest(
                workspaceId,
                attachmentId
        );

        UserProfileResponse profileResponse = UserProfileResponse.builder()
                .profileId(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(userId)
                .nickName("테스트 사용자")
                .email("test@example.com")
                .profileImageUrl(s3Url)
                .build();

        when(userProfileService.updateProfile(any(UpdateProfileRequest.class)))
                .thenReturn(profileResponse);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(s3Url))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));

        verify(userProfileService, times(1)).updateProfile(any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("존재하지 않는 attachmentId - 400 에러 반환")
    void updateProfileImage_InvalidAttachmentId_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID invalidAttachmentId = UUID.randomUUID();

        UpdateProfileImageByKeyRequest request = new UpdateProfileImageByKeyRequest(
                workspaceId,
                invalidAttachmentId
        );

        when(userProfileService.updateProfile(any(UpdateProfileRequest.class)))
                .thenThrow(new CustomException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 첨부파일입니다."));

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userProfileService, times(1)).updateProfile(any());
    }

    @Test
    @DisplayName("null attachmentId - 400 에러 반환")
    void updateProfileImage_NullAttachmentId_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s",
                    "attachmentId": null
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        // @NotNull validation에 의해 400 에러 발생
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        // Validation에서 걸리므로 userProfileService는 호출되지 않음
        verify(userProfileService, never()).updateProfile(any());
    }

    @Test
    @DisplayName("프로필 업데이트 실패 - 프로필을 찾을 수 없음 - 404 에러 반환")
    void updateProfileImage_ProfileNotFound_Returns404() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        UpdateProfileImageByKeyRequest request = new UpdateProfileImageByKeyRequest(
                workspaceId,
                attachmentId
        );

        when(userProfileService.updateProfile(any(UpdateProfileRequest.class)))
                .thenThrow(new UserNotFoundException("프로필을 찾을 수 없습니다."));

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(userProfileService, times(1)).updateProfile(any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("필수 필드 누락 - workspaceId - 400 에러 반환")
    void updateProfileImage_MissingWorkspaceId_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "attachmentId": "%s"
                }
                """, attachmentId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(userProfileService, never()).updateProfile(any());
    }

    @Test
    @DisplayName("필수 필드 누락 - attachmentId - 400 에러 반환")
    void updateProfileImage_MissingAttachmentId_Returns400() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String requestJson = String.format("""
                {
                    "workspaceId": "%s"
                }
                """, workspaceId);

        Principal principal = createMockPrincipal(userId);

        // When & Then
        mockMvc.perform(put("/api/profiles/me/image")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(userProfileService, never()).updateProfile(any());
    }

    @Test
    @DisplayName("다양한 유효한 attachmentId로 프로필 업데이트 성공")
    void updateProfileImage_VariousValidAttachmentIds_Success() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        // 여러 개의 attachmentId 테스트
        UUID[] attachmentIds = {
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        };

        for (UUID attachmentId : attachmentIds) {
            String s3Url = "https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/user/" + workspaceId + "/test.jpg";

            UpdateProfileImageByKeyRequest request = new UpdateProfileImageByKeyRequest(
                    workspaceId,
                    attachmentId
            );

            UserProfileResponse profileResponse = UserProfileResponse.builder()
                    .profileId(UUID.randomUUID())
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .nickName("테스트 사용자")
                    .email("test@example.com")
                    .profileImageUrl(s3Url)
                    .build();

            when(userProfileService.updateProfile(any(UpdateProfileRequest.class)))
                    .thenReturn(profileResponse);

            Principal principal = createMockPrincipal(userId);

            // When & Then
            mockMvc.perform(put("/api/profiles/me/image")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileImageUrl").value(s3Url));
        }

        verify(userProfileService, times(attachmentIds.length)).updateProfile(any(UpdateProfileRequest.class));
    }
}
