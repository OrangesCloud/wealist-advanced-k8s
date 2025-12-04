package OrangeCloud.UserRepo.job;

import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * AttachmentCleanupJob 단위 테스트
 * Requirements: 7.4, 7.5, 7.6
 */
@ExtendWith(MockitoExtension.class)
class AttachmentCleanupJobTest {

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private AttachmentCleanupJob cleanupJob;

    @Test
    @DisplayName("만료된 첨부파일 정리 성공")
    void cleanupExpiredAttachments_Success() {
        // Given
        Attachment expiredAttachment1 = Attachment.builder()
                .id(UUID.randomUUID())
                .entityType(Attachment.EntityType.USER_PROFILE)
                .status(Attachment.AttachmentStatus.TEMP)
                .fileName("file1.jpg")
                .fileUrl("https://bucket.s3.region.amazonaws.com/user/workspace/2024/01/file1.jpg")
                .fileSize(1024000L)
                .contentType("image/jpeg")
                .uploadedBy(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusHours(2))
                .build();

        Attachment expiredAttachment2 = Attachment.builder()
                .id(UUID.randomUUID())
                .entityType(Attachment.EntityType.USER_PROFILE)
                .status(Attachment.AttachmentStatus.TEMP)
                .fileName("file2.jpg")
                .fileUrl("https://bucket.s3.region.amazonaws.com/user/workspace/2024/01/file2.jpg")
                .fileSize(2048000L)
                .contentType("image/jpeg")
                .uploadedBy(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        List<Attachment> expiredAttachments = Arrays.asList(expiredAttachment1, expiredAttachment2);

        when(attachmentService.findExpiredTempAttachments()).thenReturn(expiredAttachments);
        doNothing().when(attachmentService).deleteBatch(expiredAttachments);

        // When
        cleanupJob.cleanupExpiredAttachments();

        // Then
        verify(attachmentService, times(1)).findExpiredTempAttachments();
        verify(attachmentService, times(1)).deleteBatch(expiredAttachments);
    }

    @Test
    @DisplayName("만료된 첨부파일이 없는 경우")
    void cleanupExpiredAttachments_NoExpiredFiles() {
        // Given
        when(attachmentService.findExpiredTempAttachments()).thenReturn(Collections.emptyList());

        // When
        cleanupJob.cleanupExpiredAttachments();

        // Then
        verify(attachmentService, times(1)).findExpiredTempAttachments();
        verify(attachmentService, never()).deleteBatch(anyList());
    }

    @Test
    @DisplayName("정리 작업 중 예외 발생 시 로그만 남기고 계속 진행")
    void cleanupExpiredAttachments_ExceptionHandling() {
        // Given
        when(attachmentService.findExpiredTempAttachments())
                .thenThrow(new RuntimeException("Database connection failed"));

        // When
        cleanupJob.cleanupExpiredAttachments();

        // Then
        verify(attachmentService, times(1)).findExpiredTempAttachments();
        verify(attachmentService, never()).deleteBatch(anyList());
        // 예외가 발생해도 메서드가 정상적으로 종료되어야 함 (예외를 던지지 않음)
    }

    @Test
    @DisplayName("삭제 작업 중 예외 발생 시 로그만 남기고 계속 진행")
    void cleanupExpiredAttachments_DeleteException() {
        // Given
        Attachment expiredAttachment = Attachment.builder()
                .id(UUID.randomUUID())
                .entityType(Attachment.EntityType.USER_PROFILE)
                .status(Attachment.AttachmentStatus.TEMP)
                .fileName("file.jpg")
                .fileUrl("https://bucket.s3.region.amazonaws.com/user/workspace/2024/01/file.jpg")
                .fileSize(1024000L)
                .contentType("image/jpeg")
                .uploadedBy(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        List<Attachment> expiredAttachments = Collections.singletonList(expiredAttachment);

        when(attachmentService.findExpiredTempAttachments()).thenReturn(expiredAttachments);
        doThrow(new RuntimeException("S3 delete failed")).when(attachmentService).deleteBatch(expiredAttachments);

        // When
        cleanupJob.cleanupExpiredAttachments();

        // Then
        verify(attachmentService, times(1)).findExpiredTempAttachments();
        verify(attachmentService, times(1)).deleteBatch(expiredAttachments);
        // 예외가 발생해도 메서드가 정상적으로 종료되어야 함
    }
}
