package OrangeCloud.UserRepo.filter;

import OrangeCloud.UserRepo.service.AuthService;
import OrangeCloud.UserRepo.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, AuthService authService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
            throws IOException, ServletException {

        String jwt = getJwtFromRequest(httpRequest);

        // 토큰이 존재하고, Security Context에 이미 인증 정보가 없는 경우
        if (StringUtils.hasText(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 토큰 유효성 검사 (실패 시 CustomJwtException 발생)
            jwtTokenProvider.validateToken(jwt);

            // 토큰이 블랙리스트에 있는지 확인
            if (!authService.isTokenBlacklisted(jwt)) {
                // 토큰에서 사용자 ID 추출
                UUID userId = jwtTokenProvider.getUserIdFromToken(jwt);

                // Authentication 객체 생성 (권한은 빈 리스트로 설정)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));

                // Security Context에 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 다음 필터로 요청 전달
        chain.doFilter(httpRequest, httpResponse);
    }

    /**
     * HTTP 요청 헤더에서 JWT 토큰을 추출합니다.
     * Authorization 헤더의 "Bearer " 접두사를 제거하고 토큰을 반환합니다.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
