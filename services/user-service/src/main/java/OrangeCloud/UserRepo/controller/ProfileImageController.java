package OrangeCloud.UserRepo.controller;

import OrangeCloud.UserRepo.dto.userprofile.*;
import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import OrangeCloud.UserRepo.service.AttachmentService;
import OrangeCloud.UserRepo.service.S3Service;
import OrangeCloud.UserRepo.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * 프로필 이미지 컨트롤러
 * Presigned URL 기반 프로필 이미지 업로드를 처리합니다.
 */
@RestController
@RequestMapping("/api/profiles/me/image")
@RequiredArgsConstructor
@Tag(name = "ProfileImage", description = "프로필 이미지 업로드 API")
@Slf4j
public class ProfileImageController {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    private final S3Service s3Service;
    private final UserProfileService userProfileService;
    private final AttachmentService attachmentService;

    /**
     * 인증된 사용자 ID 추출
     */
    private UUID extractUserId(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return UUID.fromString(authentication.getName());
        }
        throw new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다.");
    }

    /**
     * Presigned URL 생성
     *
     * @param request   Presigned URL 요청
     * @param principal 인증된 사용자 정보
     * @return Presigned URL 응답
     */
    @PostMapping("/presigned-url")
    @Operation(
            summary = "프로필 이미지 업로드를 위한 Presigned URL 생성",
            description = "클라이언트가 S3에 직접 업로드할 수 있는 Presigned URL을 생성합니다. " +
                    "이미지 파일만 허용되며, 최대 20MB까지 업로드 가능합니다."
    )
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            Principal principal) {

        UUID userId = extractUserId(principal);
        log.info("Presigned URL 요청 - userId: {}, workspaceId: {}, fileName: {}, fileSize: {}, contentType: {}",
                userId, request.workspaceId(), request.fileName(), request.fileSize(), request.contentType());

        // 파일 메타데이터 검증
        validateFileMetadata(request);

        // Presigned URL 생성
        S3Service.PresignedUrlResponse s3Response = s3Service.generatePresignedUrl(
                request.workspaceId(),
                userId,
                request.fileName(),
                request.contentType()
        );

        PresignedUrlResponse response = new PresignedUrlResponse(
                s3Response.getUploadUrl(),
                s3Response.getFileKey(),
                s3Response.getExpiresIn()
        );

        log.info("Presigned URL 생성 완료 - userId: {}, fileKey: {}", userId, response.fileKey());
        return ResponseEntity.ok(response);
    }

    /**
     * 파일 메타데이터 검증
     * - 파일 크기: 20MB 이하
     * - 파일 타입: 이미지만 허용 (jpg, jpeg, png, gif, webp)
     * - 파일 확장자: Content-Type과 일치
     */
    private void validateFileMetadata(PresignedUrlRequest request) {
        // 파일 크기 검증
        if (request.fileSize() > MAX_FILE_SIZE) {
            log.warn("파일 크기 초과 - fileName: {}, fileSize: {}, maxSize: {}",
                    request.fileName(), request.fileSize(), MAX_FILE_SIZE);
            throw new CustomException(ErrorCode.FILE_TOO_LARGE,
                    String.format("파일 크기는 %dMB를 초과할 수 없습니다.", MAX_FILE_SIZE / (1024 * 1024)));
        }

        // Content-Type 검증 (이미지만 허용)
        if (!ALLOWED_IMAGE_TYPES.contains(request.contentType().toLowerCase())) {
            log.warn("지원하지 않는 파일 타입 - fileName: {}, contentType: {}",
                    request.fileName(), request.contentType());
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE,
                    "이미지 파일만 업로드 가능합니다. 지원 형식: jpg, jpeg, png, gif, webp");
        }

        // 파일 확장자 검증
        String fileName = request.fileName().toLowerCase();
        boolean hasValidExtension = ALLOWED_IMAGE_EXTENSIONS.stream()
                .anyMatch(fileName::endsWith);

        if (!hasValidExtension) {
            log.warn("지원하지 않는 파일 확장자 - fileName: {}", request.fileName());
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE,
                    "지원하지 않는 파일 확장자입니다. 지원 형식: jpg, jpeg, png, gif, webp");
        }

        log.debug("파일 메타데이터 검증 완료 - fileName: {}, fileSize: {}, contentType: {}",
                request.fileName(), request.fileSize(), request.contentType());
    }

    /**
     * 임시 첨부파일 메타데이터 저장
     * S3에 업로드된 파일의 메타데이터를 임시로 저장합니다.
     *
     * @param request   첨부파일 저장 요청
     * @param principal 인증된 사용자 정보
     * @return 저장된 첨부파일 정보
     */
    @PostMapping("/attachment")
    @Operation(
            summary = "프로필 이미지 첨부파일 메타데이터 저장",
            description = "S3에 업로드된 파일의 메타데이터를 임시로 저장합니다. " +
                    "임시 파일은 1시간 후 자동으로 삭제되며, 프로필 업데이트 시 확정됩니다."
    )
    public ResponseEntity<AttachmentResponse> saveAttachmentMetadata(
            @Valid @RequestBody SaveAttachmentRequest request,
            Principal principal) {

        UUID userId = extractUserId(principal);
        log.info("첨부파일 메타데이터 저장 요청 - userId: {}, fileKey: {}, fileName: {}",
                userId, request.fileKey(), request.fileName());

        // 임시 첨부파일 생성
        Attachment attachment = attachmentService.createTempAttachment(
                Attachment.EntityType.USER_PROFILE,
                request.fileName(),
                request.fileKey(),
                request.fileSize(),
                request.contentType(),
                userId
        );

        AttachmentResponse response = AttachmentResponse.from(attachment);
        log.info("첨부파일 메타데이터 저장 완료 - attachmentId: {}, expiresAt: {}",
                response.getAttachmentId(), response.getExpiresAt());

        return ResponseEntity.ok(response);
    }

    /**
     * 프로필 이미지 업데이트 (attachmentId 기반)
     * 임시 첨부파일을 확정하고 프로필 이미지 URL을 업데이트합니다.
     *
     * @param request   프로필 이미지 업데이트 요청
     * @param principal 인증된 사용자 정보
     * @return 업데이트된 프로필 정보
     */
    @PutMapping
    @Operation(
            summary = "프로필 이미지 업데이트",
            description = "임시 첨부파일의 attachmentId를 받아 프로필 이미지를 업데이트합니다. " +
                    "클라이언트는 먼저 /attachment 엔드포인트로 파일을 업로드하여 attachmentId를 받은 후, " +
                    "이 엔드포인트를 호출하여 프로필을 업데이트해야 합니다."
    )
    public ResponseEntity<UserProfileResponse> updateProfileImage(
            @Valid @RequestBody UpdateProfileImageByKeyRequest request,
            Principal principal) {

        UUID userId = extractUserId(principal);
        log.info("프로필 이미지 업데이트 요청 - userId: {}, workspaceId: {}, attachmentId: {}",
                userId, request.workspaceId(), request.attachmentId());

        // 프로필 업데이트 (attachmentId 전달)
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                request.workspaceId(),
                userId,
                null,  // nickName은 변경하지 않음
                null,  // email은 변경하지 않음
                null,  // profileImageUrl은 attachment에서 가져옴
                request.attachmentId()  // attachmentId로 임시 첨부파일 확정
        );

        UserProfileResponse response = userProfileService.updateProfile(updateRequest);
        log.info("프로필 이미지 업데이트 완료 - userId: {}, profileImageUrl: {}",
                userId, response.getProfileImageUrl());

        return ResponseEntity.ok(response);
    }
}
