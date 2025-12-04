// RefreshTokenRequest.java
package OrangeCloud.UserRepo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token은 필수입니다.")

    private String refreshToken;
    // 기본 생성자
    public RefreshTokenRequest() {}

    // 매개변수가 있는 생성자
    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // Getter
    public String getRefreshToken() {
        return refreshToken;
    }

    // Setter
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}