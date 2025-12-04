package OrangeCloud.UserRepo.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "Access is Denied"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C004", "Unauthorized"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "Internal Server Error"),

    // User Specific Errors
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User not found"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "Email already exists"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "U003", "Invalid password"),
    USER_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "U004", "User is already deleted"),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "U005", "User is not active"),

    // Group Specific Errors
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "Group not found"),
    COMPANY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "G002", "Company name already exists"),
    GROUP_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "G003", "Group is already deleted"),

    // Team Specific Errors
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "Team not found"),
    LEADER_NOT_FOUND_IN_TEAM(HttpStatus.BAD_REQUEST, "T002", "Leader not found in team"),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "T003", "Not a team leader"),
    TEAM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "T004", "Team member not found"),

    // JWT Specific Errors
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "J001", "Invalid JWT token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "J002", "Expired JWT token"),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "J003", "Unsupported JWT token"),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, "J004", "Malformed JWT token"),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "J005", "JWT token not found"),
    TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "J006", "JWT signature is invalid"),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "J007", "JWT token is blacklisted"),

    // S3 Specific Errors
    S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "S3 upload failed"),
    S3_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S002", "S3 service unavailable"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "S003", "File size exceeds limit"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "S004", "Invalid file type"),

    // Attachment Specific Errors
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "Attachment not found"),
    INVALID_ATTACHMENT_STATUS(HttpStatus.BAD_REQUEST, "A002", "Invalid attachment status");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
