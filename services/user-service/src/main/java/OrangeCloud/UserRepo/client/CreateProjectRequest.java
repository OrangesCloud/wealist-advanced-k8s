package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a project in Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {
    
    @JsonProperty("workspaceId")
    private UUID workspaceId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
}
