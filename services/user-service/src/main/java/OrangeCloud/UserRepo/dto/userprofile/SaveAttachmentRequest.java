package OrangeCloud.UserRepo.dto.userprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 첨부파일 메타데이터 저장 요청 DTO
 */
public record SaveAttachmentRequest(
        @NotBlank(message = "파일 키는 필수입니다.")
        String fileKey,

        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 양수여야 합니다.")
        Long fileSize,

        @NotBlank(message = "Content-Type은 필수입니다.")
        String contentType
) {
}
