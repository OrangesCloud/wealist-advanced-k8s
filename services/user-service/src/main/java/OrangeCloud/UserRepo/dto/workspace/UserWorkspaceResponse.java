package OrangeCloud.UserRepo.dto.workspace;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserWorkspaceResponse {
    private UUID workspaceId;
    private String workspaceName;
    private String workspaceDescription;
    private LocalDateTime createdAt;
    private String role;     // OWNER, MEMBER, PENDING, APPROVED 등
    private boolean isOwner; // 편의상 true면 owner
}
