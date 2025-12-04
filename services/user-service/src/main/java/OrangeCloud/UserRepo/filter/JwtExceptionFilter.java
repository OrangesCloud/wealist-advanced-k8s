package OrangeCloud.UserRepo.filter;

import OrangeCloud.UserRepo.exception.CustomJwtException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; // 💡 Lombok 임포트 추가
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 💡 Lombok을 사용하여 final 필드에 대한 생성자 주입을 자동으로 처리
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {
    
    // 💡 SecurityConfig에서 주입받을 ObjectMapper
    private final ObjectMapper objectMapper; 

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (CustomJwtException ex) {
            setErrorResponse(response, ex.getErrorCode());
        }
    }

    private void setErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        
        // 💡 수정: 필드에 주입된 objectMapper를 사용 (매번 새로운 인스턴스를 생성하지 않음)
        // ObjectMapper objectMapper = new ObjectMapper(); // 👈 이 줄을 제거했습니다.
        
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // ErrorCode DTO에 LocalDateTime 타입이 포함되어 있지 않더라도, 
        // 응답 객체에 시간 정보가 포함될 경우, 이 objectMapper가 안전하게 처리합니다.
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new java.util.Date()); // 예시: 에러 발생 시간을 담는 경우
        body.put("status", errorCode.getStatus().value());
        body.put("code", errorCode.getCode());
        body.put("message", errorCode.getMessage());

        // 💡 수정: 주입된 objectMapper 사용
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}