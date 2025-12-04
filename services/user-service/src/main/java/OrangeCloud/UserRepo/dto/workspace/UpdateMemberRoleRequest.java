package OrangeCloud.UserRepo.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRoleRequest {
    @NotBlank(message = "역할명은 필수입니다.")
    private String roleName;
}