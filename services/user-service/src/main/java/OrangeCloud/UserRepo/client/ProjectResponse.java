package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for project from Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    
    @JsonProperty("data")
    private ProjectData data;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectData {
        @JsonProperty("id")
        private UUID id;
        
        @JsonProperty("workspace_id")
        private UUID workspaceId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("owner_id")
        private UUID ownerId;
        
        @JsonProperty("ownerName")
        private String ownerName;
        
        @JsonProperty("ownerEmail")
        private String ownerEmail;
        
        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
        
        @JsonProperty("updatedAt")
        private LocalDateTime updatedAt;
    }
}
