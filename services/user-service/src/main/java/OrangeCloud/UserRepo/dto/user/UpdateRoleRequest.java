// src/main/java/OrangeCloud/UserRepo/dto/userinfo/UpdateRoleRequest.java
package OrangeCloud.UserRepo.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoleRequest {

    @NotBlank(message = "역할은 필수입니다.")
    @Size(min = 1, max = 50, message = "역할은 1자 이상 50자 이하여야 합니다.")
    private String role;
}