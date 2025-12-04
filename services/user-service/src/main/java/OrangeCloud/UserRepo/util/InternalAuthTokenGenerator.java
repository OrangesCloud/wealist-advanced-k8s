package OrangeCloud.UserRepo.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Internal service-to-service authentication token generator.
 * Generates JWT tokens for internal API calls to Board-Service.
 */
@Component
public class InternalAuthTokenGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InternalAuthTokenGenerator.class);
    private static final long ONE_HOUR_MS = 3600000L; // 1 hour in milliseconds

    private final SecretKey key;

    public InternalAuthTokenGenerator(@Value("${jwt.secret}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT Secret이 설정되지 않았습니다. .env 또는 application.yml을 확인하세요.");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates an internal authentication token for service-to-service communication.
     * Token expires in 1 hour.
     *
     * @param userId the user ID to include in the token subject
     * @return JWT token string
     */
    public String generateInternalToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ONE_HOUR_MS);

        String token = Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        logger.debug("Generated internal auth token for user: {}", userId);
        return token;
    }
}
