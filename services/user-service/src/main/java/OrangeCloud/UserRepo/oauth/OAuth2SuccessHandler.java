package OrangeCloud.UserRepo.oauth;

import OrangeCloud.UserRepo.util.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtTokenProvider jwtTokenProvider;
        @Value("${oauth2.redirect-url}")
        private String redirectUrl;

        @PostConstruct
        public void init() {
                log.info("OAuth2SuccessHandler initialized with redirect URL: {}", redirectUrl);
        }

        @Override
        public void onAuthenticationSuccess(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException {
                CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

                log.info("OAuth2 login successful: email={}, userId={}", oAuth2User.getEmail(), oAuth2User.getUserId());
                // JWT 토큰 생성
                String accessToken = jwtTokenProvider.generateToken(oAuth2User.getUserId());
                String refreshToken = jwtTokenProvider.generateRefreshToken(oAuth2User.getUserId());

                log.debug("Tokens generated: accessToken length={}, refreshToken length={}",
                                accessToken.length(), refreshToken.length());

                // 프론트엔드로 리다이렉트 (쿼리 파라미터로 토큰 전달)
                String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                                .queryParam("accessToken", accessToken)
                                .queryParam("refreshToken", refreshToken)
                                .queryParam("userId", oAuth2User.getUserId().toString())
                                .queryParam("email", oAuth2User.getEmail())
                                .queryParam("nickName", oAuth2User.getName())
                                .build()
                                .encode() // UTF-8로 인코딩 (예: 한글을 %ED%95%9C으로 변환)
                                .toUriString();

                log.info("Redirecting to: {}", targetUrl);

                // 🔑 추가된 핵심 로직: 세션에 남아있는 임시 인증 정보 및 속성 정리
                // 1. 부모 클래스가 제공하는 메서드를 호출하여 세션의 임시 속성(에러/요청 정보 등)을 지웁니다.
                // 이는 SimpleUrlAuthenticationSuccessHandler의 표준 세션 클리어 방식입니다.
                clearAuthenticationAttributes(request);

                // 2. (선택적이지만 안전함) 세션 자체를 무효화하여 확실히 StateLess를 보장합니다.
                if (request.getSession(false) != null) {
                        log.debug("Invalidating HTTP session after successful OAuth2 login.");
                        request.getSession(false).invalidate();
                }

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}