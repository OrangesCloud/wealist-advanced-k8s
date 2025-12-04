package OrangeCloud.UserRepo.dto.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceValidationResponse {
    private UUID workspaceId;
    private UUID userId;

    @JsonProperty("isValid")
    private boolean isValid;
}
