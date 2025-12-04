package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.config.S3Config;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3Service 단위 테스트
 * Requirements: 2.3, 2.4
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    @Mock
    private S3Config s3Config;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Presigner, s3Client, s3Config);
        lenient().when(s3Config.getBucket()).thenReturn("wealist-dev-files");
        lenient().when(s3Config.getRegion()).thenReturn("ap-northeast-2");
    }

    @Test
    @DisplayName("유효한 파라미터로 Presigned URL 생성 성공")
    void generatePresignedUrl_Success() throws Exception {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";
        String contentType = "image/jpeg";

        // Mock PresignedPutObjectRequest
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/user/workspace/2024/01/user_123456789.jpg?signature=xyz");
        when(mockPresignedRequest.url()).thenReturn(mockUrl);

        // Mock S3Presigner
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        S3Service.PresignedUrlResponse response = s3Service.generatePresignedUrl(
                workspaceId, userId, fileName, contentType);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUploadUrl()).isNotEmpty();
        assertThat(response.getFileKey()).isNotEmpty();
        assertThat(response.getExpiresIn()).isEqualTo(300); // 5분 = 300초

        // fileKey 형식 검증: user/{workspaceId}/{year}/{month}/{userId}_{timestamp}.ext
        assertThat(response.getFileKey()).startsWith("user/" + workspaceId);
        assertThat(response.getFileKey()).endsWith(".jpg");
        assertThat(response.getFileKey()).contains(userId.toString());

        // S3Presigner 호출 검증
        verify(s3Presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("workspaceId가 null인 경우 예외 발생")
    void generatePresignedUrl_NullWorkspaceId_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";
        String contentType = "image/jpeg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(null, userId, fileName, contentType))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("워크스페이스 ID는 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        // S3Presigner가 호출되지 않았는지 검증
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("userId가 null인 경우 예외 발생")
    void generatePresignedUrl_NullUserId_ThrowsException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        String fileName = "profile.jpg";
        String contentType = "image/jpeg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, null, fileName, contentType))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("사용자 ID는 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("fileName이 null인 경우 예외 발생")
    void generatePresignedUrl_NullFileName_ThrowsException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String contentType = "image/jpeg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, userId, null, contentType))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("파일명은 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("fileName이 빈 문자열인 경우 예외 발생")
    void generatePresignedUrl_EmptyFileName_ThrowsException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String contentType = "image/jpeg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, userId, "  ", contentType))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("파일명은 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("contentType이 null인 경우 예외 발생")
    void generatePresignedUrl_NullContentType_ThrowsException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, userId, fileName, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Content-Type은 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("contentType이 빈 문자열인 경우 예외 발생")
    void generatePresignedUrl_EmptyContentType_ThrowsException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, userId, fileName, "  "))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Content-Type은 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("S3Presigner 예외 발생 시 CustomException으로 래핑")
    void generatePresignedUrl_S3PresignerException_ThrowsCustomException() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";
        String contentType = "image/jpeg";

        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 connection failed"));

        // When & Then
        assertThatThrownBy(() -> s3Service.generatePresignedUrl(workspaceId, userId, fileName, contentType))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Presigned URL 생성에 실패했습니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.S3_UPLOAD_FAILED);

        verify(s3Presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("다양한 파일 확장자로 fileKey 생성 검증")
    void generatePresignedUrl_VariousFileExtensions() throws Exception {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] fileNames = {"image.png", "photo.jpeg", "avatar.gif", "profile.webp"};

        for (String fileName : fileNames) {
            // Mock setup
            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test");
            when(mockPresignedRequest.url()).thenReturn(mockUrl);
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                    .thenReturn(mockPresignedRequest);

            // When
            S3Service.PresignedUrlResponse response = s3Service.generatePresignedUrl(
                    workspaceId, userId, fileName, "image/jpeg");

            // Then
            String extension = fileName.substring(fileName.lastIndexOf('.'));
            assertThat(response.getFileKey()).endsWith(extension);
        }
    }

    @Test
    @DisplayName("URL 만료 시간이 5분(300초)으로 설정됨")
    void generatePresignedUrl_ExpirationTime() throws Exception {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileName = "profile.jpg";
        String contentType = "image/jpeg";

        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test");
        when(mockPresignedRequest.url()).thenReturn(mockUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        S3Service.PresignedUrlResponse response = s3Service.generatePresignedUrl(
                workspaceId, userId, fileName, contentType);

        // Then
        assertThat(response.getExpiresIn()).isEqualTo(300); // 5분 = 300초
    }

    // ========== generateS3Url 메서드 테스트 ==========

    @Test
    @DisplayName("유효한 fileKey로 S3 URL 생성 성공")
    void generateS3Url_Success() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileKey = "user/" + workspaceId + "/2024/01/" + userId + "_1704326400.jpg";

        // When
        String s3Url = s3Service.generateS3Url(fileKey);

        // Then
        assertThat(s3Url).isNotNull();
        assertThat(s3Url).startsWith("https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/");
        assertThat(s3Url).endsWith(fileKey);
        assertThat(s3Url).isEqualTo("https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/" + fileKey);
    }

    @Test
    @DisplayName("fileKey가 null인 경우 예외 발생")
    void generateS3Url_NullFileKey_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> s3Service.generateS3Url(null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("파일 키는 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("fileKey가 빈 문자열인 경우 예외 발생")
    void generateS3Url_EmptyFileKey_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> s3Service.generateS3Url("  "))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("파일 키는 필수입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("잘못된 fileKey 형식 - board/로 시작 - 예외 발생")
    void generateS3Url_InvalidFormat_Board_ThrowsException() {
        // Given
        String invalidFileKey = "board/boards/abc123/2024/01/test.jpg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generateS3Url(invalidFileKey))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("잘못된 파일 키 형식입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("잘못된 fileKey 형식 - 임의의 문자열 - 예외 발생")
    void generateS3Url_InvalidFormat_Random_ThrowsException() {
        // Given
        String invalidFileKey = "random/path/file.jpg";

        // When & Then
        assertThatThrownBy(() -> s3Service.generateS3Url(invalidFileKey))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("잘못된 파일 키 형식입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("다양한 유효한 fileKey 형식으로 S3 URL 생성")
    void generateS3Url_VariousValidFormats() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] validFileKeys = {
                "user/" + workspaceId + "/2024/01/" + userId + "_1704326400.jpg",
                "user/" + workspaceId + "/2024/12/" + userId + "_1704326400.png",
                "user/" + workspaceId + "/2025/06/" + userId + "_1704326400.gif",
                "user/" + workspaceId + "/2023/03/" + userId + "_1704326400.webp"
        };

        for (String fileKey : validFileKeys) {
            // When
            String s3Url = s3Service.generateS3Url(fileKey);

            // Then
            assertThat(s3Url).isNotNull();
            assertThat(s3Url).startsWith("https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/user/");
            assertThat(s3Url).endsWith(fileKey);
        }
    }

    @Test
    @DisplayName("fileKey에 특수 문자가 포함된 경우도 URL 생성")
    void generateS3Url_SpecialCharactersInFileKey() {
        // Given
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fileKey = "user/" + workspaceId + "/2024/01/" + userId + "_1704326400-test_file.jpg";

        // When
        String s3Url = s3Service.generateS3Url(fileKey);

        // Then
        assertThat(s3Url).isNotNull();
        assertThat(s3Url).contains(fileKey);
    }
}
