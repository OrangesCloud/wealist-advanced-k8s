package OrangeCloud.UserRepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 설정 클래스
 * 로컬 및 AWS 환경에서 S3 클라이언트를 구성합니다.
 * 
 * AWS SDK 기본 자격증명 체인 사용:
 * - EC2: IAM 역할 자격증명 (자동)
 * - 로컬: ~/.aws/credentials 또는 환경 변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 */
@Configuration
@ConfigurationProperties(prefix = "aws.s3")
public class S3Config {

    private String bucket;
    private String region;
    private String accessKey; // MinIO용만 필요 (선택적)
    private String secretKey; // MinIO용만 필요 (선택적)
    private String endpoint;  // 로컬 MinIO용 (선택적)

    /**
     * S3 클라이언트 빈 생성
     * endpoint가 설정된 경우(MinIO) 명시적 자격증명 사용
     * 그 외의 경우 AWS SDK 기본 자격증명 체인 사용
     */
    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region));

        // MinIO 사용 시 명시적 자격증명 필요
        if (endpoint != null && !endpoint.isEmpty()) {
            if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
                throw new IllegalStateException("Access key and secret key are required for MinIO endpoint");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true); // MinIO는 path-style access 필요
        }
        // AWS 환경에서는 기본 자격증명 체인 사용 (IAM 역할 또는 ~/.aws/credentials)

        return builder.build();
    }

    /**
     * S3 Presigner 빈 생성
     * Presigned URL 생성에 사용됩니다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region));

        // MinIO 사용 시 명시적 자격증명 필요
        if (endpoint != null && !endpoint.isEmpty()) {
            if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
                throw new IllegalStateException("Access key and secret key are required for MinIO endpoint");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
            builder.endpointOverride(URI.create(endpoint));
            
            // MinIO는 path-style access 필요
            // 이렇게 하면 http://localhost:9000/bucket/key 형식의 URL이 생성됨
            // virtual-hosted-style (http://bucket.localhost:9000/key)이 아닌
            builder.serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            );
        }
        // AWS 환경에서는 기본 자격증명 체인 사용 (IAM 역할 또는 ~/.aws/credentials)

        return builder.build();
    }

    // Getters and Setters
    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
