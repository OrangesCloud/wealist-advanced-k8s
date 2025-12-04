package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.config.S3Config;
import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * S3 서비스
 * Presigned URL 생성 및 파일 키 관리를 담당합니다.
 */
@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Config s3Config;

    public S3Service(S3Presigner s3Presigner, S3Client s3Client, S3Config s3Config) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.s3Config = s3Config;
    }

    /**
     * Presigned URL 생성
     *
     * @param workspaceId 워크스페이스 ID
     * @param userId      사용자 ID
     * @param fileName    파일명
     * @param contentType Content-Type
     * @return Presigned URL과 파일 키를 포함한 응답
     */
    public PresignedUrlResponse generatePresignedUrl(
            UUID workspaceId,
            UUID userId,
            String fileName,
            String contentType) {

        // 파라미터 검증
        validateParameters(workspaceId, userId, fileName, contentType);

        try {
            // 파일 키 생성
            String fileKey = generateFileKey(workspaceId, userId, fileName);

            // PutObjectRequest 생성
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(fileKey)
                    .contentType(contentType)
                    .build();

            // Presigned URL 생성
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(PRESIGNED_URL_EXPIRATION)
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();

            // MinIO 환경에서 내부 호스트를 외부 호스트로 치환
            // endpoint가 설정된 경우(로컬 개발 환경)에만 치환을 시도합니다.
            // path-style URL 형식: http://minio:9000/bucket/key -> http://localhost:9000/bucket/key
            if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
                // MinIO의 내부 서비스 이름을 localhost로 치환
                presignedUrl = presignedUrl.replace("http://minio:9000", "http://localhost:9000");
                
                logger.debug("Presigned URL 호스트 치환 완료: {}", presignedUrl);
            }

            logger.info("Presigned URL 생성 성공 - workspaceId: {}, userId: {}, fileKey: {}",
                    workspaceId, userId, fileKey);

            return new PresignedUrlResponse(presignedUrl, fileKey, (int) PRESIGNED_URL_EXPIRATION.getSeconds());

        } catch (Exception e) {
            logger.error("Presigned URL 생성 실패 - workspaceId: {}, userId: {}, error: {}",
                    workspaceId, userId, e.getMessage(), e);
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED, "Presigned URL 생성에 실패했습니다.");
        }
    }

    /**
     * 파일 키 생성
     * 형식: user/{workspaceId}/{year}/{month}/{userId}_{timestamp}.ext
     *
     * @param workspaceId 워크스페이스 ID
     * @param userId      사용자 ID
     * @param fileName    파일명
     * @return 생성된 파일 키
     */
    private String generateFileKey(UUID workspaceId, UUID userId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        long timestamp = System.currentTimeMillis();

        // 파일 확장자 추출
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            extension = fileName.substring(lastDotIndex);
        }

        return String.format("user/%s/%s/%s/%s_%d%s",
                workspaceId, year, month, userId, timestamp, extension);
    }

    /**
     * 파일 키로부터 S3 URL 생성
     * MinIO 환경과 AWS 환경을 자동으로 감지하여 적절한 URL 형식을 생성합니다.
     *
     * @param fileKey S3 파일 키
     * @return S3 파일 URL
     */
    public String generateS3Url(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "파일 키는 필수입니다.");
        }

        // fileKey 형식 검증 (user/ 로 시작해야 함)
        if (!fileKey.startsWith("user/")) {
            logger.warn("Invalid fileKey format: {}", fileKey);
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 파일 키 형식입니다.");
        }

        String s3Url;
        
        // MinIO 환경인 경우 (endpoint가 설정된 경우)
        if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
            // endpoint에서 프로토콜 제거하여 호스트 추출
            String endpoint = s3Config.getEndpoint()
                    .replace("http://", "")
                    .replace("https://", "");
            
            // MinIO URL 형식: http://{endpoint}/{bucket}/{fileKey}
            s3Url = String.format("http://%s/%s/%s",
                    endpoint,
                    s3Config.getBucket(),
                    fileKey);
            
            logger.debug("Generated MinIO URL from fileKey: {} -> {}", fileKey, s3Url);
        } else {
            // AWS S3 환경인 경우
            // 형식: https://{bucket}.s3.{region}.amazonaws.com/{fileKey}
            s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s",
                    s3Config.getBucket(),
                    s3Config.getRegion(),
                    fileKey);
            
            logger.debug("Generated AWS S3 URL from fileKey: {} -> {}", fileKey, s3Url);
        }

        return s3Url;
    }

    /**
     * 파라미터 검증
     */
    private void validateParameters(UUID workspaceId, UUID userId, String fileName, String contentType) {
        if (workspaceId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "워크스페이스 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 필수입니다.");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "파일명은 필수입니다.");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "Content-Type은 필수입니다.");
        }
    }

    /**
     * S3에서 파일 삭제
     *
     * @param fileKey 삭제할 파일의 키
     */
    public void deleteFile(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "파일 키는 필수입니다.");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            logger.info("S3 파일 삭제 성공 - fileKey: {}", fileKey);

        } catch (Exception e) {
            logger.error("S3 파일 삭제 실패 - fileKey: {}, error: {}", fileKey, e.getMessage(), e);
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED, "S3 파일 삭제에 실패했습니다.");
        }
    }

    /**
     * Presigned URL 응답 DTO
     */
    public static class PresignedUrlResponse {
        private final String uploadUrl;
        private final String fileKey;
        private final int expiresIn;

        @com.fasterxml.jackson.annotation.JsonCreator
        public PresignedUrlResponse(
                @com.fasterxml.jackson.annotation.JsonProperty("uploadUrl") String uploadUrl,
                @com.fasterxml.jackson.annotation.JsonProperty("fileKey") String fileKey,
                @com.fasterxml.jackson.annotation.JsonProperty("expiresIn") int expiresIn) {
            this.uploadUrl = uploadUrl;
            this.fileKey = fileKey;
            this.expiresIn = expiresIn;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }

        public String getFileKey() {
            return fileKey;
        }

        public int getExpiresIn() {
            return expiresIn;
        }
    }
}
