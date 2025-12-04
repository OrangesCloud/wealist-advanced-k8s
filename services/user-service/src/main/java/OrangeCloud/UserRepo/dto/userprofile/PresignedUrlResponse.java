package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Presigned URL 응답 DTO
 */
@Schema(description = "Presigned URL 응답")
public record PresignedUrlResponse(
        @Schema(description = "업로드 URL", example = "https://wealist-dev-files.s3.ap-northeast-2.amazonaws.com/user/...")
        String uploadUrl,

        @Schema(description = "파일 키", example = "user/abc123/2024/01/880e8400-e29b-41d4-a716-446655440003_1704326400.jpg")
        String fileKey,

        @Schema(description = "만료 시간 (초)", example = "300")
        int expiresIn
) {
}
