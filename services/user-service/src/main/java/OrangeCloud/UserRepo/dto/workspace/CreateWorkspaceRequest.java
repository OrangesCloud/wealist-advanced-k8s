package OrangeCloud.UserRepo.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkspaceRequest {
    @NotBlank(message = "워크스페이스 이름은 필수입니다.")
    private String workspaceName;
    private String workspaceDescription;
    private Boolean isPublic = true; // 검색 > 초대 가능성
}