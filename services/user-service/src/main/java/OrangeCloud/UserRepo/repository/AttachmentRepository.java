package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Attachment 엔티티에 대한 데이터 접근 계층 (DAO)
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Entity ID로 첨부파일 조회
     */
    List<Attachment> findByEntityTypeAndEntityIdAndDeletedAtIsNull(
            Attachment.EntityType entityType, 
            UUID entityId);

    /**
     * 만료된 임시 첨부파일 조회
     * status가 TEMP이고 expiresAt이 현재 시간보다 이전인 파일들
     */
    @Query("SELECT a FROM Attachment a WHERE a.status = 'TEMP' " +
           "AND a.expiresAt < :now AND a.deletedAt IS NULL")
    List<Attachment> findExpiredTempAttachments(@Param("now") LocalDateTime now);

    /**
     * ID로 첨부파일 조회 (삭제되지 않은 것만)
     */
    Optional<Attachment> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * 여러 ID로 첨부파일 조회 (삭제되지 않은 것만)
     */
    List<Attachment> findByIdInAndDeletedAtIsNull(List<UUID> ids);

    /**
     * 첨부파일 확정 (TEMP -> CONFIRMED)
     */
    @Modifying
    @Query("UPDATE Attachment a SET a.status = 'CONFIRMED', a.entityId = :entityId, " +
           "a.expiresAt = NULL WHERE a.id IN :attachmentIds")
    int confirmAttachments(@Param("attachmentIds") List<UUID> attachmentIds, 
                          @Param("entityId") UUID entityId);

    /**
     * 여러 첨부파일 일괄 삭제 (soft delete)
     */
    @Modifying
    @Query("UPDATE Attachment a SET a.deletedAt = :deletedAt WHERE a.id IN :attachmentIds")
    int softDeleteBatch(@Param("attachmentIds") List<UUID> attachmentIds, 
                       @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 여러 첨부파일 일괄 삭제 (hard delete)
     */
    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.id IN :attachmentIds")
    int deleteBatch(@Param("attachmentIds") List<UUID> attachmentIds);
}
