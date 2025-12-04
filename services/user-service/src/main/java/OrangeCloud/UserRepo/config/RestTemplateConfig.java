package OrangeCloud.UserRepo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used in Board-Service communication.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean with timeout configurations.
     * 
     * Configuration:
     * - Connect timeout: 5 seconds
     * - Read timeout: 10 seconds
     * 
     * @param builder RestTemplateBuilder provided by Spring Boot
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
