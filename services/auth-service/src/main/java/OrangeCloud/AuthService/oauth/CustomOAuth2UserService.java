package OrangeCloud.AuthService.oauth;

import OrangeCloud.AuthService.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserServiceClient userServiceClient;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String provider = userRequest.getClientRegistration().getRegistrationId();

        log.info("OAuth2 로그인 시도: email={}, provider={}", email, provider);

        // user-service에 유저 조회/생성 요청
        UUID userId = userServiceClient.findOrCreateOAuthUser(email, name, provider);

        log.info("OAuth2 로그인 성공: userId={}, email={}", userId, email);

        return new CustomOAuth2User(oAuth2User, userId, email, name);
    }
}
