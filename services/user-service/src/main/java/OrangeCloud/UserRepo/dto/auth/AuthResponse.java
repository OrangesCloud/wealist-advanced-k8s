// AuthResponse.java
package OrangeCloud.UserRepo.dto.auth;

import java.util.UUID;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String name;
    private String email;
    private String tokenType = "Bearer";

    // 기본 생성자
    public AuthResponse() {}

    // 생성자들
    public AuthResponse(String accessToken, String refreshToken, UUID userId, String name, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.tokenType = "Bearer";
    }

    // 기존 생성자와의 호환성을 위한 생성자 (refreshToken 없이)
    public AuthResponse(String accessToken, UUID userId, String name, String email) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.tokenType = "Bearer";
    }

    // Getter 메서드들 (Jackson 직렬화를 위해 필수)
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getTokenType() { return tokenType; }

    // Setter 메서드들
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    @Override
    public String toString() {
        return "AuthResponse{" +
                "accessToken='" + accessToken + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", userId=" + userId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", tokenType='" + tokenType + '\'' +
                '}';
    }
}