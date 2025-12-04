package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "프로필 이미지 업데이트 요청 DTO (기존 API용)")
public record UpdateProfileImageRequest(
    String profileImageUrl,
    @Schema(description = "워크스페이스 ID (선택사항)", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID workspaceId

) {}
