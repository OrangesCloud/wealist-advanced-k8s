// src/main/java/OrangeCloud/UserRepo/dto/MessageApiResponse.java
package OrangeCloud.UserRepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageApiResponse {
    private boolean success;
    private String message;
    private Object data;

    // 데이터 없이 성공/실패와 메시지만 있는 경우를 위한 생성자
    public MessageApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // 정적 팩토리 메서드들
    public static MessageApiResponse success(String message) {
        return MessageApiResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static MessageApiResponse success(String message, Object data) {
        return MessageApiResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static MessageApiResponse failure(String message) {
        return MessageApiResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static MessageApiResponse failure(String message, Object data) {
        return MessageApiResponse.builder()
                .success(false)
                .message(message)
                .data(data)
                .build();
    }
}