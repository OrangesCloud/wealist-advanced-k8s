package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 프로필 이미지 업데이트 요청 DTO (Attachment 기반)
 * 임시 첨부파일의 attachmentId를 받아 프로필 이미지를 업데이트합니다.
 */
@Schema(description = "프로필 이미지 업데이트 요청 DTO (attachmentId 기반)")
public record UpdateProfileImageByKeyRequest(
        @Schema(description = "워크스페이스 ID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull(message = "워크스페이스 ID는 필수입니다.")
        UUID workspaceId,

        @Schema(description = "첨부파일 ID (임시 첨부파일을 확정할 때 사용)", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull(message = "첨부파일 ID는 필수입니다.")
        UUID attachmentId
) {
}
