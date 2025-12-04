package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for comment from Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    
    @JsonProperty("data")
    private CommentData data;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentData {
        @JsonProperty("id")
        private UUID id;
        
        @JsonProperty("board_id")
        private UUID boardId;
        
        @JsonProperty("user_id")
        private UUID userId;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
        
        @JsonProperty("updatedAt")
        private LocalDateTime updatedAt;
    }
}
