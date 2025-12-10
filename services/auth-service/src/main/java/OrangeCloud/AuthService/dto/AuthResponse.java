package OrangeCloud.AuthService.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 인증 응답 DTO - 토큰 정보만 반환
 * nickName, email 등 사용자 정보는 user-service에서 별도 조회
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
}
