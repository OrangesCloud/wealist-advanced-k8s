package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "사용자 프로필 업데이트 요청 DTO")
public record UpdateProfileRequest(
        @Schema(description = "워크스페이스 ID (선택사항, null이면 기본 프로필 업데이트)",
                example = "123e4567-e89b-12d3-a456-426614174000")
    @NotNull
    UUID workspaceId,
    @Schema(description = "워크스페이스 ID", required = true)
    @NotNull
    UUID userId,

    @Schema(description = "업데이트할 사용자 닉네임", example = "코딩왕", required = false)
    @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하로 입력해야 합니다.")
    String nickName,

    @Schema(description = "업데이트할 이메일", example = "user@example.com", required = false)
    String email,

    @Schema(description = "업데이트할 프로필 이미지 URL", example = "https://new.image.url/avatar.jpg", required = false)
    String profileImageUrl,

    @Schema(description = "프로필 이미지 첨부파일 ID (임시 첨부파일을 확정할 때 사용)", example = "123e4567-e89b-12d3-a456-426614174000", required = false)
    UUID attachmentId

) {}

