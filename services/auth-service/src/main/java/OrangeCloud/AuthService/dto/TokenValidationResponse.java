package OrangeCloud.AuthService.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TokenValidationResponse {
    private String userId;
    private boolean valid;
    private String message;
}
