package OrangeCloud.UserRepo.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJoinRequestRequest {
    @NotBlank(message = "상태는 필수입니다. (APPROVED, REJECTED)")
    private String status;
}