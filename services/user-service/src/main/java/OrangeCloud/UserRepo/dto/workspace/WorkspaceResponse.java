package OrangeCloud.UserRepo.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceResponse {
    private UUID workspaceId;
    private String workspaceName;
    private String workspaceDescription;
    private UUID ownerId;
    private String ownerName;
    private String ownerEmail;
    private Boolean isPublic;
    private Boolean needApproved;
    private LocalDateTime createdAt;
}