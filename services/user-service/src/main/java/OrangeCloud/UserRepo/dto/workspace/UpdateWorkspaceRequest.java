package OrangeCloud.UserRepo.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkspaceRequest {
    private String workspaceName;
    private String workspaceDescription;
}