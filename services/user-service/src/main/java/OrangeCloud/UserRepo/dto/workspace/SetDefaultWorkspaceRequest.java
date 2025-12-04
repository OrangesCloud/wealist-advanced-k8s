package OrangeCloud.UserRepo.dto.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SetDefaultWorkspaceRequest {
    @NotNull(message = "워크스페이스 ID는 필수입니다.")
    private UUID workspaceId;
}