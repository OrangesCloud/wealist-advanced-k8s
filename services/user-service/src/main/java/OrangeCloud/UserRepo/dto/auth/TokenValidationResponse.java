// OrangeCloud.UserRepo.dto.TokenValidationResponse (신규 DTO 파일)
package OrangeCloud.UserRepo.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenValidationResponse {
    private String userId; // User ID (UUID string)
    private boolean valid; // Validation result
    private String message;
}