package OrangeCloud.UserRepo.dto.user;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserIdRequest {
    private List<UUID> userIds;
}
