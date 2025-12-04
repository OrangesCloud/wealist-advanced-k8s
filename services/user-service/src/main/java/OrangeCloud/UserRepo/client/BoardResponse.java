package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for board from Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardResponse {
    
    @JsonProperty("data")
    private BoardData data;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardData {
        @JsonProperty("id")
        private UUID id;
        
        @JsonProperty("project_id")
        private UUID projectId;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
        
        @JsonProperty("updatedAt")
        private LocalDateTime updatedAt;
    }
}
