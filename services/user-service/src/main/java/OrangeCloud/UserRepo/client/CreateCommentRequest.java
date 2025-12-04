package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a comment in Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    
    @JsonProperty("boardId")
    private UUID boardId;
    
    @JsonProperty("content")
    private String content;
}
