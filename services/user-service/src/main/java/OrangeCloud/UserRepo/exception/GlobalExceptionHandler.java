package OrangeCloud.UserRepo.exception;

import OrangeCloud.UserRepo.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException ex) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .code(ex.getErrorCode().getCode())
                        .message(ex.getErrorCode().getMessage())
                        .status(ex.getErrorCode().getStatus())
                        .build(),
                ex.getErrorCode().getStatus()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .code(ErrorCode.INVALID_INPUT_VALUE.getCode())
                        .message(ErrorCode.INVALID_INPUT_VALUE.getMessage())
                        .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                        .errors(errors)
                        .build(),
                ErrorCode.INVALID_INPUT_VALUE.getStatus()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .code(ErrorCode.INVALID_INPUT_VALUE.getCode())
                        .message(ex.getMessage())
                        .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                        .build(),
                ErrorCode.INVALID_INPUT_VALUE.getStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                        .message(ErrorCode.INTERNAL_SERVER_ERROR.getMessage() + ": " + ex.getMessage())
                        .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                        .build(),
                ErrorCode.INTERNAL_SERVER_ERROR.getStatus()
        );
    }
}
