package OrangeCloud.UserRepo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class JacksonConfig {
    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);
    
    public JacksonConfig() {
        log.warn("🚨 JacksonConfig가 성공적으로 로드되었습니다! 설정이 적용될 것입니다.");
    }
    
    /**
     * Spring Boot의 기본 Jackson ObjectMapper 빌더를 커스터마이징합니다.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        log.info("✅ JavaTimeModule이 Jackson 빌더에 등록되었습니다.");
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
    
    /**
     * Primary ObjectMapper를 명시적으로 생성하여 모든 곳에서 사용되도록 합니다.
     * 이는 Spring MVC, RestTemplate, 그리고 다른 모든 Jackson 사용처에 적용됩니다.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("✅ Primary ObjectMapper가 JavaTimeModule과 함께 생성되었습니다.");
        return objectMapper;
    }
}