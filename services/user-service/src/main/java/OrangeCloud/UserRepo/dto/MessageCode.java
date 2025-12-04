package OrangeCloud.UserRepo.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageCode {

    // Auth Messages
    SIGNUP_SUCCESS("회원가입이 완료되었습니다."),
    LOGIN_SUCCESS("로그인이 완료되었습니다."),
    LOGOUT_SUCCESS("로그아웃이 완료되었습니다."),
    TOKEN_REFRESH_SUCCESS("토큰이 갱신되었습니다."),
    USER_INFO_RETRIEVED_SUCCESS("사용자 정보 조회 성공"),

    // User Messages
    EMAIL_AVAILABLE("사용 가능한 이메일입니다."),
    USER_COUNT_RETRIEVED("활성화된 사용자 수"),

    // Group Messages
    GROUP_CREATED_SUCCESS("그룹이 성공적으로 생성되었습니다."),
    GROUP_LIST_RETRIEVED_SUCCESS("활성화된 그룹 목록을 성공적으로 조회했습니다."),
    COMPANY_NAME_EXISTS("회사명의 그룹이 이미 존재합니다."),
    GROUP_COUNT_RETRIEVED("활성화된 그룹 수"),

    // Common Messages
    OPERATION_SUCCESS("작업이 성공적으로 완료되었습니다.");

    private final String message;
}
