package OrangeCloud.UserRepo.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "워크스페이스 설정 수정 요청 DTO")
public class UpdateWorkspaceSettingsRequest {

    @Size(max = 50, message = "워크스페이스 이름은 50자를 초과할 수 없습니다.")
    @Schema(description = "워크스페이스 이름 (선택적 업데이트)")
    private String workspaceName;

    @Size(max = 500, message = "워크스페이스 설명은 500자를 초과할 수 없습니다.")
    @Schema(description = "워크스페이스 설명 (선택적 업데이트)")
    private String workspaceDescription;

    @Schema(description = "공개 여부 (true: 공개, false: 비공개)")
    private Boolean isPublic;

    @Schema(description = "가입 승인 필요 여부 (true: 승인 필요, false: 자유 가입)")
    private Boolean needApproved; // BE 엔티티 필드명과 일치

    @Schema(description = "OWNER만 초대 가능 여부 (현재 BE 엔티티에 필드 없음 - 추후 확장 대비)")
    private Boolean onlyOwnerCanInvite;
}