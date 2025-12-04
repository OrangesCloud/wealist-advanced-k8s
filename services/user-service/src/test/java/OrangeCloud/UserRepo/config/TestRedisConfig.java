package OrangeCloud.UserRepo.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트 환경에서 Redis를 Mock으로 대체하는 설정
 * Redis 연결 없이 테스트를 실행할 수 있도록 ConcurrentMapCacheManager를 사용합니다.
 */
@TestConfiguration
@Profile("test")
@EnableCaching
public class TestRedisConfig {

    /**
     * 테스트용 CacheManager - 인메모리 캐시 사용
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("userProfile", "userProfiles");
    }

    /**
     * Mock RedisConnectionFactory - Redis 연결 없이 테스트 가능
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        return factory;
    }

    /**
     * Mock RedisTemplate - Redis 연결 없이 테스트 가능
     * 실제 RedisTemplate 객체를 생성하되, Mock ConnectionFactory를 사용
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
