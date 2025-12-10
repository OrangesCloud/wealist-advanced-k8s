package OrangeCloud.AuthService.oauth;

import OrangeCloud.AuthService.dto.AuthResponse;
import OrangeCloud.AuthService.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${oauth2.redirect-url}")
    private String redirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        log.info("OAuth2 인증 성공: userId={}, email={}", oAuth2User.getUserId(), oAuth2User.getEmail());

        // 토큰 발행
        AuthResponse authResponse = authService.generateTokens(oAuth2User.getUserId());

        // 프론트엔드로 리다이렉트 (토큰 정보를 쿼리 파라미터로 전달)
        // nickName, email은 더 이상 전달하지 않음 - 프론트에서 user-service 호출
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                .queryParam("accessToken", authResponse.getAccessToken())
                .queryParam("refreshToken", authResponse.getRefreshToken())
                .queryParam("userId", authResponse.getUserId().toString())
                .build()
                .toUriString();

        log.debug("Redirecting to: {}", targetUrl);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
