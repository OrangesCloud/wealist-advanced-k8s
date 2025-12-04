package OrangeCloud.UserRepo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for sample data generation.
 * Enables asynchronous execution of sample data seeding to avoid blocking workspace creation.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Creates a thread pool executor for sample data generation tasks.
     * 
     * Configuration:
     * - Core pool size: 2 threads
     * - Max pool size: 5 threads
     * - Queue capacity: 100 tasks
     * - Thread name prefix: "sample-data-"
     * 
     * This ensures sample data generation runs asynchronously without impacting
     * workspace creation response times.
     * 
     * @return configured Executor for sample data tasks
     */
    @Bean(name = "sampleDataExecutor")
    public Executor sampleDataExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sample-data-");
        executor.initialize();
        return executor;
    }
}
