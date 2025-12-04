package OrangeCloud.UserRepo.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor
public class ErrorResponse {
    private final LocalDateTime timestamp = LocalDateTime.now();
    private String code;
    private String message;
    private HttpStatus status;
    private Map<String, String> errors;

    @Builder
    public ErrorResponse(String code, String message, HttpStatus status, Map<String, String> errors) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.errors = errors;
    }

    public ErrorResponse(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
