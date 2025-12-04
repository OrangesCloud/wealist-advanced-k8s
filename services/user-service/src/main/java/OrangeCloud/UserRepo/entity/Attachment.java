package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Attachment 엔티티
 * 프로필 이미지 등의 파일 첨부를 관리합니다.
 * 임시 파일(TEMP)과 확정 파일(CONFIRMED) 상태를 구분하여 관리합니다.
 */
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachments_entity", columnList = "entityType, entityId"),
        @Index(name = "idx_attachments_entity_id", columnList = "entityId"),
        @Index(name = "idx_attachments_status", columnList = "status"),
        @Index(name = "idx_attachments_uploaded_by", columnList = "uploadedBy"),
        @Index(name = "idx_attachments_expires_at", columnList = "expiresAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entityType", length = 50, nullable = false)
    private EntityType entityType;

    @Column(name = "entityId", columnDefinition = "UUID")
    private UUID entityId;  // Nullable - 임시 파일은 아직 entity에 연결되지 않음

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AttachmentStatus status = AttachmentStatus.TEMP;

    @Column(name = "fileName", length = 255, nullable = false)
    private String fileName;

    @Column(name = "fileUrl", columnDefinition = "TEXT", nullable = false)
    private String fileUrl;

    @Column(name = "fileSize", nullable = false)
    private Long fileSize;

    @Column(name = "contentType", length = 100, nullable = false)
    private String contentType;

    @Column(name = "uploadedBy", columnDefinition = "UUID", nullable = false)
    private UUID uploadedBy;

    @Column(name = "expiresAt")
    private LocalDateTime expiresAt;  // 임시 파일의 만료 시간

    @CreationTimestamp
    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;  // Soft delete

    /**
     * EntityType enum
     */
    public enum EntityType {
        USER_PROFILE
    }

    /**
     * AttachmentStatus enum
     */
    public enum AttachmentStatus {
        TEMP,       // 임시 상태
        CONFIRMED   // 확정 상태
    }

    /**
     * 임시 첨부파일 생성
     */
    public static Attachment createTemp(
            EntityType entityType,
            String fileName,
            String fileUrl,
            Long fileSize,
            String contentType,
            UUID uploadedBy,
            LocalDateTime expiresAt) {
        return Attachment.builder()
                .entityType(entityType)
                .entityId(null)  // 임시 파일은 entity에 연결되지 않음
                .status(AttachmentStatus.TEMP)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(uploadedBy)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 첨부파일 확정 (TEMP -> CONFIRMED)
     */
    public void confirm(UUID entityId) {
        this.entityId = entityId;
        this.status = AttachmentStatus.CONFIRMED;
        this.expiresAt = null;  // 확정되면 만료 시간 제거
    }

    /**
     * Soft delete
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return status == AttachmentStatus.TEMP 
                && expiresAt != null 
                && LocalDateTime.now().isAfter(expiresAt);
    }
}
