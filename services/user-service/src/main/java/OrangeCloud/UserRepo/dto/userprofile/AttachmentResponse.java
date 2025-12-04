package OrangeCloud.UserRepo.dto.userprofile;

import OrangeCloud.UserRepo.entity.Attachment;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 첨부파일 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID attachmentId;
    private String entityType;
    private UUID entityId;
    private String status;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private UUID uploadedBy;
    
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime uploadedAt;
    
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    /**
     * Entity를 DTO로 변환
     */
    public static AttachmentResponse from(Attachment attachment) {
        return AttachmentResponse.builder()
                .attachmentId(attachment.getId())
                .entityType(attachment.getEntityType().name())
                .entityId(attachment.getEntityId())
                .status(attachment.getStatus().name())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .contentType(attachment.getContentType())
                .uploadedBy(attachment.getUploadedBy())
                .uploadedAt(attachment.getCreatedAt())
                .expiresAt(attachment.getExpiresAt())
                .build();
    }
}
