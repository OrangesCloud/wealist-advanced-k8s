package OrangeCloud.UserRepo.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "워크스페이스 설정 조회 응답 DTO")
public class WorkspaceSettingsResponse {

    @Schema(description = "워크스페이스 ID")
    private UUID workspaceId;

    @Schema(description = "워크스페이스 이름")
    private String workspaceName;

    @Schema(description = "워크스페이스 설명")
    private String workspaceDescription;

    @Schema(description = "공개 여부 (true: 공개, false: 비공개)")
    private Boolean isPublic;

    @Schema(description = "가입 승인 필요 여부 (true: 승인 필요, false: 자유 가입)", example = "true")
    private Boolean requiresApproval; // BE의 needApproved에 매핑

    @Schema(description = "OWNER만 초대 가능 여부 (현재 BE 엔티티에 필드 없음 - 추후 확장 대비)", example = "false")
    private Boolean onlyOwnerCanInvite; 
}