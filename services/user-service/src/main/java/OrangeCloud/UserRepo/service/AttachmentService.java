package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import OrangeCloud.UserRepo.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Attachment 서비스
 * 첨부파일 관리 로직을 처리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final S3Service s3Service;

    /**
     * 임시 첨부파일 생성
     * 
     * @param entityType  엔티티 타입
     * @param fileName    파일명
     * @param fileKey     S3 파일 키
     * @param fileSize    파일 크기
     * @param contentType Content-Type
     * @param uploadedBy  업로드한 사용자 ID
     * @return 생성된 첨부파일
     */
    @Transactional
    public Attachment createTempAttachment(
            Attachment.EntityType entityType,
            String fileName,
            String fileKey,
            Long fileSize,
            String contentType,
            UUID uploadedBy) {

        // S3 URL 생성
        String fileUrl = s3Service.generateS3Url(fileKey);

        // 만료 시간 설정 (1시간 후)
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 임시 첨부파일 생성
        Attachment attachment = Attachment.createTemp(
                entityType,
                fileName,
                fileUrl,
                fileSize,
                contentType,
                uploadedBy,
                expiresAt
        );

        Attachment saved = attachmentRepository.save(attachment);
        log.info("임시 첨부파일 생성 완료 - id: {}, fileName: {}, expiresAt: {}", 
                saved.getId(), saved.getFileName(), saved.getExpiresAt());

        return saved;
    }

    /**
     * 첨부파일 확정 (프로필 업데이트 시 호출)
     * 
     * @param attachmentId 첨부파일 ID
     * @param entityId     연결할 엔티티 ID
     * @return 확정된 첨부파일
     */
    @Transactional
    public Attachment confirmAttachment(UUID attachmentId, UUID entityId) {
        Attachment attachment = attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTACHMENT_NOT_FOUND));

        if (attachment.getStatus() != Attachment.AttachmentStatus.TEMP) {
            throw new CustomException(ErrorCode.INVALID_ATTACHMENT_STATUS,
                    "이미 확정된 첨부파일입니다.");
        }

        attachment.confirm(entityId);
        Attachment confirmed = attachmentRepository.save(attachment);

        log.info("첨부파일 확정 완료 - attachmentId: {}, entityId: {}", attachmentId, entityId);
        
        return confirmed;
    }

    /**
     * 만료된 임시 첨부파일 조회
     */
    @Transactional(readOnly = true)
    public List<Attachment> findExpiredTempAttachments() {
        return attachmentRepository.findExpiredTempAttachments(LocalDateTime.now());
    }

    /**
     * 첨부파일 삭제 (S3 파일 + DB 레코드)
     * 
     * @param attachmentId 첨부파일 ID
     */
    @Transactional
    public void deleteAttachment(UUID attachmentId) {
        Attachment attachment = attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTACHMENT_NOT_FOUND));

        // S3 파일 삭제
        try {
            String fileKey = extractFileKeyFromUrl(attachment.getFileUrl());
            s3Service.deleteFile(fileKey);
            log.info("S3 파일 삭제 완료 - fileKey: {}", fileKey);
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패 - fileUrl: {}, error: {}", 
                    attachment.getFileUrl(), e.getMessage());
            // S3 삭제 실패해도 DB는 삭제 진행
        }

        // DB 레코드 삭제 (soft delete)
        attachment.softDelete();
        attachmentRepository.save(attachment);

        log.info("첨부파일 삭제 완료 - attachmentId: {}", attachmentId);
    }

    /**
     * 여러 첨부파일 일괄 삭제
     * 
     * @param attachments 삭제할 첨부파일 목록
     */
    @Transactional
    public void deleteBatch(List<Attachment> attachments) {
        if (attachments.isEmpty()) {
            return;
        }

        // S3 파일 삭제
        for (Attachment attachment : attachments) {
            try {
                String fileKey = extractFileKeyFromUrl(attachment.getFileUrl());
                s3Service.deleteFile(fileKey);
                log.debug("S3 파일 삭제 완료 - fileKey: {}", fileKey);
            } catch (Exception e) {
                log.error("S3 파일 삭제 실패 - fileUrl: {}, error: {}", 
                        attachment.getFileUrl(), e.getMessage());
                // 계속 진행
            }
        }

        // DB 레코드 삭제 (hard delete)
        List<UUID> attachmentIds = attachments.stream()
                .map(Attachment::getId)
                .toList();
        
        int deletedCount = attachmentRepository.deleteBatch(attachmentIds);
        log.info("첨부파일 일괄 삭제 완료 - count: {}", deletedCount);
    }

    /**
     * S3 URL에서 파일 키 추출
     */
    private String extractFileKeyFromUrl(String fileUrl) {
        // URL 형식: https://bucket.s3.region.amazonaws.com/user/workspace/year/month/file.ext
        // 또는: https://s3.region.amazonaws.com/bucket/user/workspace/year/month/file.ext
        
        if (fileUrl.contains(".amazonaws.com/")) {
            String[] parts = fileUrl.split(".amazonaws.com/", 2);
            if (parts.length == 2) {
                return parts[1].split("\\?")[0];  // 쿼리 파라미터 제거
            }
        }
        
        throw new IllegalArgumentException("Invalid S3 URL format: " + fileUrl);
    }
}
