package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import OrangeCloud.UserRepo.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AttachmentService 단위 테스트
 * Requirements: 7.1, 7.2, 7.4, 7.5
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private AttachmentService attachmentService;

    private UUID userId;
    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fileKey = "user/workspace/2024/01/user_123456789.jpg";
        fileName = "profile.jpg";
        fileSize = 1024000L;
        contentType = "image/jpeg";
    }

    @Test
    @DisplayName("임시 첨부파일 생성 성공")
    void createTempAttachment_Success() {
        // Given
        String s3Url = "https://bucket.s3.region.amazonaws.com/" + fileKey;
        when(s3Service.generateS3Url(fileKey)).thenReturn(s3Url);

        Attachment savedAttachment = Attachment.builder()
                .id(UUID.randomUUID())
                .entityType(Attachment.EntityType.USER_PROFILE)
                .fileName(fileName)
                .fileUrl(s3Url)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(userId)
                .status(Attachment.AttachmentStatus.TEMP)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(attachmentRepository.save(any(Attachment.class))).thenReturn(savedAttachment);

        // When
        Attachment result = attachmentService.createTempAttachment(
                Attachment.EntityType.USER_PROFILE,
                fileName,
                fileKey,
                fileSize,
                contentType,
                userId
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Attachment.AttachmentStatus.TEMP);
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getFileName()).isEqualTo(fileName);
        assertThat(result.getFileUrl()).isEqualTo(s3Url);

        verify(s3Service, times(1)).generateS3Url(fileKey);
        verify(attachmentRepository, times(1)).save(any(Attachment.class));
    }

    @Test
    @DisplayName("첨부파일 확정 성공")
    void confirmAttachment_Success() {
        // Given
        UUID attachmentId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        Attachment tempAttachment = Attachment.builder()
                .id(attachmentId)
                .entityType(Attachment.EntityType.USER_PROFILE)
                .status(Attachment.AttachmentStatus.TEMP)
                .fileName(fileName)
                .fileUrl("https://bucket.s3.region.amazonaws.com/" + fileKey)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(userId)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId))
                .thenReturn(Optional.of(tempAttachment));
        when(attachmentRepository.save(any(Attachment.class))).thenReturn(tempAttachment);

        // When
        attachmentService.confirmAttachment(attachmentId, entityId);

        // Then
        verify(attachmentRepository, times(1)).findByIdAndDeletedAtIsNull(attachmentId);
        verify(attachmentRepository, times(1)).save(any(Attachment.class));
    }

    @Test
    @DisplayName("존재하지 않는 첨부파일 확정 시 예외 발생")
    void confirmAttachment_NotFound_ThrowsException() {
        // Given
        UUID attachmentId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        when(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> attachmentService.confirmAttachment(attachmentId, entityId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND);

        verify(attachmentRepository, times(1)).findByIdAndDeletedAtIsNull(attachmentId);
        verify(attachmentRepository, never()).save(any(Attachment.class));
    }

    @Test
    @DisplayName("이미 확정된 첨부파일 재확정 시 예외 발생")
    void confirmAttachment_AlreadyConfirmed_ThrowsException() {
        // Given
        UUID attachmentId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        Attachment confirmedAttachment = Attachment.builder()
                .id(attachmentId)
                .entityType(Attachment.EntityType.USER_PROFILE)
                .entityId(UUID.randomUUID())
                .status(Attachment.AttachmentStatus.CONFIRMED)
                .fileName(fileName)
                .fileUrl("https://bucket.s3.region.amazonaws.com/" + fileKey)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(userId)
                .build();

        when(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId))
                .thenReturn(Optional.of(confirmedAttachment));

        // When & Then
        assertThatThrownBy(() -> attachmentService.confirmAttachment(attachmentId, entityId))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 확정된 첨부파일입니다")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ATTACHMENT_STATUS);

        verify(attachmentRepository, times(1)).findByIdAndDeletedAtIsNull(attachmentId);
        verify(attachmentRepository, never()).save(any(Attachment.class));
    }

    @Test
    @DisplayName("첨부파일 삭제 성공")
    void deleteAttachment_Success() {
        // Given
        UUID attachmentId = UUID.randomUUID();
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + fileKey;

        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .entityType(Attachment.EntityType.USER_PROFILE)
                .status(Attachment.AttachmentStatus.TEMP)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(userId)
                .build();

        when(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId))
                .thenReturn(Optional.of(attachment));
        doNothing().when(s3Service).deleteFile(fileKey);
        when(attachmentRepository.save(any(Attachment.class))).thenReturn(attachment);

        // When
        attachmentService.deleteAttachment(attachmentId);

        // Then
        verify(attachmentRepository, times(1)).findByIdAndDeletedAtIsNull(attachmentId);
        verify(s3Service, times(1)).deleteFile(fileKey);
        verify(attachmentRepository, times(1)).save(any(Attachment.class));
    }

    @Test
    @DisplayName("존재하지 않는 첨부파일 삭제 시 예외 발생")
    void deleteAttachment_NotFound_ThrowsException() {
        // Given
        UUID attachmentId = UUID.randomUUID();

        when(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> attachmentService.deleteAttachment(attachmentId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND);

        verify(attachmentRepository, times(1)).findByIdAndDeletedAtIsNull(attachmentId);
        verify(s3Service, never()).deleteFile(anyString());
        verify(attachmentRepository, never()).save(any(Attachment.class));
    }
}
