package OrangeCloud.UserRepo.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating a board in Board-Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoardRequest {
    
    @JsonProperty("projectId")
    private UUID projectId;
    
    @JsonProperty("assigneeId")
    private UUID assigneeId;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("customFields")
    private Map<String, Object> customFields;
    
    @JsonProperty("startDate")
    private LocalDateTime startDate;
    
    @JsonProperty("dueDate")
    private LocalDateTime dueDate;
}
