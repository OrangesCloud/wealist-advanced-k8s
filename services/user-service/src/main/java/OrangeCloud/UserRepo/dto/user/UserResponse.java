// UserResponse.java
package OrangeCloud.UserRepo.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserResponse {
    private UUID userId;
    private String email;
    private String provider;
    private String googleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;

    // 기본 생성자
    public UserResponse() {}

    // 생성자
    public UserResponse(UUID userId, String email, String provider, String googleId,
                       LocalDateTime createdAt, LocalDateTime updatedAt, Boolean isActive) {
        this.userId = userId;
        this.email = email;
        this.provider = provider;
        this.googleId = googleId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isActive = isActive;
    }

    // Getter 메서드들
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getProvider() { return provider; }
    public String getGoogleId() { return googleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Boolean getIsActive() { return isActive; }

    // Setter 메서드들
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}