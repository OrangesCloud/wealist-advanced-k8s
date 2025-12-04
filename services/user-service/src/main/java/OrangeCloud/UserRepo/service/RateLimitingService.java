package OrangeCloud.UserRepo.service;

import OrangeCloud.UserRepo.exception.CustomException;
import OrangeCloud.UserRepo.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RateLimitingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 지정된 키에 대해 요청 속도를 제한합니다.
     * 고정 윈도우 카운터 알고리즘을 사용합니다.
     * @param key 속도 제한을 적용할 키 (예: IP 주소, 사용자 ID)
     * @param limit 허용되는 최대 요청 수
     * @param duration 윈도우 기간 (초)
     * @throws CustomException 속도 제한을 초과했을 경우
     */
    public void checkRateLimit(String key, int limit, long duration) {
        String redisKey = "rate_limit:" + key;

        // Redis에서 현재 카운트와 윈도우 시작 시간을 가져옵니다.
        // RedisTemplate의 opsForValue().increment()는 키가 없으면 0으로 초기화 후 1 증가시킵니다.
        Long count = redisTemplate.opsForValue().increment(redisKey, 1);

        if (count == null) {
            logger.error("Failed to increment rate limit counter for key: {}", key);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "속도 제한 카운터 증가 실패");
        }

        // 키가 처음 생성되었을 때만 만료 시간을 설정합니다.
        if (count == 1) {
            redisTemplate.expire(redisKey, duration, TimeUnit.SECONDS);
            logger.debug("Rate limit key {} initialized with count 1 and duration {}s", key, duration);
        }

        // 속도 제한 초과 여부 확인
        if (count > limit) {
            logger.warn("Rate limit exceeded for key: {}. Count: {}, Limit: {}", key, count, limit);
            System.out.println("RATE LIMIT EXCEEDED: Key=" + key + ", Count=" + count + ", Limit=" + limit); // Debug print
            throw new CustomException(ErrorCode.METHOD_NOT_ALLOWED, "요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }
        logger.debug("Rate limit check passed for key: {}. Count: {}, Limit: {}", key, count, limit);
        System.out.println("RATE LIMIT PASSED: Key=" + key + ", Count=" + count + ", Limit=" + limit); // Debug print
    }
}
