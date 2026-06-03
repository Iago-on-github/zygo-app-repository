package com.travel_system.backend_app.config;

import com.travel_system.backend_app.service.AsyncNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableAsync
@Configuration
public class ThreadPoolExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 100;
        int KEEP_ALIVE_TIME_SECONDS = 1;
        int CORE_POOL_SIZE = 5;
        int MAXIMUM_POOL_SIZE = 10;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("FCM-Notification-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "vehicleGpsTaskExecutor")
    public ThreadPoolTaskExecutor vehicleGpsTaskExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 200;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("RBMQ-VehicleGps-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }


}
