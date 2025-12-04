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
public class WorkspaceMemberResponse {
    private UUID id;
    private UUID workspaceId;
    private UUID userId;
    private String profileImageUrl;
    private String userName;
    private String userEmail;
    private String roleName;
    private Boolean isDefault;
    private LocalDateTime joinedAt;
}