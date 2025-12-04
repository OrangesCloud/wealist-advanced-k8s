package OrangeCloud.UserRepo.dto.userprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "사용자 프로필 생성 요청 DTO")
public record CreateProfileRequest(
    @Schema(description = "워크스페이스 ID", required = true)
    @NotNull
    UUID workspaceId,

    @Schema(description = "사용자 닉네임", example = "코딩왕", required = true)
    @NotBlank
    @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하로 입력해야 합니다.")
    String nickName,

    @Schema(description = "사용자 이메일", example = "user@example.com", required = false)
    String email
) {}
