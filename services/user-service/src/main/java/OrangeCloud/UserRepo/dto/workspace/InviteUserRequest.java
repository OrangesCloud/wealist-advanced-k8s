package OrangeCloud.UserRepo.dto.workspace;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequest {
    @NotNull
    private String query;

}
