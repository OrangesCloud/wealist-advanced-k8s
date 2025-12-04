package OrangeCloud.UserRepo.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 테스트용 S3 설정
 * 실제 AWS 연결 없이 테스트를 실행할 수 있도록 모의 S3 클라이언트를 제공합니다.
 */
@TestConfiguration
public class TestS3Config {

    @Bean
    @Primary
    public S3Client testS3Client() {
        // 테스트용 S3 클라이언트 (실제 연결 없음)
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build();
    }

    @Bean
    @Primary
    public S3Presigner testS3Presigner() {
        // 테스트용 S3 Presigner (실제 연결 없음)
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build();
    }
}
