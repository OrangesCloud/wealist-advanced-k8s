package OrangeCloud.UserRepo.oauth;

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        log.info("Loading user from OAuth2: {}", userRequest.getClientRegistration().getRegistrationId());

        // 기본 OAuth2User 로드
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // Google에서 반환한 정보 추출
        String email = (String) attributes.get("email");
        String googleId = (String) attributes.get("sub"); // Google의 unique ID
        String nickName = (String) attributes.get("name"); // Google name을 nickName으로 사용

        log.debug("Google OAuth2 info: email={}, googleId={}, nickName={}", email, googleId, nickName);

        // UserService를 통해 사용자 생성 또는 조회 (UserProfile도 함께 생성됨)
        User user = userService.findOrCreateUserByGoogle(email, googleId, nickName);
        log.info("User processed: userId={}, email={}", user.getUserId(), email);

        // CustomOAuth2User 반환
        return new CustomOAuth2User(
                user.getUserId(),
                user.getEmail(),
                nickName,
                user.getGoogleId(),
                attributes,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}