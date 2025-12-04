package OrangeCloud.UserRepo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalAuthTokenGeneratorTest {

    private static final String TEST_JWT_SECRET = "test-secret-key-that-is-long-enough-for-hs512-algorithm-minimum-512-bits";
    private InternalAuthTokenGenerator tokenGenerator;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        tokenGenerator = new InternalAuthTokenGenerator(TEST_JWT_SECRET);
        secretKey = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldGenerateValidToken() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        String token = tokenGenerator.generateInternalToken(userId);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    void shouldIncludeUserIdInTokenSubject() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        String token = tokenGenerator.generateInternalToken(userId);

        // Then
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void shouldSetTokenExpirationToOneHour() {
        // Given
        UUID userId = UUID.randomUUID();
        long beforeGeneration = System.currentTimeMillis();

        // When
        String token = tokenGenerator.generateInternalToken(userId);
        long afterGeneration = System.currentTimeMillis();

        // Then
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        Date expiration = claims.getExpiration();
        Date issuedAt = claims.getIssuedAt();

        // Token should expire approximately 1 hour (3600000 ms) after issuance
        long expirationDuration = expiration.getTime() - issuedAt.getTime();
        assertThat(expirationDuration).isEqualTo(3600000L);

        // Issued at should be around the time of generation (JWT truncates to seconds)
        // Allow 2 second tolerance for test execution time
        assertThat(issuedAt.getTime()).isBetween(beforeGeneration - 2000, afterGeneration + 2000);
    }

    @Test
    void shouldSetIssuedAtTimestamp() {
        // Given
        UUID userId = UUID.randomUUID();
        long beforeGeneration = System.currentTimeMillis();

        // When
        String token = tokenGenerator.generateInternalToken(userId);
        long afterGeneration = System.currentTimeMillis();

        // Then
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        Date issuedAt = claims.getIssuedAt();
        assertThat(issuedAt).isNotNull();
        // JWT truncates to seconds, so allow 2 second tolerance
        assertThat(issuedAt.getTime()).isBetween(beforeGeneration - 2000, afterGeneration + 2000);
    }

    @Test
    void shouldThrowExceptionWhenJwtSecretIsNull() {
        // When & Then
        assertThatThrownBy(() -> new InternalAuthTokenGenerator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT Secret이 설정되지 않았습니다");
    }

    @Test
    void shouldThrowExceptionWhenJwtSecretIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> new InternalAuthTokenGenerator(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT Secret이 설정되지 않았습니다");
    }

    @Test
    void shouldThrowExceptionWhenJwtSecretIsBlank() {
        // When & Then
        assertThatThrownBy(() -> new InternalAuthTokenGenerator("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT Secret이 설정되지 않았습니다");
    }

    @Test
    void shouldGenerateDifferentTokensForSameUser() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        String token1 = tokenGenerator.generateInternalToken(userId);
        // JWT truncates to seconds, so we need at least 1 second delay
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String token2 = tokenGenerator.generateInternalToken(userId);

        // Then
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentUsers() {
        // Given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        // When
        String token1 = tokenGenerator.generateInternalToken(userId1);
        String token2 = tokenGenerator.generateInternalToken(userId2);

        // Then
        assertThat(token1).isNotEqualTo(token2);

        Claims claims1 = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token1)
                .getBody();

        Claims claims2 = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token2)
                .getBody();

        assertThat(claims1.getSubject()).isEqualTo(userId1.toString());
        assertThat(claims2.getSubject()).isEqualTo(userId2.toString());
    }
}
