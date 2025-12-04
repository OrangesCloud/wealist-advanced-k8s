package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Presigned URL 요청 DTO
 */
@Schema(description = "Presigned URL 요청")
public record PresignedUrlRequest(
        @NotNull(message = "워크스페이스 ID는 필수입니다.")
        @Schema(description = "워크스페이스 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID workspaceId,

        @NotBlank(message = "파일명은 필수입니다.")
        @Schema(description = "파일명", example = "avatar.jpg")
        String fileName,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 양수여야 합니다.")
        @Schema(description = "파일 크기 (bytes)", example = "512000")
        Long fileSize,

        @NotBlank(message = "Content-Type은 필수입니다.")
        @Schema(description = "Content-Type", example = "image/jpeg")
        String contentType
) {
}
