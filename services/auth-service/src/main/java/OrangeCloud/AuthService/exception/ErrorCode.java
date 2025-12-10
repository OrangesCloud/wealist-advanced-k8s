package OrangeCloud.AuthService.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Token errors
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH002", "만료된 토큰입니다."),
    TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH003", "토큰 서명이 유효하지 않습니다."),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH004", "잘못된 형식의 토큰입니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH005", "지원하지 않는 토큰입니다."),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "AUTH006", "이미 로그아웃된 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH007", "토큰이 없습니다."),

    // User service errors
    USER_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AUTH008", "사용자 서비스 연결 오류입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH009", "사용자를 찾을 수 없습니다."),

    // General errors
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH999", "내부 서버 오류입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
