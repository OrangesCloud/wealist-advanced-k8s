package OrangeCloud.UserRepo.dto.user;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;
    private String googleId;

    @Override
    public String toString() {
        return "UpdateUserRequest{" +
                "email='" + email + '\'' +
                ", googleId='" + googleId + '\'' +
                '}';
    }
}